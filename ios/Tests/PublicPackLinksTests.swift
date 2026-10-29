import XCTest
@testable import StickerDock

final class PublicPackLinksTests: XCTestCase {
    func testPublicCodeAndLinkResolveOnlyBoundedCatalogIdentifiers() {
        XCTAssertEqual(PublicPackLinks.parse("SD:pack-example-123"), "pack-example-123")
        XCTAssertEqual(PublicPackLinks.parse("stickerdock://pack/pack-example-123"), "pack-example-123")
        for value in ["https://example.com/pack-example-123", "stickerdock://evil/pack-example-123",
                      "stickerdock://pack/pack-example-123?x=1", "stickerdock://pack/../secret",
                      "SD:../../secret", "SD:" + String(repeating: "a", count: 61)] {
            XCTAssertNil(PublicPackLinks.parse(value), value)
        }
    }
}
