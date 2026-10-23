import XCTest
@testable import StickerDock

/// Read-only production integration. This does not upload, publish, sign in,
/// consume a model budget or alter any server/library state.
final class CatalogLiveTests: XCTestCase {
    func testPublishedAnimatedPackDownloadsAndSavesWithMotionIntact() async throws {
        let base = try XCTUnwrap(URL(string: "https://qqicsllktmcmvmzplsxl.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/"))
        let client = CatalogClient(base: base)
        let entries = try await client.browse()
        let entry = try XCTUnwrap(entries.first(where: { $0.id == "pack-47f225c3-258a-576c-91f3-24776cbf9eb6" }))
        XCTAssertEqual(entry.rating, "GENERAL")
        XCTAssertEqual(entry.kinds, ["ANIMATED"])
        XCTAssertEqual(entry.stickerCount, 3)
        XCTAssertEqual(entry.sha256, "c22c5c63db1bc772c79aba05a68b57e343f2c21a952fa4f86cea4f33240351fc")
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("live-motion-test-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(root: root)
        let before = try await store.load(); XCTAssertTrue(before.isEmpty)
        let packs = try await client.download(entry)
        XCTAssertEqual(packs.count, 1)
        let pack = try XCTUnwrap(packs.first)
        XCTAssertEqual(pack.kind, .ANIMATED)
        XCTAssertEqual(pack.stickers.count, 3)
        XCTAssertEqual(pack.stickers.map(\.label), ["Meri bhi sun lo!", "Busy hoon, baad mein.", "Khaana kidhar hai?"])
        for sticker in pack.stickers { try MediaRules.validate(sticker.data, kind: .ANIMATED) }
        let afterDownload = try await store.load()
        XCTAssertTrue(afterDownload.isEmpty, "Preview/download must not silently import")
        let saved = try await store.importPacks(packs, sourceKey: entry.id + ":" + entry.sha256)
        XCTAssertEqual(saved.count, 1)
        XCTAssertEqual(saved.first?.kind, .ANIMATED)
        XCTAssertEqual(saved.first?.stickers.count, 3)
        let reopened = try await LibraryStore(root: root).load()
        XCTAssertEqual(reopened, saved)
    }

    func testReviewedCatalogCanBePreviewedThenExplicitlySavedLocally() async throws {
        let base = try XCTUnwrap(URL(string: "https://qqicsllktmcmvmzplsxl.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/"))
        let client = CatalogClient(base: base)
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("live-catalog-test-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(root: root)
        let entries = try await client.browse()
        guard let entry = entries.first else { throw XCTSkip("No reviewed public pack is available for live catalog acceptance.") }
        XCTAssertEqual(entry.rating, "GENERAL")
        let before = try await store.load(); XCTAssertTrue(before.isEmpty)
        let preview = try await client.preview(entry)
        if entry.preview != nil { XCTAssertNotNil(preview) }
        let packs = try await client.download(entry)
        XCTAssertFalse(packs.isEmpty)
        XCTAssertTrue(packs.allSatisfy { $0.rating == .GENERAL && !$0.stickers.isEmpty })
        let afterPreview = try await store.load(); XCTAssertTrue(afterPreview.isEmpty)
        let saved = try await store.importPacks(packs, sourceKey: entry.id + ":" + entry.sha256)
        XCTAssertEqual(saved.count, packs.count)
        let reopened = try await LibraryStore(root: root).load()
        XCTAssertEqual(reopened, saved)
    }
}
