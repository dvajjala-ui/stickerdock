import Foundation

/// A shared identifier is resolved only against the reviewed public catalog.
enum PublicPackLinks {
    static func parse(_ raw: String) -> String? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.utf8.count <= 256 else { return nil }
        let id: String
        if value.hasPrefix("SD:") {
            id = String(value.dropFirst(3))
        } else {
            guard let url = URLComponents(string: value), url.scheme == "stickerdock",
                  url.host == "pack", url.user == nil, url.password == nil,
                  url.port == nil, url.query == nil, url.fragment == nil,
                  url.percentEncodedPath.hasPrefix("/") else { return nil }
            id = String(url.percentEncodedPath.dropFirst())
        }
        guard id.range(of: "^[a-z0-9-]{3,60}$", options: .regularExpression) != nil else { return nil }
        return id
    }

    static func shareText(name: String, id: String) -> String {
        "\(name)\nSD:\(id)\nstickerdock://pack/\(id)\n\nOpen this code in StickerDock → Discover → Open pack code.\nGet StickerDock: https://stickerdock.vercel.app/download"
    }
}
