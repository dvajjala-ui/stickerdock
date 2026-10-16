import Foundation
import UIKit
import AVFoundation
import ImageIO
import CoreImage
import Vision
import SDWebImage
import SDWebImageWebPCoder
import StickerDockCore

struct CreationRecipe: Equatable {
    var caption = ""
    var start: Double = 0
    var duration: Double = 2
    var fps = 8
    var fill = false
    var focusX: Double = 0.5
    var focusY: Double = 0.5
    var removeBackground = false
}

struct MediaInfo {
    let animated: Bool
    let duration: Double
}

/// Local CPU/codec work runs on this actor, outside SwiftUI's main actor.
actor MediaCreator {
    private let bridge = AppleMediaBridge()

    func inspect(_ url: URL) async throws -> MediaInfo {
        let data = try MediaRules.read(url, limit: 20_000_000)
        if let source = CGImageSourceCreateWithData(data as CFData, nil) {
            let count = CGImageSourceGetCount(source)
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] ?? [:]
            let width = properties[kCGImagePropertyPixelWidth] as? Int ?? 0
            let height = properties[kCGImagePropertyPixelHeight] as? Int ?? 0
            try require(width > 0 && height > 0 && width <= 16000 && height <= 16000 && width * height <= 20_000_000, "Image dimensions exceed the local creator limit.")
            try require(count <= 1000 && (count == 1 || Int64(width) * Int64(height) * Int64(count) <= 16_000_000), "This animation needs a smaller source file.")
            let duration = count > 1 ? (0..<count).reduce(0) { $0 + delay(source, $1) } : 0
            try require(count == 1 || (duration >= 0.5 && duration <= 600), "Choose a GIF between half a second and ten minutes.")
            if let details = bridge.inspect(base64: data.base64EncodedString()), details.animated {
                throw StickerError.invalid("Import existing animated WebP stickers in a StickerDock ZIP. Choose a GIF or video to create a new animation.")
            }
            return MediaInfo(animated: count > 1, duration: duration)
        }
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration).seconds
        try require(duration.isFinite && duration >= 0.5 && duration <= 600, "Choose a video between half a second and ten minutes.")
        guard let track = try await asset.loadTracks(withMediaType: .video).first else { throw StickerError.invalid("No video track found.") }
        let size = try await track.load(.naturalSize)
        try require(size.width > 0 && size.height > 0 && size.width * size.height <= 4_200_000, "Choose a video up to 1080p for this creator.")
        return MediaInfo(animated: true, duration: duration)
    }

    func create(_ url: URL, recipe: CreationRecipe, progress: @Sendable (String) -> Void) async throws -> ImportedPack {
        try require(recipe.caption.utf16.count <= 80 && !recipe.caption.unicodeScalars.contains { $0.value < 32 }, "Use a caption of up to 80 characters on one line.")
        try require(recipe.focusX.isFinite && recipe.focusY.isFinite && (0...1).contains(recipe.focusX) && (0...1).contains(recipe.focusY), "Invalid crop position.")
        let info = try await inspect(url)
        let data = try MediaRules.read(url, limit: 20_000_000)
        if !info.animated {
            guard let source = CGImageSourceCreateWithData(data as CFData, nil), let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: 1600, kCGImageSourceCreateThumbnailWithTransform: true] as CFDictionary) else { throw StickerError.invalid("Could not decode this photo.") }
            try Task.checkCancellation()
            progress("Preparing your sticker")
            let image = recipe.removeBackground ? try cutout(cg) : UIImage(cgImage: cg)
            let rendered = render(image, recipe: recipe)
            let bytes = try encode(rendered, limit: 99_950)
            guard let normalized = bridge.normalizeStatic(base64: bytes.base64EncodedString()), let output = Data(base64Encoded: normalized) else { throw StickerError.invalid("Could not validate the encoded photo.") }
            try MediaRules.validate(output, kind: .STATIC)
            return ImportedPack(name: "My reactions", kind: .STATIC, stickers: [ImportedSticker(data: output, label: recipe.caption)])
        }
        try require(!recipe.removeBackground, "Background removal is available for photos. Turn it off for clips.")
        try require([6, 8, 10].contains(recipe.fps) && recipe.duration.isFinite && recipe.start.isFinite && (0.5...4).contains(recipe.duration) && recipe.start >= 0 && recipe.start + recipe.duration <= info.duration + 0.001, "Choose a valid 0.5–4 second section.")
        let count = Int(ceil(recipe.duration * Double(recipe.fps)))
        let frameMs = Int((1000 / Double(recipe.fps)).rounded())
        let gif = CGImageSourceCreateWithData(data as CFData, nil)
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 1600, height: 1600)
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        defer { generator.cancelAllCGImageGeneration() }
        var frames: [String] = []
        var total = 0
        for index in 0..<count {
            try Task.checkCancellation()
            progress("Rendering frame \(index + 1) of \(count)")
            let time = recipe.start + Double(index) / Double(recipe.fps)
            let frame = try autoreleasepool { () throws -> Data in
                let cg: CGImage
                if let gif {
                    var elapsed: Double = 0
                    var selected = max(0, CGImageSourceGetCount(gif) - 1)
                    for i in 0..<CGImageSourceGetCount(gif) {
                        elapsed += delay(gif, i)
                        if time < elapsed { selected = i; break }
                    }
                    guard let image = CGImageSourceCreateImageAtIndex(gif, selected, nil) else { throw StickerError.invalid("Could not decode an animation frame.") }
                    cg = image
                } else {
                    cg = try generator.copyCGImage(at: CMTime(seconds: time, preferredTimescale: 600), actualTime: nil)
                }
                return try encode(render(UIImage(cgImage: cg), recipe: recipe), limit: min(24_000, 460_000 / count))
            }
            total += frame.count
            try require(total <= 490_000, "Clip is too detailed. Shorten the section or lower the motion rate.")
            frames.append(frame.base64EncodedString())
        }
        guard let result = bridge.mux(framesBase64: frames.joined(separator: "\n"), frameMs: Int32(frameMs)), let output = Data(base64Encoded: result) else { throw StickerError.invalid("Could not create a moving sticker. Try another section or lower motion rate.") }
        try MediaRules.validate(output, kind: .ANIMATED)
        return ImportedPack(name: "My moving reactions", kind: .ANIMATED, stickers: [ImportedSticker(data: output, label: recipe.caption)])
    }

    func tray(_ bytes: Data) throws -> Data {
        guard let image = SDImageWebPCoder.shared.decodedImage(with: bytes, options: [.decodeFirstFrameOnly: true, .decodeThumbnailPixelSize: CGSize(width: 96, height: 96)]) else { throw StickerError.invalid("Could not create the pack icon.") }
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let result = UIGraphicsImageRenderer(size: CGSize(width: 96, height: 96), format: format).image { _ in image.draw(in: CGRect(x: 0, y: 0, width: 96, height: 96)) }
        guard let png = result.pngData(), png.count <= 50_000 else { throw StickerError.invalid("Could not fit the pack icon.") }
        return png
    }

    private func encode(_ image: UIImage, limit: Int) throws -> Data {
        guard let data = SDImageWebPCoder.shared.encodedData(with: image, format: .webP, options: [.encodeCompressionQuality: 0.85, .encodeMaxFileSize: limit, .encodeFirstFrameOnly: true]), data.count <= limit else { throw StickerError.invalid("Sticker is too detailed. Try a simpler crop or shorter caption.") }
        return data
    }

    private func delay(_ source: CGImageSource, _ index: Int) -> Double {
        let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
        let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
        let value = gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double ?? gif?[kCGImagePropertyGIFDelayTime] as? Double ?? 0.1
        return value.isFinite ? max(0.008, min(value, 10)) : 0.1
    }

    private func render(_ image: UIImage, recipe: CreationRecipe) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: 512, height: 512), format: format).image { context in
            let area = CGRect(x: 8, y: 8, width: 496, height: recipe.caption.isEmpty ? 496 : 390)
            let scale = recipe.fill ? max(area.width / image.size.width, area.height / image.size.height) : min(area.width / image.size.width, area.height / image.size.height)
            let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let x = area.minX + (area.width - size.width) * CGFloat(recipe.fill ? recipe.focusX : 0.5)
            let y = area.minY + (area.height - size.height) * CGFloat(recipe.fill ? recipe.focusY : 0.5)
            context.cgContext.saveGState(); context.cgContext.clip(to: area)
            image.draw(in: CGRect(origin: CGPoint(x: x, y: y), size: size))
            context.cgContext.restoreGState()
            if !recipe.caption.isEmpty {
                let paragraph = NSMutableParagraphStyle(); paragraph.alignment = .center; paragraph.lineBreakMode = .byWordWrapping
                let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: recipe.caption.count > 36 ? 30 : 38), .foregroundColor: UIColor.white, .strokeColor: UIColor.black, .strokeWidth: -5, .paragraphStyle: paragraph]
                (recipe.caption as NSString).draw(in: CGRect(x: 12, y: 402, width: 488, height: 104), withAttributes: attributes)
            }
        }
    }

    private func cutout(_ image: CGImage) throws -> UIImage {
        let handler = VNImageRequestHandler(cgImage: image)
        let request = VNGenerateForegroundInstanceMaskRequest()
        try handler.perform([request])
        guard let result = request.results?.first else { throw StickerError.invalid("No subject found. Turn off background removal or choose another photo.") }
        let buffer = try result.generateMaskedImage(ofInstances: result.allInstances, from: handler, croppedToInstancesExtent: false)
        let input = CIImage(cvPixelBuffer: buffer)
        guard let cg = CIContext().createCGImage(input, from: input.extent) else { throw StickerError.invalid("Could not render the subject mask.") }
        return UIImage(cgImage: cg)
    }
}
