import XCTest
import UIKit
import ImageIO
import UniformTypeIdentifiers
import ZIPFoundation
import StickerDockCore
@testable import StickerDock

final class PhotoArchiveTests: XCTestCase {
    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("photo-archive-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    private func png() throws -> Data {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: 300, height: 300), format: format).image { context in
            UIColor.systemRed.setFill(); context.fill(CGRect(x: 60, y: 60, width: 180, height: 180))
        }
        return try XCTUnwrap(image.pngData())
    }

    private func jpeg(orientation: Int = 1) throws -> Data {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: 640, height: 320), format: format).image { context in
            UIColor.systemBlue.setFill(); context.fill(CGRect(x: 0, y: 0, width: 640, height: 320))
        }
        let data = NSMutableData()
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try XCTUnwrap(image.cgImage), [kCGImagePropertyOrientation: orientation] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return data as Data
    }

    private func archive(_ files: [(String, Data)]) throws -> URL {
        let url = try directory().appendingPathComponent("photos.zip")
        let zip = try Archive(url: url, accessMode: .create)
        for (path, data) in files {
            try zip.addEntry(with: path, type: .file, uncompressedSize: Int64(data.count), provider: { position, size in
                data.subdata(in: Int(position)..<min(Int(position) + size, data.count))
            })
        }
        return url
    }

    private func alpha(_ data: Data, x: Int, y: Int) throws -> UInt8 {
        let image = try XCTUnwrap(safeWebPFirstFrame(data: data, thumbnail: .zero, limitBytes: 4 * 1024 * 1024)?.cgImage)
        var pixels = [UInt8](repeating: 0, count: 512 * 512 * 4)
        try pixels.withUnsafeMutableBytes { buffer in
            let context = try XCTUnwrap(CGContext(data: buffer.baseAddress, width: 512, height: 512, bitsPerComponent: 8,
                bytesPerRow: 512 * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(image, in: CGRect(x: 0, y: 0, width: 512, height: 512))
        }
        return pixels[(y * 512 + x) * 4 + 3]
    }

    func testPhotoZipConvertsPNGAndJPEGDeduplicatesAndKeepsAnimation() async throws {
        let transparent = try png()
        let red = try MediaCreator.archivePhoto(transparent)
        let blue = try MediaCreator.archivePhoto(jpeg())
        let encoded = try XCTUnwrap(AppleMediaBridge().mux(framesBase64: red.base64EncodedString() + "\n" + blue.base64EncodedString(), frameMs: 125))
        let moving = try XCTUnwrap(Data(base64Encoded: encoded))
        let url = try archive([("photos/transparent.PNG", transparent), ("photos/copy.png", transparent),
                               ("photos/landscape.JPEG", try jpeg()), ("photos/moving.webp", moving)])
        let packs = try PackArchive.read(url)
        XCTAssertEqual(packs.count, 2)
        let photos = try XCTUnwrap(packs.first { $0.kind == .STATIC })
        XCTAssertEqual(photos.stickers.count, 2)
        XCTAssertEqual(Set(photos.stickers.map(\.label)), Set(["transparent", "landscape"]))
        let item = try XCTUnwrap(photos.stickers.first { $0.label == "transparent" })
        XCTAssertEqual(try alpha(item.data, x: 40, y: 40), 0)
        XCTAssertGreaterThan(try alpha(item.data, x: 256, y: 256), 240)
        for sticker in photos.stickers { try MediaRules.validate(sticker.data, kind: .STATIC) }
        XCTAssertEqual(packs.first { $0.kind == .ANIMATED }?.stickers.first?.data, moving)
        let root = try directory()
        let store = LibraryStore(root: root)
        let saved = try await store.importPacks(packs)
        XCTAssertEqual(saved.count, 2)
        let reopened = LibraryStore(root: root)
        let retained = try await reopened.load()
        XCTAssertEqual(retained.map { $0.stickers.count }.sorted(), [1, 2])
        let export = try await PackArchive.write(retained, store: reopened)
        defer { try? FileManager.default.removeItem(at: export.deletingLastPathComponent()) }
        let roundTrip = try PackArchive.read(export)
        XCTAssertEqual(Set(roundTrip.flatMap { $0.stickers.map { MediaRules.hash($0.data) } }),
                       Set(packs.flatMap { $0.stickers.map { MediaRules.hash($0.data) } }))
        let attachment = XCTAttachment(contentsOfFile: export)
        attachment.name = "native-photo-archive-roundtrip.zip"; attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testJPEGOrientationIsAppliedBeforeFittingTheSticker() throws {
        let packs = try PackArchive.read(archive([("portrait.jpg", try jpeg(orientation: 6))]))
        let data = try XCTUnwrap(packs.first?.stickers.first?.data)
        // EXIF 6 rotates the 2:1 source into a portrait; horizontal sides must
        // remain transparent, while top/bottom artwork reaches the canvas edge.
        XCTAssertEqual(try alpha(data, x: 30, y: 256), 0)
        XCTAssertGreaterThan(try alpha(data, x: 256, y: 30), 240)
        XCTAssertGreaterThan(try alpha(data, x: 256, y: 480), 240)
    }

    func testBadLaterPhotoRejectsWholeArchiveWithoutChangingSavedLibrary() async throws {
        let root = try directory()
        let store = LibraryStore(root: root)
        let bytes = try MediaCreator.archivePhoto(png())
        let old = try await store.importPacks([ImportedPack(name: "Keep private", kind: .STATIC, rating: .MATURE,
            stickers: [ImportedSticker(data: bytes)])])
        let before = try Data(contentsOf: root.appendingPathComponent("library.json"))
        let url = try archive([("good.png", try png()), ("broken.jpg", Data("not a JPEG".utf8))])
        XCTAssertThrowsError(try PackArchive.read(url))
        XCTAssertEqual(try Data(contentsOf: root.appendingPathComponent("library.json")), before)
        let retained = try await store.bytes(pack: old[0], sticker: old[0].stickers[0])
        XCTAssertEqual(retained, bytes)
    }

    func testGIFRenamedAsPhotoIsRejectedRatherThanFlattened() throws {
        let bytes = try png()
        let source = try XCTUnwrap(CGImageSourceCreateWithData(bytes as CFData, nil))
        let image = try XCTUnwrap(CGImageSourceCreateImageAtIndex(source, 0, nil))
        let data = NSMutableData()
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data, UTType.gif.identifier as CFString, 2, nil))
        CGImageDestinationAddImage(destination, image, nil)
        CGImageDestinationAddImage(destination, image, nil)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        XCTAssertThrowsError(try PackArchive.read(archive([("renamed.png", data as Data)])))
    }
}
