import XCTest

final class NavigationTests: XCTestCase {
    func testEmptyLibraryCreationAndSettingsNavigation() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-test-isolated"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Your library"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Your reactions belong here"].exists)
        app.buttons["WhatsApp packs"].tap()
        XCTAssertTrue(app.staticTexts["No packs yet"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Create stickers"].exists)
        app.buttons["Done"].tap()
        app.tabBars.buttons["Create"].tap()
        XCTAssertTrue(app.buttons["Choose from Photos"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Choose from Files"].exists)
        let createShot = XCTAttachment(screenshot: app.screenshot()); createShot.name = "Create"; createShot.lifetime = .keepAlways; add(createShot)
        app.tabBars.buttons["Discover"].tap()
        XCTAssertTrue(app.navigationBars["Discover"].waitForExistence(timeout: 5))
        let discoverShot = XCTAttachment(screenshot: app.screenshot()); discoverShot.name = "Discover"; discoverShot.lifetime = .keepAlways; add(discoverShot)
        app.tabBars.buttons["Library"].tap()
        app.buttons["Settings and backup"].tap()
        XCTAssertTrue(app.buttons["Import sticker ZIP"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars["Settings"].exists)
        XCTAssertFalse(app.buttons["Export library backup"].isEnabled)
        app.buttons["Done"].tap()
        XCTAssertTrue(app.navigationBars["Your library"].exists)
        XCTAssertFalse(app.navigationBars["Settings"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
