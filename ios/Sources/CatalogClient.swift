import Foundation

struct CatalogPack: Identifiable, Decodable {
    let id: String
    let name: String
    let creator: String
    let file: String
    let sha256: String
    let rating: String?
    let preview: Preview?
    struct Preview: Decodable { let file: String; let sha256: String }
}

private final class NoRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

actor CatalogClient {
    private let base: URL?
    private let session: URLSession
    init(base: URL?) {
        self.base = base
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 40
        configuration.httpMaximumConnectionsPerHost = 3
        self.session = URLSession(configuration: configuration, delegate: NoRedirects(), delegateQueue: nil)
    }

    func browse() async throws -> [CatalogPack] {
        guard let base else { return [] }
        let data = try await fetch(base.appendingPathComponent("packs.json"), limit: 512_000, allowMissing: true)
        if data.isEmpty { return [] }
        struct Index: Decodable { let version: Int; let packs: [CatalogPack] }
        let index = try JSONDecoder().decode(Index.self, from: data)
        try require(index.version == 1 && index.packs.count <= 100, "Unsupported catalog.")
        var ids = Set<String>()
        return try index.packs.filter { $0.rating == "GENERAL" }.map { pack in
            try require(pack.id.range(of: "^[a-z0-9-]{3,60}$", options: .regularExpression) != nil && ids.insert(pack.id).inserted && MediaRules.validName(pack.name) && MediaRules.validName(pack.creator), "Invalid catalog entry.")
            try require(pack.file.range(of: "^[A-Za-z0-9_-]{1,64}\\.zip$", options: .regularExpression) != nil && validHash(pack.sha256), "Invalid catalog archive.")
            if let preview = pack.preview { try require(validHash(preview.sha256) && preview.file == preview.sha256 + ".webp", "Invalid catalog preview.") }
            return pack
        }
    }

    func preview(_ pack: CatalogPack) async throws -> Data? {
        guard let base, let preview = pack.preview else { return nil }
        let bytes = try await fetch(base.appendingPathComponent("previews").appendingPathComponent(preview.file), limit: 320_000)
        try require(MediaRules.hash(bytes) == preview.sha256, "Preview failed its integrity check.")
        return bytes
    }

    /// Fetches only when the user explicitly chooses Preview or Save.
    func download(_ pack: CatalogPack) async throws -> [ImportedPack] {
        guard let base else { throw StickerError.invalid("Discover is not configured.") }
        let bytes = try await fetch(base.appendingPathComponent("packs").appendingPathComponent(pack.file), limit: 25_000_000)
        try require(MediaRules.hash(bytes) == pack.sha256, "Pack failed its integrity check.")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".zip")
        defer { try? FileManager.default.removeItem(at: url) }
        try bytes.write(to: url, options: .atomic)
        let packs = try PackArchive.read(url)
        try require(packs.allSatisfy { $0.rating == .GENERAL }, "Public packs cannot contain mature content.")
        return packs
    }

    private func validHash(_ hash: String) -> Bool { hash.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil }

    private func fetch(_ url: URL, limit: Int, allowMissing: Bool = false) async throws -> Data {
        guard let base else { throw StickerError.invalid("Discover is not configured.") }
        try require(base.scheme == "https" && base.user == nil && base.password == nil && base.query == nil && base.fragment == nil && base.absoluteString.hasSuffix("/") && url.absoluteString.hasPrefix(base.absoluteString), "Catalog requires its configured HTTPS origin.")
        let (stream, response) = try await session.bytes(from: url)
        guard let response = response as? HTTPURLResponse else { throw StickerError.invalid("Invalid catalog response.") }
        if allowMissing && response.statusCode == 404 { return Data() }
        try require(response.statusCode == 200 && response.expectedContentLength <= Int64(limit), "Catalog is unavailable or exceeds its limit.")
        var bytes = Data()
        bytes.reserveCapacity(min(limit, max(0, Int(response.expectedContentLength))))
        var chunk: [UInt8] = []
        chunk.reserveCapacity(8192)
        var received = 0
        for try await byte in stream {
            received += 1
            try require(received <= limit, "Catalog response is too large.")
            chunk.append(byte)
            if chunk.count == 8192 {
                try Task.checkCancellation()
                bytes.append(contentsOf: chunk); chunk.removeAll(keepingCapacity: true)
            }
        }
        try Task.checkCancellation()
        bytes.append(contentsOf: chunk)
        return bytes
    }
}
