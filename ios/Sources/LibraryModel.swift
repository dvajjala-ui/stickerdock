import SwiftUI
import SDWebImage
import SDWebImageWebPCoder

@MainActor final class LibraryModel: ObservableObject {
    @Published var packs: [SavedPack] = []
    @Published var catalog: [CatalogPack] = []
    @Published var busy: String?
    @Published var error: String?
    @Published var notice: String?
    @Published var shareURL: URL?
    @Published var incomingPublicPackID: String?
    let root: URL
    let store: LibraryStore
    let creator = MediaCreator()
    let catalogClient: CatalogClient
    private var operation: Task<Void, Never>?

    init(root: URL? = nil, catalogBase: URL? = nil) {
        let isolatedTest = ProcessInfo.processInfo.arguments.contains("--ui-test-isolated")
        self.root = root ?? (isolatedTest ? FileManager.default.temporaryDirectory.appendingPathComponent("ui-test-" + UUID().uuidString) : URL.applicationSupportDirectory.appendingPathComponent("StickerDock", isDirectory: true))
        self.store = LibraryStore(root: self.root)
        let configured = Bundle.main.object(forInfoDictionaryKey: "StickerDockCatalogBase") as? String ?? ""
        self.catalogClient = CatalogClient(base: isolatedTest ? nil : (catalogBase ?? URL(string: configured)))
        SDImageCodersManager.shared.addCoder(SDImageWebPCoder.shared)
    }

    func mediaURL(_ pack: SavedPack, _ item: SavedSticker) -> URL {
        root.appendingPathComponent(pack.id).appendingPathComponent(item.fileName)
    }

    func refresh() async throws { packs = try await store.load().sorted { $0.updatedAt > $1.updatedAt } }

    func run(_ label: String, action: @escaping @MainActor () async throws -> Void) {
        guard operation == nil else { return }
        busy = label; notice = nil
        operation = Task {
            defer { busy = nil; operation = nil }
            do { try await action(); try await refresh() }
            catch is CancellationError { notice = "Cancelled. Your existing stickers are unchanged." }
            catch { self.error = error.localizedDescription }
        }
    }

    func cancel() { operation?.cancel() }

    func importArchive(_ url: URL) {
        run("Checking sticker archive") {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let worker = Task.detached { try PackArchive.read(url) }
            let inputs = try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() })
            _ = try await self.store.importPacks(inputs)
            self.notice = "Imported \(inputs.count) packs. Add each pack to WhatsApp when you choose."
        }
    }

    func share(_ packs: [SavedPack]) {
        run("Preparing your ZIP") { self.shareURL = try await PackArchive.write(packs, store: self.store) }
    }

    func addToWhatsApp(_ pack: SavedPack) {
        run("Preparing WhatsApp pack") {
            var images: [Data] = []
            for item in pack.stickers { images.append(try await self.store.bytes(pack: pack, sticker: item)) }
            guard let first = images.first else { throw StickerError.invalid("Add at least three stickers first.") }
            let tray = try await self.creator.tray(first)
            let payload = try WhatsAppHandoff.payload(pack: pack, images: images, tray: tray)
            try Task.checkCancellation()
            try await WhatsAppHandoff.open(payload: payload)
            self.notice = "Confirm the pack inside WhatsApp. Opening WhatsApp does not confirm installation."
        }
    }
}
