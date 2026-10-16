import Foundation

/// Serializes mutations off the main actor. Media commits before the atomic
/// manifest; failures never replace an existing pack or delete its files.
actor LibraryStore {
    let root: URL
    private var document = LibraryDocument()
    private var loaded = false

    init(root: URL) { self.root = root }

    func load() throws -> [SavedPack] {
        if loaded { return document.packs }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let manifest = root.appendingPathComponent("library.json")
        if FileManager.default.fileExists(atPath: manifest.path) {
            let parsed = try JSONDecoder().decode(LibraryDocument.self, from: MediaRules.read(manifest, limit: 16_000_000))
            try require(parsed.schema == 1 && parsed.packs.count <= 1000, "Unsupported library. Your files have been retained.")
            var ids = Set<String>()
            for pack in parsed.packs {
                try require(MediaRules.safeID(pack.id) && ids.insert(pack.id).inserted && MediaRules.validName(pack.name) && pack.stickers.count <= 30, "Invalid library. Your files have been retained.")
                var names = Set<String>()
                for sticker in pack.stickers {
                    try require(MediaRules.safeID(sticker.id) && sticker.fileName == sticker.id + ".webp" && names.insert(sticker.fileName).inserted, "Invalid library path.")
                }
            }
            document = parsed
        }
        loaded = true
        return document.packs
    }

    func mediaURL(pack: SavedPack, sticker: SavedSticker) throws -> URL {
        try require(MediaRules.safeID(pack.id) && MediaRules.safeID(sticker.id) && sticker.fileName == sticker.id + ".webp", "Invalid sticker path.")
        return root.appendingPathComponent(pack.id).appendingPathComponent(sticker.fileName)
    }

    func bytes(pack: SavedPack, sticker: SavedSticker) throws -> Data {
        let data = try MediaRules.read(mediaURL(pack: pack, sticker: sticker), limit: 500_000)
        try require(MediaRules.hash(data) == sticker.sha256, "A sticker failed its integrity check.")
        try MediaRules.validate(data, kind: pack.kind)
        return data
    }

    @discardableResult func importPacks(_ inputs: [ImportedPack], sourceKey: String? = nil) throws -> [SavedPack] {
        _ = try load()
        if let sourceKey, let ids = document.catalogImports[sourceKey] {
            try require(!document.packs.contains { ids.contains($0.id) }, "This catalog revision is already in your library.")
        }
        try require(!inputs.isEmpty && document.packs.count + inputs.count <= 1000, "Library limit reached.")
        var next = document
        var created: [URL] = []
        var results: [SavedPack] = []
        do {
            for input in inputs {
                try Task.checkCancellation()
                try require(MediaRules.validName(input.name) && input.stickers.count <= 30, "Invalid pack name or size.")
                let id = UUID().uuidString.lowercased()
                let directory = root.appendingPathComponent(id)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
                created.append(directory)
                var pack = SavedPack(id: id, name: input.name, kind: input.kind, rating: input.rating, category: input.category, stickers: [])
                var hashes = Set<String>()
                for source in input.stickers {
                    try MediaRules.validate(source.data, kind: input.kind)
                    try require(source.label.utf16.count <= 80 && source.tags.utf16.count <= 400, "Sticker metadata is too long.")
                    let hash = MediaRules.hash(source.data)
                    guard hashes.insert(hash).inserted else { continue }
                    let itemID = UUID().uuidString.lowercased()
                    let item = SavedSticker(id: itemID, fileName: itemID + ".webp", sha256: hash, label: source.label, tags: source.tags)
                    try source.data.write(to: directory.appendingPathComponent(item.fileName), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                    pack.stickers.append(item)
                }
                next.packs.append(pack)
                results.append(pack)
            }
            if let sourceKey { next.catalogImports[sourceKey] = results.map(\.id) }
            try commit(next)
            return results
        } catch {
            for url in created { try? FileManager.default.removeItem(at: url) }
            throw error
        }
    }

    func append(_ source: ImportedSticker, to id: String, kind: StickerKind) throws {
        try appendBatch([source], to: id, kind: kind)
    }

    func appendBatch(_ sources: [ImportedSticker], to id: String, kind: StickerKind) throws {
        _ = try load()
        guard let i = document.packs.firstIndex(where: { $0.id == id }) else { throw StickerError.invalid("Pack no longer exists.") }
        try require(!sources.isEmpty && document.packs[i].kind == kind && document.packs[i].stickers.count + sources.count <= 30, "Choose a compatible pack with room.")
        var next = document
        var hashes = Set(next.packs[i].stickers.map(\.sha256))
        var staged: [(URL, Data)] = []
        for source in sources {
            try MediaRules.validate(source.data, kind: kind)
            try require(source.label.utf16.count <= 80 && source.tags.utf16.count <= 400, "Sticker metadata is too long.")
            let hash = MediaRules.hash(source.data)
            try require(hashes.insert(hash).inserted, "This sticker is already in the pack.")
            let itemID = UUID().uuidString.lowercased()
            let item = SavedSticker(id: itemID, fileName: itemID + ".webp", sha256: hash, label: source.label, tags: source.tags)
            staged.append((try mediaURL(pack: document.packs[i], sticker: item), source.data))
            next.packs[i].stickers.append(item)
        }
        var written: [URL] = []
        do {
            for (url, bytes) in staged { try Task.checkCancellation(); try bytes.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]); written.append(url) }
            next.packs[i].updatedAt = Date()
            try commit(next)
        } catch { for url in written { try? FileManager.default.removeItem(at: url) }; throw error }
    }

    func rename(_ id: String, name: String) throws {
        try require(MediaRules.validName(name), "Use a pack name of 1–60 characters.")
        _ = try load()
        var next = document
        guard let i = next.packs.firstIndex(where: { $0.id == id }) else { throw StickerError.invalid("Pack no longer exists.") }
        next.packs[i].name = name; next.packs[i].updatedAt = Date()
        try commit(next)
    }

    func toggleFavorite(packID: String, stickerID: String) throws {
        _ = try load()
        var next = document
        guard let i = next.packs.firstIndex(where: { $0.id == packID }), let j = next.packs[i].stickers.firstIndex(where: { $0.id == stickerID }) else { return }
        next.packs[i].stickers[j].favorite.toggle()
        try commit(next)
    }

    func remove(packID: String, stickerID: String? = nil) throws {
        _ = try load()
        guard let pack = document.packs.first(where: { $0.id == packID }) else { return }
        var next = document
        if let stickerID {
            guard let item = pack.stickers.first(where: { $0.id == stickerID }), let i = next.packs.firstIndex(where: { $0.id == packID }) else { return }
            next.packs[i].stickers.removeAll { $0.id == stickerID }
            next.packs[i].updatedAt = Date()
            try commit(next)
            try? FileManager.default.removeItem(at: mediaURL(pack: pack, sticker: item))
        } else {
            next.packs.removeAll { $0.id == packID }
            try commit(next)
            try? FileManager.default.removeItem(at: root.appendingPathComponent(packID))
        }
    }

    private func commit(_ next: LibraryDocument) throws {
        try Task.checkCancellation()
        let bytes = try JSONEncoder().encode(next)
        try require(bytes.count <= 16_000_000, "Library metadata limit reached. Export a backup before organizing further.")
        try bytes.write(to: root.appendingPathComponent("library.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        document = next
    }
}
