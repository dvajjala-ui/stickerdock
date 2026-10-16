import SwiftUI
import UniformTypeIdentifiers

@main struct StickerDockApp: App {
    @StateObject private var model = LibraryModel()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(model)
                .task { do { try await model.refresh() } catch { model.error = error.localizedDescription } }
                .onOpenURL { url in
                    if url.pathExtension.lowercased() == "zip" { model.importArchive(url) }
                    else { model.error = "Open StickerDock ZIP backups here. Use Create for photos or clips." }
                }
        }
    }
}

private enum HomeTab: Hashable { case discover, create, library }

private struct CreatorSheetPresentationKey: EnvironmentKey { static let defaultValue: Binding<Bool> = .constant(false) }
private extension EnvironmentValues {
    var creatorSheetPresentation: Binding<Bool> {
        get { self[CreatorSheetPresentationKey.self] }
        set { self[CreatorSheetPresentationKey.self] = newValue }
    }
}

struct OperationProgressBar: View {
    @EnvironmentObject var model: LibraryModel
    var body: some View {
        if let busy = model.busy {
            HStack { ProgressView(); Text(busy).font(.footnote); Spacer(); Button("Cancel", action: model.cancel).accessibilityIdentifier("cancel-operation") }
                .padding().background(.regularMaterial).accessibilityIdentifier("operation-progress")
        }
    }
}

private struct OperationFeedback: ViewModifier {
    @EnvironmentObject var model: LibraryModel
    var active: Bool
    func body(content: Content) -> some View {
        content
            .alert("Couldn’t finish", isPresented: Binding(get: { active && model.error != nil }, set: { if !$0 && active { model.error = nil } })) { Button("OK") { model.error = nil } } message: { Text(model.error ?? "") }
            .overlay(alignment: .top) {
                if active, let notice = model.notice {
                    HStack { Text(notice).font(.callout); Button { model.notice = nil } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel("Dismiss message") }
                        .padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18)).padding().shadow(radius: 3)
                }
            }
    }
}

extension View {
    func operationFeedback(active: Bool = true) -> some View { modifier(OperationFeedback(active: active)) }
}

struct RootView: View {
    @EnvironmentObject var model: LibraryModel
    @State private var tab: HomeTab = .library
    @State private var settings = false
    @State private var creatorPresented = false
    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { DiscoverView() }.tabItem { Label("Discover", systemImage: "sparkles") }.tag(HomeTab.discover)
            NavigationStack { CreatorView() }.tabItem { Label("Create", systemImage: "plus.app") }.tag(HomeTab.create)
            NavigationStack {
                LibraryView()
                    .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { settings = true } label: { Image(systemName: "gearshape") }.accessibilityLabel("Settings and backup") } }
            }.tabItem { Label("Library", systemImage: "square.grid.2x2") }.tag(HomeTab.library)
        }
        .environment(\.creatorSheetPresentation, $creatorPresented)
        .tint(.indigo)
        .safeAreaInset(edge: .bottom) { OperationProgressBar() }
        .sheet(isPresented: $settings) { NavigationStack { SettingsView() } }
        .sheet(isPresented: Binding(get: { !settings && model.shareURL != nil }, set: { if !$0 && !settings { model.shareURL = nil } })) {
            if let url = model.shareURL { ShareSheet(url: url) }
        }
        .operationFeedback(active: !settings && !creatorPresented)
    }
}

