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
        let restored = try PackArchive.read(archive)
        XCTAssertEqual(restored.first?.rating, .MATURE)
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

    func testArchiveTraversalIsRejectedWithoutExtractingFiles() throws {
        let root = try directory()
        let url = root.appendingPathComponent("malicious.zip")
        let archive = try Archive(url: url, accessMode: .create)
        let data = Data("do not extract".utf8)
        try archive.addEntry(with: "../escape.webp", type: .file, uncompressedSize: Int64(data.count), provider: { position, count in data.subdata(in: Int(position)..<min(Int(position) + count, data.count)) })
        XCTAssertThrowsError(try PackArchive.read(url))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.deletingLastPathComponent().appendingPathComponent("escape.webp").path))
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
        XCTAssertEqual(json["animated_sticker_pack"] as? Bool, false)
        XCTAssertThrowsError(try WhatsAppHandoff.payload(pack: pack, images: [image.data], tray: tray))
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
