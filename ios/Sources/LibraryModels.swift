import Foundation
import CryptoKit
import StickerDockCore
import SDWebImage
import SDWebImageWebPCoder

enum StickerError: LocalizedError {
    case invalid(String)
    var errorDescription: String? { if case .invalid(let text) = self { return text }; return nil }
}

func require(_ condition: Bool, _ message: String) throws {
    if !condition { throw StickerError.invalid(message) }
}

enum StickerKind: String, Codable, CaseIterable { case STATIC, ANIMATED }
enum ContentRating: String, Codable, CaseIterable { case GENERAL, MATURE }

struct SavedSticker: Codable, Identifiable, Hashable {
    var id: String
    var fileName: String
    var sha256: String
    var label: String
    var tags: String
    var favorite: Bool = false
}

struct SavedPack: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var kind: StickerKind
    var rating: ContentRating
    var category: String
    var stickers: [SavedSticker]
    var updatedAt: Date = Date()
}

struct LibraryDocument: Codable {
    var schema: Int = 1
    var packs: [SavedPack] = []
    var catalogImports: [String: [String]] = [:]
}

struct ImportedSticker {
    var data: Data
    var label: String = ""
    var tags: String = ""
}

struct ImportedPack {
    var name: String
    var kind: StickerKind
    var rating: ContentRating = .GENERAL
    var category: String = "ALL"
    var stickers: [ImportedSticker]
}

enum MediaRules {
    static func hash(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func validName(_ name: String) -> Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        name.utf16.count <= 60 && !name.unicodeScalars.contains { $0.value < 32 }
    }

    static func safeID(_ value: String) -> Bool {
        value.range(of: "^[A-Za-z0-9_-]{1,64}$", options: .regularExpression) != nil
    }

    static func validate(_ data: Data, kind: StickerKind) throws {
        try require(data.count >= 30 && data.count <= (kind == .ANIMATED ? 500_000 : 100_000), "Sticker exceeds the size limit.")
        guard let info = AppleMediaBridge().inspect(base64: data.base64EncodedString()) else {
            throw StickerError.invalid("Invalid 512px WebP sticker.")
        }
        try require(info.animated == (kind == .ANIMATED), "Keep static and animated stickers in separate packs.")
        // This coder budgets all declared animation frames even when decoding
        // only the first one. Account for that estimate to avoid shrinking a
        // valid 512px poster; decodeFirstFrameOnly still allocates one image.
        let decodeBudget = 4 * 1024 * 1024 * max(1, Int(info.frames))
        guard let image = safeWebPFirstFrame(data: data, thumbnail: .zero, limitBytes: UInt(decodeBudget)), let cg = image.cgImage else { throw StickerError.invalid("The sticker image could not be decoded.") }
        try require(cg.width == 512 && cg.height == 512, "Decoded sticker must be 512px; received \(cg.width)×\(cg.height).")
    }

    static func read(_ url: URL, limit: Int) throws -> Data {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        let bytes = try file.read(upToCount: limit + 1) ?? Data()
        try require(bytes.count <= limit, "File exceeds the size limit.")
        return bytes
    }
}
