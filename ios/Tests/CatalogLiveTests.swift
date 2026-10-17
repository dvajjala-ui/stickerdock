import XCTest
@testable import StickerDock

/// Read-only production integration. This does not upload, publish, sign in,
/// consume a model budget or alter any server/library state.
final class CatalogLiveTests: XCTestCase {
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
