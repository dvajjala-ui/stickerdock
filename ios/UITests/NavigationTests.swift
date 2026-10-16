import XCTest

final class NavigationTests: XCTestCase {
    func testEmptyLibraryCreationAndSettingsNavigation() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-test-isolated"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Your library"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Your reactions belong here"].exists)
        app.tabBars.buttons["Create"].tap()
        XCTAssertTrue(app.buttons["Choose from Photos"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Choose from Files"].exists)
        app.tabBars.buttons["Discover"].tap()
        XCTAssertTrue(app.navigationBars["Discover"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Library"].tap()
        app.buttons["Settings and backup"].tap()
        XCTAssertTrue(app.buttons["Import sticker ZIP"].waitForExistence(timeout: 5))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.navigationBars["Your library"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
