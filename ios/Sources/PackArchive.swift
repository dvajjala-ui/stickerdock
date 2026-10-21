import Foundation
import ZIPFoundation
import StickerDockCore

enum PackArchive {
    static let maxTotal = 50_000_000

    static func read(_ url: URL) throws -> [ImportedPack] {
        let attrs = try url.resourceValues(forKeys: [.fileSizeKey])
        try require((attrs.fileSize ?? Int.max) <= maxTotal, "Archive is too large. Import smaller batches.")
        let archive = try Archive(url: url, accessMode: .read)
        if archive["manifest.json"] == nil { return try readGenericImages(archive) }
        var entries: [String: Data] = [:]
        var total = 0
        for entry in archive {
            try Task.checkCancellation()
            try require(entry.type == .file && entries.count < 3001 && entries[entry.path] == nil, "Duplicate, unsupported or oversized ZIP entry.")
            let path = entry.path
            let parts = path.split(separator: "/", omittingEmptySubsequences: false)
            let valid = path == "manifest.json" || (parts.count == 3 && parts[0] == "packs" && MediaRules.safeID(String(parts[1])) && safeFile(String(parts[2])))
            try require(valid, "Unsupported archive path. Use a StickerDock backup ZIP.")
            let limit = path == "manifest.json" ? 2_000_000 : 500_000
            try require(entry.uncompressedSize <= UInt64(limit), "ZIP entry is too large.")
            var data = Data()
            let checksum = try archive.extract(entry, consumer: { chunk in
                try require(data.count + chunk.count <= limit && total + chunk.count <= maxTotal, "ZIP expands beyond its limit.")
                data.append(chunk); total += chunk.count
            })
            try require(checksum == entry.checksum, "ZIP entry failed its checksum check.")
            entries[path] = data
        }
        guard let manifest = entries["manifest.json"], let doc = try JSONSerialization.jsonObject(with: manifest) as? [String: Any], let schema = doc["schema"] as? Int, (1...5).contains(schema), let packs = doc["packs"] as? [[String: Any]] else { throw StickerError.invalid("Invalid StickerDock backup manifest.") }
        try require(!packs.isEmpty && packs.count <= 1000, "Invalid backup pack count.")
        var expected: Set<String> = ["manifest.json"]
        var ids = Set<String>()
        var result: [ImportedPack] = []
        for pack in packs {
            guard let id = pack["id"] as? String, let name = pack["name"] as? String, let stickers = pack["stickers"] as? [[String: Any]] else { throw StickerError.invalid("Invalid backup pack.") }
            let canonical = AppleMediaBridge().canonicalArchiveID(value: id)
            try require(canonical != nil && ids.insert(canonical ?? "").inserted && MediaRules.validName(name) && stickers.count <= 30, "Invalid backup pack metadata.")
            guard let kind = StickerKind(rawValue: schema >= 2 ? (pack["kind"] as? String ?? "") : "STATIC"), let rating = ContentRating(rawValue: schema >= 3 ? (pack["rating"] as? String ?? "") : "GENERAL") else { throw StickerError.invalid("Invalid pack type or rating.") }
            var items: [ImportedSticker] = []
            for sticker in stickers {
                guard let file = sticker["name"] as? String, safeFile(file), let hash = sticker["sha256"] as? String else { throw StickerError.invalid("Invalid sticker metadata.") }
                let path = "packs/\(id)/\(file)"
                guard expected.insert(path).inserted, let bytes = entries[path], MediaRules.hash(bytes) == hash.lowercased() else { throw StickerError.invalid("Missing, duplicate or damaged sticker.") }
                try MediaRules.validate(bytes, kind: kind)
                let label = schema >= 5 ? (sticker["label"] as? String ?? "") : ""
                let tags = schema >= 5 ? (sticker["tags"] as? String ?? "") : ""
                try require(label.utf16.count <= 80 && tags.utf16.count <= 400, "Sticker label or tags are too long.")
                items.append(ImportedSticker(data: bytes, label: label, tags: tags))
            }
            let category = pack["category"] as? String ?? "GENERAL"
            result.append(ImportedPack(name: name, kind: kind, rating: rating, category: category.count <= 40 ? category : "GENERAL", stickers: items))
        }
        try require(Set(entries.keys) == expected, "Unexpected content in backup.")
        return result
    }

