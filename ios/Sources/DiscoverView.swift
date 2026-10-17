import SwiftUI

struct DiscoverView: View {
    @EnvironmentObject var model: LibraryModel
    @State private var query = ""
    @State private var selected: CatalogPack?
    @State private var loading = false
    @State private var failure: String?
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                Text("Find your next reaction").font(.title2.bold())
                Text("Preview a pack, then save only what you want.").foregroundStyle(.secondary)
                if loading { ProgressView("Finding packs") }
                if let failure { ContentUnavailableView { Label("Couldn’t load packs", systemImage: "wifi.exclamationmark") } description: { Text(failure) } actions: { Button("Retry") { Task { await refresh() } } } }
                if !loading && failure == nil && model.catalog.isEmpty { ContentUnavailableView("More reactions soon", systemImage: "sparkles", description: Text("You can create from your own photos and clips while new packs are reviewed.")) }
                ForEach(model.catalog.filter { query.isEmpty || $0.name.localizedStandardContains(query) || $0.creator.localizedStandardContains(query) }) { pack in
                    Button { selected = pack } label: { CatalogCard(pack: pack) }.buttonStyle(.plain)
                }
            }.padding()
        }
        .navigationTitle("Discover").searchable(text: $query, prompt: "Search reactions")
        .task { await refresh() }.refreshable { await refresh() }
        .sheet(item: $selected) { pack in NavigationStack { CatalogDetail(pack: pack) } }
    }
    private func refresh() async {
        loading = true; failure = nil
        defer { loading = false }
        do { model.catalog = try await model.catalogClient.browse() }
        catch is CancellationError { }
        catch { failure = error.localizedDescription }
    }
}

private struct CatalogCard: View {
    let pack: CatalogPack
    @EnvironmentObject var model: LibraryModel
    @State private var preview: Data?
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let preview { StickerImage(data: preview, pixels: 576).frame(height: 190) }
            Text(pack.name).font(.headline)
            Text(pack.creator).font(.caption).foregroundStyle(.secondary)
            Label("Preview pack", systemImage: "arrow.up.right").font(.subheadline)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(16).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
            .task(id: pack.sha256) { preview = try? await model.catalogClient.preview(pack) }
    }
}

private struct CatalogDetail: View {
    let pack: CatalogPack
    @EnvironmentObject var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var contents: [ImportedPack]?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(pack.creator).font(.subheadline).foregroundStyle(.secondary)
                if let contents {
                    ForEach(Array(contents.enumerated()), id: \.offset) { _, group in
                        Text(group.name).font(.headline)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))]) {
                            ForEach(Array(group.stickers.enumerated()), id: \.offset) { _, item in StickerImage(data: item.data).frame(height: 110) }
                        }
                    }
                    Button("Save to library", systemImage: "tray.and.arrow.down") {
                        model.run("Saving pack") {
                            _ = try await model.store.importPacks(contents, sourceKey: pack.id + ":" + pack.sha256)
                            model.notice = "Saved. Add to WhatsApp from your library when you choose."
                            dismiss()
                        }
                    }.buttonStyle(.borderedProminent).disabled(model.busy != nil)
                } else {
                    Text("Download this pack for a full preview. It won’t enter your library until you tap Save.").foregroundStyle(.secondary)
                    Button("Load full preview", systemImage: "eye") { model.run("Loading pack preview") { contents = try await model.catalogClient.download(pack) } }.buttonStyle(.borderedProminent).disabled(model.busy != nil)
                }
            }.padding()
        }.navigationTitle(pack.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
    }
}
