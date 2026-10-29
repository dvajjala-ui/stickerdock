import XCTest
import UIKit
import ZIPFoundation
import StickerDockCore
@testable import StickerDock

final class LibraryIntegrationTests: XCTestCase {
    private func directory() throws -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("library-test-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        return root
    }

    private func photo(_ color: UIColor) async throws -> ImportedSticker {
        let root = try directory()
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 640, height: 480), format: format).image { context in color.setFill(); context.fill(CGRect(x: 0, y: 0, width: 640, height: 480)) }
        let url = root.appendingPathComponent("source.png")
        try XCTUnwrap(image.pngData()).write(to: url)
        let creator = MediaCreator()
        let output = try await creator.create(url, recipe: CreationRecipe()) { _ in }
        return try XCTUnwrap(output.stickers.first)
    }

    func testNativePhotoCodecSharedRulesAndAndroidArchiveRoundTrip() async throws {
        let root = try directory()
        let store = LibraryStore(root: root.appendingPathComponent("first"))
        let item = try await photo(.systemBlue)
        let inputs = [ImportedPack(name: "Reaction photos", kind: .STATIC, rating: .MATURE, category: "FUNNY", stickers: [item])]
        let created = try await store.importPacks(inputs)
        let archive = try await PackArchive.write(created, store: store)
        defer { try? FileManager.default.removeItem(at: archive.deletingLastPathComponent()) }
        let attachment = XCTAttachment(data: try Data(contentsOf: archive), uniformTypeIdentifier: "public.zip-archive")
        attachment.name = "iOS-Android-compatibility.zip"; attachment.lifetime = .keepAlways; add(attachment)
        let restored = try PackArchive.read(archive)
        XCTAssertEqual(restored.first?.rating, .MATURE)
        XCTAssertEqual(restored.first?.category, "GENERAL") // portable All, not the private FUNNY alias
        XCTAssertEqual(restored.first?.stickers.first?.data, item.data)
        let second = LibraryStore(root: root.appendingPathComponent("second"))
        _ = try await second.importPacks(restored)
        let reopened = LibraryStore(root: root.appendingPathComponent("second"))
        let packs = try await reopened.load()
        XCTAssertEqual(packs.count, 1)
        let bytes = try await reopened.bytes(pack: packs[0], sticker: packs[0].stickers[0])
        XCTAssertEqual(bytes, item.data)
    }

    func testDuplicateBatchAndInvalidImportPreserveExistingPack() async throws {
        let root = try directory()
        let store = LibraryStore(root: root)
        let first = try await photo(.systemPink)
        let second = try await photo(.systemGreen)
        let packs = try await store.importPacks([ImportedPack(name: "Keep me", kind: .STATIC, stickers: [first])])
        do { try await store.appendBatch([second, second], to: packs[0].id, kind: .STATIC); XCTFail("Duplicate batch was accepted") } catch { }
        do { _ = try await store.importPacks([ImportedPack(name: "Broken", kind: .STATIC, stickers: [ImportedSticker(data: Data([1, 2, 3]))])]); XCTFail("Invalid media was accepted") } catch { }
        let reopened = LibraryStore(root: root)
        let retained = try await reopened.load()
        XCTAssertEqual(retained, packs)
        let bytes = try await reopened.bytes(pack: retained[0], sticker: retained[0].stickers[0])
        XCTAssertEqual(bytes, first.data)
    }

    func testCopyPreservesMetadataAndRejectsDuplicatesAndPrivateToGeneral() async throws {
        let root = try directory()
        let store = LibraryStore(root: root)
        var item = try await photo(.systemOrange)
        item.label = "Work reaction"; item.tags = "work, hello"
        let packs = try await store.importPacks([
            ImportedPack(name: "Private", kind: .STATIC, rating: .MATURE, stickers: [item]),
            ImportedPack(name: "Private destination", kind: .STATIC, rating: .MATURE, stickers: []),
            ImportedPack(name: "General destination", kind: .STATIC, rating: .GENERAL, stickers: [])
        ])
        let sticker = try XCTUnwrap(packs[0].stickers.first)
        do { try await store.copySticker(packID: packs[0].id, stickerID: sticker.id, to: packs[2].id); XCTFail("Private content reached a general pack") } catch { }
        try await store.copySticker(packID: packs[0].id, stickerID: sticker.id, to: packs[1].id)
        do { try await store.copySticker(packID: packs[0].id, stickerID: sticker.id, to: packs[1].id); XCTFail("Duplicate copy was accepted") } catch { }
        let saved = try await LibraryStore(root: root).load()
        XCTAssertEqual(saved[0].stickers, packs[0].stickers)
        XCTAssertTrue(saved[2].stickers.isEmpty)
        XCTAssertEqual(saved[1].stickers.count, 1)
        XCTAssertEqual(saved[1].stickers[0].sha256, sticker.sha256)
        XCTAssertEqual(saved[1].stickers[0].label, "Work reaction")
        XCTAssertEqual(saved[1].stickers[0].tags, "work, hello")
    }

    func testLegacyFoundationUUIDsExportWithCanonicalAndroidPaths() async throws {
        let root = try directory()
        let item = try await photo(.systemPurple)
        let packID = UUID().uuidString.uppercased(), stickerID = UUID().uuidString.uppercased()
        let sticker = SavedSticker(id: stickerID, fileName: stickerID + ".webp", sha256: MediaRules.hash(item.data), label: "Legacy photo", tags: "private")
        let pack = SavedPack(id: packID, name: "Preserve private metadata", kind: .STATIC, rating: .MATURE, category: "ALL", stickers: [sticker])
        let media = root.appendingPathComponent(packID)
        try FileManager.default.createDirectory(at: media, withIntermediateDirectories: true)
        try item.data.write(to: media.appendingPathComponent(sticker.fileName))
        try JSONEncoder().encode(LibraryDocument(packs: [pack])).write(to: root.appendingPathComponent("library.json"))
        let store = LibraryStore(root: root)
        _ = try await store.load()
        let url = try await PackArchive.write([pack], store: store)
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let archive = try Archive(url: url, accessMode: .read)
        XCTAssertTrue(archive.allSatisfy { $0.path == $0.path.lowercased() })
        let restored = try PackArchive.read(url)
        XCTAssertEqual(restored.first?.rating, .MATURE)
        XCTAssertEqual(restored.first?.name, pack.name)
        XCTAssertEqual(restored.first?.stickers.first?.data, item.data)
        XCTAssertTrue(FileManager.default.fileExists(atPath: media.appendingPathComponent(sticker.fileName).path))
    }

    func testArchiveTraversalIsRejectedWithoutExtractingFiles() throws {
        let root = try directory()
        let url = root.appendingPathComponent("malicious.zip")
        let archive = try Archive(url: url, accessMode: .create)
        let data = Data("do not extract".utf8)
        try archive.addEntry(with: "../escape.webp", type: .file, uncompressedSize: Int64(data.count), provider: { position, count in data.subdata(in: Int(position)..<min(Int(position) + count, data.count)) })
        XCTAssertThrowsError(try PackArchive.read(url))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.deletingLastPathComponent().appendingPathComponent("escape.webp").path))
    }

    private func storedArchive(_ entries: [(String, Data)], in root: URL) throws -> URL {
        let url = root.appendingPathComponent(UUID().uuidString + ".zip")
        let archive = try Archive(url: url, accessMode: .create)
        for (path, bytes) in entries {
            try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(bytes.count), compressionMethod: .none, provider: { position, count in
                bytes.subdata(in: Int(position)..<min(Int(position) + count, bytes.count))
            })
        }
        return url
    }

    private func assertChecksumRejectionPreservesLibrary(_ url: URL, store: LibraryStore, root: URL, original: [SavedPack], bytes: Data) async throws {
        do {
            let inputs = try PackArchive.read(url)
            _ = try await store.importPacks(inputs)
            XCTFail("An archive with an invalid CRC was imported")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("checksum"), "Unexpected rejection: \(error)")
        }
        let reopened = LibraryStore(root: root)
        let retained = try await reopened.load()
        XCTAssertEqual(retained, original)
        let retainedBytes = try await reopened.bytes(pack: original[0], sticker: original[0].stickers[0])
        XCTAssertEqual(retainedBytes, bytes)
    }

    func testStoredManifestBitFlipRejectsValidChangedJSONAndPreservesLibrary() async throws {
        let root = try directory()
        let store = LibraryStore(root: root.appendingPathComponent("library"))
        let item = try await photo(.systemIndigo)
        let existing = try await store.importPacks([ImportedPack(name: "Keep this pack", kind: .STATIC, stickers: [item])])
        let packID = UUID().uuidString.lowercased(), name = UUID().uuidString.lowercased() + ".webp"
        let manifest: [String: Any] = ["schema": 5, "packs": [["id": packID, "name": "Private reactions", "kind": "STATIC", "rating": "MATURE", "category": "GENERAL", "stickers": [["name": name, "sha256": MediaRules.hash(item.data), "label": "Reaction", "tags": "private"]]]]]
        let json = try JSONSerialization.data(withJSONObject: manifest)
        let url = try storedArchive([("manifest.json", json), ("packs/\(packID)/\(name)", item.data)], in: root)
        XCTAssertEqual(try PackArchive.read(url).first?.name, "Private reactions")
        var raw = try Data(contentsOf: url)
        let range = try XCTUnwrap(raw.range(of: Data("Private reactions".utf8)))
        raw[range.lowerBound] ^= 1 // P -> Q: valid JSON and valid metadata; only its CRC detects the damage.
        try raw.write(to: url, options: .atomic)
        let archive = try Archive(url: url, accessMode: .read)
        let entry = try XCTUnwrap(archive["manifest.json"])
        var changed = Data()
        let checksum: CRC32 = try archive.extract(entry, consumer: { changed.append($0) })
        XCTAssertNotEqual(checksum, entry.checksum)
        let parsed = try XCTUnwrap(JSONSerialization.jsonObject(with: changed) as? [String: Any])
        XCTAssertEqual((parsed["packs"] as? [[String: Any]])?.first?["name"] as? String, "Qrivate reactions")
        try await assertChecksumRejectionPreservesLibrary(url, store: store, root: root.appendingPathComponent("library"), original: existing, bytes: item.data)
    }

    func testGenericWebPWithWrongStoredCRCIsRejectedAndPreservesLibrary() async throws {
        let root = try directory()
        let store = LibraryStore(root: root.appendingPathComponent("library"))
        let item = try await photo(.systemCyan)
        let existing = try await store.importPacks([ImportedPack(name: "Keep this pack", kind: .STATIC, stickers: [item])])
        let url = try storedArchive([("photo.webp", item.data)], in: root)
        XCTAssertEqual(try PackArchive.read(url).count, 1)
        var raw = try Data(contentsOf: url)
        let central = try XCTUnwrap(raw.range(of: Data([0x50, 0x4b, 0x01, 0x02]), options: .backwards))
        // ZIPFoundation's stored writer has no data descriptor. Change the declared
        // CRC, leaving the complete, valid WebP payload untouched.
        XCTAssertEqual(raw[central.lowerBound + 8] & 0x08, 0)
        raw[central.lowerBound + 16] ^= 1
        try raw.write(to: url, options: .atomic)
        let archive = try Archive(url: url, accessMode: .read)
        let entry = try XCTUnwrap(archive["photo.webp"])
        var retainedImage = Data()
        let checksum: CRC32 = try archive.extract(entry, consumer: { retainedImage.append($0) })
        XCTAssertEqual(retainedImage, item.data)
        XCTAssertNotEqual(checksum, entry.checksum)
        try MediaRules.validate(retainedImage, kind: .STATIC)
        try await assertChecksumRejectionPreservesLibrary(url, store: store, root: root.appendingPathComponent("library"), original: existing, bytes: item.data)
    }

    func testMalformedCompressedPixelsFailAsAnErrorInsteadOfCrashing() throws {
        func chunk(_ tag: String, _ payload: [UInt8]) -> Data {
            var data = Data(tag.utf8)
            var size = UInt32(payload.count).littleEndian
            withUnsafeBytes(of: &size) { data.append(contentsOf: $0) }
            data.append(contentsOf: payload)
            if payload.count % 2 != 0 { data.append(0) }
            return data
        }
        var body = Data("WEBP".utf8)
        body.append(chunk("VP8X", [0, 0, 0, 0, 255, 1, 0, 255, 1, 0]))
        // Correct 512px lossless feature header, missing the pixel bitstream.
        body.append(chunk("VP8L", [47, 255, 193, 127, 0]))
        var riff = Data("RIFF".utf8)
        var length = UInt32(body.count).littleEndian
        withUnsafeBytes(of: &length) { riff.append(contentsOf: $0) }
        riff.append(body)
        XCTAssertNotNil(AppleMediaBridge().inspect(base64: riff.base64EncodedString()))
        XCTAssertThrowsError(try MediaRules.validate(riff, kind: .STATIC))
    }

    func testCatalogImportIsExplicitAndRevisionDeduplicated() async throws {
        let store = LibraryStore(root: try directory())
        let initial = try await store.load(); XCTAssertTrue(initial.isEmpty)
        let item = try await photo(.systemYellow)
        let input = [ImportedPack(name: "Reviewed photos", kind: .STATIC, stickers: [item])]
        _ = try await store.importPacks(input, sourceKey: "reviewed:revision")
        do { _ = try await store.importPacks(input, sourceKey: "reviewed:revision"); XCTFail("Revision duplicated") } catch { }
        let packs = try await store.load(); XCTAssertEqual(packs.count, 1)
    }

    func testSituationSearchRanksStickerMeaningBeforeBroadPackMatch() {
        XCTAssertEqual(StickerSearchRanking.rank(query: "work", label: "work", tags: "", packName: "Team", category: "GENERAL", fileName: "one.webp"), 0)
        XCTAssertEqual(StickerSearchRanking.rank(query: "work", label: "Relief", tags: "work", packName: "Team", category: "GENERAL", fileName: "two.webp"), 2)
        XCTAssertEqual(StickerSearchRanking.rank(query: "work", label: "Hello", tags: "", packName: "Work Reactions", category: "GENERAL", fileName: "three.webp"), 3)
        XCTAssertEqual(StickerSearchRanking.rank(query: "work", label: "Hello", tags: "", packName: "Team", category: "WORK", fileName: "four.webp"), 4)
    }

    func testFavoritesRenameAndDeletionPersistWithoutChangingAnotherPack() async throws {
        let root = try directory()
        let store = LibraryStore(root: root)
        let first = try await photo(.systemTeal), second = try await photo(.systemBrown)
        let packs = try await store.importPacks([
            ImportedPack(name: "First pack", kind: .STATIC, stickers: [first]),
            ImportedPack(name: "Keep this pack", kind: .STATIC, stickers: [second])
        ])
        try await store.toggleFavorite(packID: packs[0].id, stickerID: packs[0].stickers[0].id)
        try await store.rename(packs[0].id, name: "My favorite reactions")
        let reopened = LibraryStore(root: root)
        let saved = try await reopened.load()
        let edited = try XCTUnwrap(saved.first { $0.id == packs[0].id })
        XCTAssertEqual(edited.name, "My favorite reactions")
        XCTAssertTrue(edited.stickers[0].favorite)
        try await reopened.remove(packID: edited.id, stickerID: edited.stickers[0].id)
        try await reopened.remove(packID: edited.id)
        let remaining = try await LibraryStore(root: root).load()
        XCTAssertEqual(remaining, [packs[1]])
        let retained = try await reopened.bytes(pack: packs[1], sticker: packs[1].stickers[0])
        XCTAssertEqual(retained, second.data)
    }

    func testWhatsAppPayloadContainsExactBytesAndRejectsIncompletePack() async throws {
        let image = try await photo(.systemOrange)
        let items = (0..<3).map { SavedSticker(id: "item\($0)", fileName: "item\($0).webp", sha256: MediaRules.hash(image.data), label: "", tags: "") }
        let pack = SavedPack(id: "test-pack", name: "Actual export", kind: .STATIC, rating: .GENERAL, category: "ALL", stickers: items)
        let tray = try await MediaCreator().tray(image.data)
        let data = try WhatsAppHandoff.payload(pack: pack, images: [image.data, image.data, image.data], tray: tray)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let stickers = try XCTUnwrap(json["stickers"] as? [[String: Any]])
        XCTAssertEqual(stickers.count, 3)
        XCTAssertEqual(stickers[0]["image_data"] as? String, image.data.base64EncodedString())
        XCTAssertEqual(stickers[0]["emojis"] as? [String], ["🙂"])
        XCTAssertEqual(json["animated_sticker_pack"] as? Bool, false)
        XCTAssertThrowsError(try WhatsAppHandoff.payload(pack: pack, images: [image.data], tray: tray))
    }

    func testWhatsAppEmojisUseExplicitStickerMetadata() {
        XCTAssertEqual(WhatsAppHandoff.emojis(label: "Funny hello", tags: "work"), ["😂", "👋"])
        XCTAssertEqual(WhatsAppHandoff.emojis(label: "Monday mood", tags: "office"), ["🙂"])
        XCTAssertEqual(WhatsAppHandoff.emojis(label: "unhappy", tags: ""), ["🙂"])
    }

    func testStickerMetadataEditPersistsWithoutChangingMedia() async throws {
        let image = try await photo(.systemPurple)
        let root = try directory()
        let store = LibraryStore(root: root)
        let imported = try await store.importPacks([ImportedPack(name: "Situations", kind: .STATIC,
            stickers: [image])])
        let pack = try XCTUnwrap(imported.first)
        let sticker = try XCTUnwrap(pack.stickers.first)
        try await store.updateStickerMetadata(packID: pack.id, stickerID: sticker.id,
                                              label: "  Monday mood  ", tags: "work, funny")
        let reopened = LibraryStore(root: root)
        let reloaded = try await reopened.load()
        let saved = try XCTUnwrap(reloaded.first?.stickers.first)
        XCTAssertEqual(saved.label, "Monday mood")
        XCTAssertEqual(saved.tags, "work, funny")
        let bytes = try await reopened.bytes(pack: pack, sticker: saved)
        XCTAssertEqual(bytes, image.data)
        XCTAssertEqual(WhatsAppHandoff.emojis(label: saved.label, tags: saved.tags), ["😂"])
    }

    func testGenericWebPZipSeparatesStaticAndAnimatedWithoutExtracting() async throws {
        let red = try await photo(.systemRed)
        let blue = try await photo(.systemBlue)
        let base64 = try XCTUnwrap(AppleMediaBridge().mux(framesBase64: red.data.base64EncodedString() + "\n" + blue.data.base64EncodedString(), frameMs: 125))
        let animated = try XCTUnwrap(Data(base64Encoded: base64))
        let url = try directory().appendingPathComponent("phone.zip")
        let archive = try Archive(url: url, accessMode: .create)
        for (path, bytes) in [("phone/photo.webp", red.data), ("phone/moving.webp", animated)] {
            try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(bytes.count), provider: { position, count in bytes.subdata(in: Int(position)..<min(Int(position) + count, bytes.count)) })
        }
        let packs = try PackArchive.read(url)
        XCTAssertEqual(packs.count, 2)
        XCTAssertEqual(Set(packs.map(\.kind)), Set([StickerKind.STATIC, .ANIMATED]))
        XCTAssertEqual(packs.first(where: { $0.kind == .ANIMATED })?.stickers[0].data, animated)
    }
}