    static func write(_ packs: [SavedPack], store: LibraryStore) async throws -> URL {
        try require(!packs.isEmpty && packs.count <= 1000, "Choose packs to export.")
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("share-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("StickerDock.zip")
        do {
            let bridge = AppleMediaBridge()
            let groups = packs.map { pack in
                (pack: pack,
                 id: bridge.canonicalArchiveID(value: pack.id) ?? UUID().uuidString.lowercased(),
                 items: pack.stickers.map { item in
                    (sticker: item, name: (bridge.canonicalArchiveID(value: item.id) ?? UUID().uuidString.lowercased()) + ".webp")
                 })
            }
            try require(Set(groups.map { $0.id }).count == groups.count, "Duplicate archive identity.")
            let archive = try Archive(url: url, accessMode: .create)
            let manifest: [String: Any] = ["schema": 5, "packs": groups.map { group in
                ["id": group.id, "name": group.pack.name, "kind": group.pack.kind.rawValue, "rating": group.pack.rating.rawValue, "category": bridge.portableArchiveCategory(value: group.pack.category),
                 "stickers": group.items.map { ["name": $0.name, "sha256": $0.sticker.sha256, "label": $0.sticker.label, "tags": $0.sticker.tags] }] as [String: Any]
            }]
            let data = try JSONSerialization.data(withJSONObject: manifest, options: [.sortedKeys])
            try require(data.count <= 2_000_000, "Backup metadata is too large.")
            try add(data, path: "manifest.json", archive: archive)
            var total = data.count
            for group in groups {
                try require(Set(group.items.map { $0.name }).count == group.items.count, "Duplicate sticker identity.")
                for entry in group.items {
                    try Task.checkCancellation()
                    let data = try await store.bytes(pack: group.pack, sticker: entry.sticker)
                    total += data.count
                    try require(total <= maxTotal, "Backup is too large. Share individual packs.")
                    try add(data, path: "packs/\(group.id)/\(entry.name)", archive: archive)
                }
            }
            return url
        } catch { try? FileManager.default.removeItem(at: directory); throw error }
    }

    private static func safeFile(_ name: String) -> Bool {
        name.range(of: "^[A-Za-z0-9_-]{1,100}\\.webp$", options: .regularExpression) != nil
    }

    /// Existing WebP packs and ordinary photo ZIP batches work on both platforms. Reads
    /// data in memory only; never extracts caller-controlled filesystem paths.
    private static func readGenericImages(_ archive: Archive) throws -> [ImportedPack] {
        var grouped: [StickerKind: [ImportedSticker]] = [:]
        var paths = Set<String>()
        var hashes = Set<String>()
        var total = 0
        var retained = 0
        for entry in archive {
            try Task.checkCancellation()
            let parts = entry.path.split(separator: "/", omittingEmptySubsequences: false)
            try require(entry.path.utf16.count <= 512 && !entry.path.hasPrefix("/") && !entry.path.contains("\\") && !parts.contains("..") && !parts.contains(".") && !entry.path.unicodeScalars.contains { $0.value < 32 } && paths.insert(entry.path).inserted && paths.count <= 3001, "Unsafe or oversized archive path.")
            if entry.type == .directory { continue }
            let ext = URL(fileURLWithPath: entry.path).pathExtension.lowercased()
            let photo = ["png", "jpg", "jpeg"].contains(ext)
            let limit = photo ? 20_000_000 : 500_000
            try require(entry.type == .file && (photo || ext == "webp") && entry.uncompressedSize <= UInt64(limit), "Use a ZIP of PNG, JPEG or WebP images, or a StickerDock backup. Choose GIF/video in Create.")
            var bytes = Data()
            let checksum = try archive.extract(entry, consumer: { chunk in
                total += chunk.count
                try require(bytes.count + chunk.count <= limit && total <= maxTotal, "ZIP expands beyond its limit.")
                bytes.append(chunk)
            })
            try require(checksum == entry.checksum, "ZIP entry failed its checksum check.")
            let bridge = StickerDockCore.AppleMediaBridge()
            if photo { bytes = try MediaCreator.archivePhoto(bytes) }
            let info = bridge.inspect(base64: bytes.base64EncodedString())
            let kind: StickerKind = info?.animated == true ? .ANIMATED : .STATIC
            if kind == .STATIC && !photo {
                guard let normalized = bridge.normalizeStatic(base64: bytes.base64EncodedString()), let output = Data(base64Encoded: normalized) else { throw StickerError.invalid("A WebP image is not a valid 512px sticker.") }
                bytes = output
            }
            try MediaRules.validate(bytes, kind: kind)
            if hashes.insert(MediaRules.hash(bytes)).inserted {
                retained += bytes.count
                try require(retained <= maxTotal, "Converted stickers exceed the archive limit. Import smaller batches.")
                let label = URL(fileURLWithPath: entry.path).deletingPathExtension().lastPathComponent
                grouped[kind, default: []].append(ImportedSticker(data: bytes, label: label.utf16.count <= 80 ? label : "Imported sticker"))
            }
        }
        var packs: [ImportedPack] = []
        for kind in StickerKind.allCases {
            let items = grouped[kind] ?? []
            for start in stride(from: 0, to: items.count, by: 30) {
                packs.append(ImportedPack(name: "Imported \(kind == .ANIMATED ? "animated" : "static") stickers \(start / 30 + 1)", kind: kind, stickers: Array(items[start..<min(start + 30, items.count)])))
            }
        }
        try require(!packs.isEmpty, "No PNG, JPEG or WebP stickers found in this ZIP.")
        return packs
    }

    private static func add(_ bytes: Data, path: String, archive: Archive) throws {
        try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(bytes.count), provider: { position, size in
            bytes.subdata(in: Int(position)..<min(Int(position) + size, bytes.count))
        })
    }
}
