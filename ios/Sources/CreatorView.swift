import SwiftUI
import PhotosUI
import CoreTransferable
import UniformTypeIdentifiers

private func copyPickedMedia(_ original: URL) throws -> URL {
    let scoped = original.startAccessingSecurityScopedResource()
    defer { if scoped { original.stopAccessingSecurityScopedResource() } }
    let data = try MediaRules.read(original, limit: 20_000_000)
    let ext = original.pathExtension.filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("creator-" + UUID().uuidString).appendingPathExtension(ext.isEmpty ? "media" : ext)
    try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    return url
}

private struct PickedMediaFile: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .data) { received in
            PickedMediaFile(url: try copyPickedMedia(received.file))
        }
    }
}

struct CreatorView: View {
    var destination: String? = nil
    @EnvironmentObject var model: LibraryModel
    @State private var photo: PhotosPickerItem?
    @State private var importer = false
    @State private var source: URL?
    @State private var info: MediaInfo?
    @State private var recipe = CreationRecipe()
    @State private var variants = ""
    @State private var output: ImportedPack?
    @State private var packName = "My reactions"
    @State private var targetID = ""
    @State private var mature = false
    private var compatible: [SavedPack] { model.packs.filter { $0.kind == output?.kind && $0.stickers.count + (output?.stickers.count ?? 1) <= 30 } }

    var body: some View {
        Form {
            Section {
                Text("Make a reaction worth sending").font(.title2.bold())
                Text("Use your own photos, funny moments or short clips. Preview everything before it enters your library.").foregroundStyle(.secondary)
                PhotosPicker(selection: $photo, matching: .any(of: [.images, .videos])) { Label("Choose from Photos", systemImage: "photo.on.rectangle") }
                Button("Choose from Files", systemImage: "folder") { importer = true }
            }
            if let info {
                Section("Make it yours") {
                    TextField("Caption", text: $recipe.caption).accessibilityIdentifier("creator-caption")
                    TextField("More captions, separated by |", text: $variants, axis: .vertical)
                    Text("Add up to five alternatives to make a small reaction pack.").font(.caption).foregroundStyle(.secondary)
                    Toggle("Fill the frame", isOn: $recipe.fill)
                    if recipe.fill {
                        VStack(alignment: .leading) { Text("Horizontal crop"); Slider(value: $recipe.focusX, in: 0...1).accessibilityLabel("Horizontal crop") }
                        VStack(alignment: .leading) { Text("Vertical crop"); Slider(value: $recipe.focusY, in: 0...1).accessibilityLabel("Vertical crop") }
                    }
                    if info.animated {
                        VStack(alignment: .leading) {
                            Text("Start: \(recipe.start, specifier: "%.1f") seconds")
                            Slider(value: $recipe.start, in: 0...max(0.01, info.duration - recipe.duration)).accessibilityLabel("Clip start")
                            Text("Length: \(recipe.duration, specifier: "%.1f") seconds")
                            Slider(value: $recipe.duration, in: 0.5...max(0.51, min(4, info.duration)), step: 0.1).accessibilityLabel("Clip length")
                        }
                        Picker("Motion rate", selection: $recipe.fps) { ForEach([6, 8, 10], id: \.self) { Text("\($0) fps").tag($0) } }
                    } else { Toggle("Remove background on device", isOn: $recipe.removeBackground) }
                    Button("Generate preview", systemImage: "sparkles", action: generate).accessibilityIdentifier("generate-preview")
                }
            }
            if let output {
                Section("Review your stickers") {
                    ScrollView(.horizontal) {
                        HStack(spacing: 14) {
                            ForEach(Array(output.stickers.enumerated()), id: \.offset) { index, item in
                                VStack { StickerImage(data: item.data, pixels: 512, animated: output.kind == .ANIMATED && index == 0).frame(width: 220, height: 220); Text(item.label).font(.caption).lineLimit(2) }.frame(width: 220)
                            }
                        }
                    }
                    Text("\(output.stickers.count) \(output.kind == .ANIMATED ? "animated" : "static") stickers · \(output.stickers.reduce(0) { $0 + $1.data.count } / 1000) KB").font(.caption).foregroundStyle(.secondary)
                }
                Section("Save when you’re happy") {
                    Picker("Destination", selection: $targetID) {
                        Text("New pack").tag("")
                        ForEach(compatible) { Text($0.name).tag($0.id) }
                    }
                    if targetID.isEmpty {
                        TextField("Pack name", text: $packName)
                        Toggle("Private mature pack", isOn: $mature)
                    }
                    Button("Save to library", systemImage: "tray.and.arrow.down", action: save).accessibilityIdentifier("save-to-library")
                }
            }
        }
        .disabled(model.busy != nil)
        .navigationTitle("Create")
        .fileImporter(isPresented: $importer, allowedContentTypes: [.image, .movie]) { result in
            switch result {
            case .success(let url): model.run("Opening your media") {
                let copied = try copyPickedMedia(url)
                do { try await select(copied) } catch { try? FileManager.default.removeItem(at: copied); throw error }
            }
            case .failure(let error): model.error = error.localizedDescription
            }
        }
        .onChange(of: photo) { _, selected in
            guard let selected else { return }
            model.run("Opening your photo or clip") {
                guard let file = try await selected.loadTransferable(type: PickedMediaFile.self) else { throw StickerError.invalid("Could not read this item. Try choosing it from Files.") }
                do { try await select(file.url) } catch { try? FileManager.default.removeItem(at: file.url); throw error }
            }
        }
        .onChange(of: recipe) { _, _ in output = nil }
        .onChange(of: variants) { _, _ in output = nil }
    }

    private func select(_ url: URL) async throws {
        let inspected = try await model.creator.inspect(url)
        if let old = source { try? FileManager.default.removeItem(at: old) }
        source = url; info = inspected; output = nil
        recipe.start = 0; recipe.duration = min(2, inspected.duration > 0 ? inspected.duration : 2)
        recipe.removeBackground = false
    }

    private func generate() {
        guard let source else { return }
        output = nil
        let chosen = recipe
        var captions = [chosen.caption]
        for caption in variants.split(separator: "|").map({ $0.trimmingCharacters(in: .whitespacesAndNewlines) }) where !caption.isEmpty && !captions.contains(caption) { captions.append(caption) }
        model.run("Creating preview") {
            try require(captions.count <= 6, "Use at most five alternative captions.")
            var combined: ImportedPack?
            for (index, caption) in captions.enumerated() {
                var current = chosen; current.caption = caption
                let result = try await model.creator.create(source, recipe: current) { text in
                    Task { @MainActor in if model.busy != nil { model.busy = "\(index + 1)/\(captions.count) · \(text)" } }
                }
                if combined == nil { combined = result } else { combined?.stickers.append(contentsOf: result.stickers) }
            }
            try Task.checkCancellation()
            output = combined
            targetID = destination.flatMap { id in model.packs.first { $0.id == id && $0.kind == combined?.kind && $0.stickers.count + (combined?.stickers.count ?? 0) <= 30 }?.id } ?? ""
        }
    }

    private func save() {
        guard var output else { return }
        output.name = packName; output.rating = mature ? .MATURE : .GENERAL
        let target = targetID
        model.run("Saving your reactions") {
            if target.isEmpty { _ = try await model.store.importPacks([output]) }
            else { try await model.store.appendBatch(output.stickers, to: target, kind: output.kind) }
            self.output = nil
            model.notice = "Saved to your library. Add the pack to WhatsApp when you choose."
        }
    }
}
