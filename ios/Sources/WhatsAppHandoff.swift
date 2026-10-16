import UIKit

/// Implements the documented third-party pack protocol. Opening WhatsApp is a
/// handoff, never proof that the user accepted or installed the pack.
enum WhatsAppHandoff {
    static func payload(pack: SavedPack, images: [Data], tray: Data) throws -> Data {
        try require((3...30).contains(images.count) && images.count == pack.stickers.count, "WhatsApp needs 3–30 stickers in a pack.")
        try require(tray.count <= 50_000 && !tray.isEmpty, "Tray icon exceeds the size limit.")
        for image in images { try MediaRules.validate(image, kind: pack.kind) }
        return try JSONSerialization.data(withJSONObject: [
            "identifier": pack.id, "name": pack.name, "publisher": "StickerDock",
            "tray_image": tray.base64EncodedString(),
            "animated_sticker_pack": pack.kind == .ANIMATED,
            "stickers": images.map { bytes in
                ["image_data": bytes.base64EncodedString(), "emojis": [String]()] as [String: Any]
            }
        ])
    }

    @MainActor static func open(payload: Data) async throws {
        guard let url = URL(string: "whatsapp://stickerPack"), UIApplication.shared.canOpenURL(url) else { throw StickerError.invalid("Install WhatsApp to add this pack. Your library stays available offline.") }
        // Official WhatsApp protocol reads this type from the general pasteboard.
        // Only an explicit user Add action calls this method; never background it.
        let board = UIPasteboard.general
        board.setItems([["net.whatsapp.third-party.sticker-pack": payload]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
        let opened = await UIApplication.shared.open(url, options: [:])
        try require(opened, "WhatsApp could not be opened. Try again.")
    }
}
