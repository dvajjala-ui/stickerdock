import XCTest
import UIKit
import SDWebImage
import StickerDockCore
@testable import StickerDock

final class MotionCreatorTests: XCTestCase {
    func testNativeGIFAndVideoProduceMovingBoundedWebPWithoutTouchingSource() async throws {
        for (name, ext) in [("motion", "gif"), ("motion", "mp4"), ("motion-rotated", "mp4")] {
            let source = try XCTUnwrap(Bundle(for: type(of: self)).url(forResource: name, withExtension: ext))
            let before = try Data(contentsOf: source)
            let creator = MediaCreator()
            var recipe = CreationRecipe(); recipe.duration = 1
            if ext == "gif" { recipe.caption = "Actual codec check" }
            let result = try await creator.create(source, recipe: recipe) { _ in }
            XCTAssertEqual(result.kind, .ANIMATED)
            let bytes = try XCTUnwrap(result.stickers.first?.data)
            let info = try XCTUnwrap(AppleMediaBridge().inspect(base64: bytes.base64EncodedString()))
            XCTAssertEqual(info.frames, 8)
            XCTAssertEqual(info.durationMs, 1000)
            XCTAssertLessThanOrEqual(bytes.count, 500_000)
            let animation = try XCTUnwrap(SDAnimatedImage(data: bytes))
            let first = try XCTUnwrap(animation.animatedImageFrame(at: 0)?.pngData())
            let last = try XCTUnwrap(animation.animatedImageFrame(at: 7)?.pngData())
            XCTAssertNotEqual(first, last, "Output repeats one static frame")
            if ext == "mp4" {
                let image = try XCTUnwrap(animation.animatedImageFrame(at: 0)?.cgImage)
                let extent = try opaqueExtent(image)
                if name.contains("rotated") { XCTAssertGreaterThan(extent.height, extent.width, "Display rotation was lost") }
                else { XCTAssertGreaterThan(extent.width, extent.height) }
            }
            XCTAssertEqual(try Data(contentsOf: source), before)
        }
    }

    private func opaqueExtent(_ image: CGImage) throws -> CGSize {
        var pixels = [UInt8](repeating: 0, count: 512 * 512 * 4)
        try pixels.withUnsafeMutableBytes { pointer in
            let context = try XCTUnwrap(CGContext(data: pointer.baseAddress, width: 512, height: 512, bitsPerComponent: 8, bytesPerRow: 512 * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
            context.draw(image, in: CGRect(x: 0, y: 0, width: 512, height: 512))
        }
        var left = 512, top = 512, right = -1, bottom = -1
        for y in 0..<512 { for x in 0..<512 where pixels[(y * 512 + x) * 4 + 3] > 16 { left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y) } }
        try require(right >= left && bottom >= top, "Rendered output is empty.")
        return CGSize(width: right - left + 1, height: bottom - top + 1)
    }
}