struct LibraryView: View {
    @EnvironmentObject var model: LibraryModel
    @State private var query = ""
    @State private var kind = "All"
    @State private var favorites = false
    @State private var importer = false
    private var filtered: [SavedPack] {
        model.packs.filter { pack in
            (kind == "All" || pack.kind.rawValue == kind) && (!favorites || pack.stickers.contains { $0.favorite }) &&
            (query.isEmpty || ([pack.name, pack.category] + pack.stickers.flatMap { [$0.label, $0.tags] }).contains { $0.localizedStandardContains(query) })
        }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Picker("Sticker type", selection: $kind) { Text("All").tag("All"); Text("Static").tag("STATIC"); Text("Animated").tag("ANIMATED") }.pickerStyle(.segmented)
                    Button { favorites.toggle() } label: { Image(systemName: favorites ? "heart.fill" : "heart") }.accessibilityLabel("Show favorite packs").accessibilityAddTraits(favorites ? .isSelected : [])
                }
                if model.packs.isEmpty {
                    ContentUnavailableView { Label("Your reactions belong here", systemImage: "face.smiling") } description: { Text("Create from a photo or clip, or import a StickerDock ZIP. Your library stays on this device.") } actions: { Button("Import sticker ZIP") { importer = true }.buttonStyle(.borderedProminent) }
                } else if filtered.isEmpty { ContentUnavailableView.search(text: query) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 14)], spacing: 14) {
                    ForEach(filtered) { pack in
                        NavigationLink(value: pack.id) {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 4) { ForEach(Array(pack.stickers.prefix(3))) { item in StickerImage(url: model.mediaURL(pack, item)).frame(height: 100) } }.frame(maxWidth: .infinity, minHeight: 110)
                                Text(pack.name).font(.headline).foregroundStyle(.primary).lineLimit(2)
                                Text("\(pack.stickers.count) stickers · \(pack.kind == .ANIMATED ? "Animated" : "Static")").font(.caption).foregroundStyle(.secondary)
                                if pack.rating == .MATURE { Label("Private mature pack", systemImage: "lock").font(.caption).foregroundStyle(.secondary) }
                            }.padding(14).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
                        }.buttonStyle(.plain)
                    }
                }
            }.padding()
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Your library")
        .searchable(text: $query, prompt: "Packs, captions or tags")
        .navigationDestination(for: String.self) { PackView(id: $0) }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { importer = true } label: { Image(systemName: "square.and.arrow.down") }.accessibilityLabel("Import sticker ZIP") } }
        .fileImporter(isPresented: $importer, allowedContentTypes: [.zip]) { result in switch result { case .success(let url): model.importArchive(url); case .failure(let error): model.error = error.localizedDescription } }
    }
}

struct PackView: View {
    let id: String
    @EnvironmentObject var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.creatorSheetPresentation) private var creatorSheetPresentation
    @State private var selected: SavedSticker?
    @State private var rename = false
    @State private var name = ""
    @State private var deleting = false
    @State private var creating = false
    private var pack: SavedPack? { model.packs.first { $0.id == id } }
    var body: some View {
        Group {
            if let pack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        HStack { Label(pack.kind == .ANIMATED ? "Animated" : "Static", systemImage: pack.kind == .ANIMATED ? "play.circle" : "photo"); Text("· \(pack.stickers.count)/30"); Spacer(); if pack.rating == .MATURE { Image(systemName: "lock.fill").accessibilityLabel("Private mature pack") } }.font(.subheadline).foregroundStyle(.secondary)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 105), spacing: 12)], spacing: 12) {
                            ForEach(pack.stickers) { item in
                                Button { selected = item } label: {
                                    VStack(spacing: 4) { StickerImage(url: model.mediaURL(pack, item)).frame(height: 112); Text(item.label.isEmpty ? "Sticker" : item.label).font(.caption).lineLimit(2).foregroundStyle(.primary) }.frame(maxWidth: .infinity).padding(8).background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 18)).overlay(alignment: .topTrailing) { if item.favorite { Image(systemName: "heart.fill").font(.caption).foregroundStyle(.pink).padding(8) } }
                                }.buttonStyle(.plain).accessibilityLabel(item.label.isEmpty ? "Preview sticker" : "Preview \(item.label)")
                            }
                            if pack.stickers.count < 30 { Button { creating = true } label: { Label("Add a sticker", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 112) }.buttonStyle(.bordered) }
                        }
                        if pack.stickers.count < 3 { Text("Add \(3 - pack.stickers.count) more stickers to use this pack in WhatsApp.").font(.callout).foregroundStyle(.secondary) }
                    }.padding()
                }
                .navigationTitle(pack.name).navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) { Button { model.addToWhatsApp(pack) } label: { Label("Add to WhatsApp", systemImage: "message.fill").frame(maxWidth: .infinity).padding(8) }.buttonStyle(.borderedProminent).disabled(model.busy != nil || pack.stickers.count < 3).padding().background(.regularMaterial) }
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Menu {
                    Button("Share ZIP", systemImage: "square.and.arrow.up") { model.share([pack]) }
                    Button("Rename", systemImage: "pencil") { name = pack.name; rename = true }
                    Button("Delete pack", systemImage: "trash", role: .destructive) { deleting = true }
                } label: { Image(systemName: "ellipsis.circle") }.accessibilityLabel("Pack options") } }
                .alert("Rename pack", isPresented: $rename) { TextField("Pack name", text: $name); Button("Save") { model.run("Renaming pack") { try await model.store.rename(id, name: name) } }; Button("Cancel", role: .cancel) {} }
                .confirmationDialog("Delete this pack from this device?", isPresented: $deleting, titleVisibility: .visible) { Button("Delete pack", role: .destructive) { model.run("Deleting pack") { try await model.store.remove(packID: id); dismiss() } } }
                .sheet(item: $selected) { item in StickerDetail(pack: pack, sticker: item) }
                .sheet(isPresented: $creating) { NavigationStack { CreatorView(destination: id) } }
                .onChange(of: creating) { _, value in creatorSheetPresentation.wrappedValue = value }
            } else { ContentUnavailableView("Pack unavailable", systemImage: "tray", description: Text("It may have been deleted. Your other packs are unchanged.")) }
        }
    }
}

private struct StickerDetail: View {
    let pack: SavedPack
    let sticker: SavedSticker
    @EnvironmentObject var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var deleting = false
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                StickerImage(url: model.mediaURL(pack, sticker), pixels: 512, animated: pack.kind == .ANIMATED).aspectRatio(1, contentMode: .fit)
                Text(sticker.label).font(.title3).multilineTextAlignment(.center)
                Button("Toggle favorite", systemImage: "heart") { model.run("Updating favorite") { try await model.store.toggleFavorite(packID: pack.id, stickerID: sticker.id); dismiss() } }
                Button("Delete sticker", systemImage: "trash", role: .destructive) { deleting = true }
            }.padding().navigationTitle("Sticker preview").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
                .confirmationDialog("Delete this sticker?", isPresented: $deleting) { Button("Delete", role: .destructive) { model.run("Removing sticker") { try await model.store.remove(packID: pack.id, stickerID: sticker.id); dismiss() } } }
        }
    }
}

private struct SettingsView: View {
    @EnvironmentObject var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var importer = false
    var body: some View {
        List {
            Section("Your library") {
                Button("Import sticker ZIP") { importer = true }
                Button("Export library backup") { model.share(model.packs) }.disabled(model.packs.isEmpty || model.busy != nil)
            }
            Section("Privacy") {
                Label("Local by default", systemImage: "lock.shield")
                Text("Your photos, clips and packs stay on this device. Discover uses the internet; saving and sharing always require your action.")
                Text("Adding to WhatsApp temporarily copies the chosen pack to this device’s clipboard for up to 60 seconds.")
            }
            Section("About") { NavigationLink("Open-source notices") { LicenseNoticesView() } }
        }
        .disabled(model.busy != nil)
        .navigationTitle("Settings").toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
        .safeAreaInset(edge: .bottom) { OperationProgressBar() }
        .operationFeedback()
        .sheet(isPresented: Binding(get: { model.shareURL != nil }, set: { if !$0 { model.shareURL = nil } })) {
            if let url = model.shareURL { ShareSheet(url: url) }
        }
        .fileImporter(isPresented: $importer, allowedContentTypes: [.zip]) { result in
            switch result { case .success(let url): model.importArchive(url); case .failure(let error): model.error = error.localizedDescription }
        }
    }
}

private struct LicenseNoticesView: View {
    private let names = ["SDWebImage", "SDWebImageWebPCoder", "ZIPFoundation", "libwebp", "libwebp-PATENTS"]
    var body: some View {
        List(names, id: \.self) { name in
            NavigationLink(name) {
                ScrollView { Text(text(name)).font(.footnote).textSelection(.enabled).padding() }.navigationTitle(name)
            }
        }.navigationTitle("Open-source notices")
    }
    private func text(_ name: String) -> String {
        guard let url = Bundle.main.url(forResource: name, withExtension: "txt"), let text = try? String(contentsOf: url, encoding: .utf8) else { return "Notice unavailable in this build. Contact StickerDock support." }
        return text
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    final class Coordinator { let url: URL; init(_ url: URL) { self.url = url } }
    func makeCoordinator() -> Coordinator { Coordinator(url) }
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [url], applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
    static func dismantleUIViewController(_ controller: UIActivityViewController, coordinator: Coordinator) {
        // Temporary archives contain private media; clean up after the share sheet closes.
        let directory = coordinator.url.deletingLastPathComponent()
        if directory.lastPathComponent.hasPrefix("share-") { try? FileManager.default.removeItem(at: directory) }
    }
}
