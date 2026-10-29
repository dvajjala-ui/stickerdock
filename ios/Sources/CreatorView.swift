import SwiftUI
import PhotosUI
import CoreTransferable
import UniformTypeIdentifiers
import CryptoKit

/// A render keeps its lease until the worker returns, even if its sheet closes.
/// Clearing UI state can therefore release private media without deleting a
/// source that AVFoundation is still using.
private final class CreatorMediaFile: @unchecked Sendable {
    let url: URL
    init(url: URL) { self.url = url }
    deinit { try? FileManager.default.removeItem(at: url) }

    private static let recoverOrphans: Void = {
        let directory = FileManager.default.temporaryDirectory
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isRegularFileKey])) ?? []
        for file in files where file.lastPathComponent.range(of: "^creator-[0-9A-Fa-f-]{36}\\.[A-Za-z0-9]+$", options: .regularExpression) != nil {
            if (try? file.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true { try? FileManager.default.removeItem(at: file) }
        }
    }()

    static func prepare() { _ = recoverOrphans }
}

private func copyPickedMedia(_ original: URL) throws -> CreatorMediaFile {
    CreatorMediaFile.prepare()
    let scoped = original.startAccessingSecurityScopedResource()
    defer { if scoped { original.stopAccessingSecurityScopedResource() } }
    let data = try MediaRules.read(original, limit: 20_000_000)
    let ext = original.pathExtension.filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("creator-" + UUID().uuidString).appendingPathExtension(ext.isEmpty ? "media" : ext)
    try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    return CreatorMediaFile(url: url)
}

private struct PickedMediaFile: Transferable {
    let file: CreatorMediaFile
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .data) { received in
            PickedMediaFile(file: try copyPickedMedia(received.file))
        }
    }
}

struct CreatorView: View {
    var destination: String? = nil
    @EnvironmentObject var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var photo: PhotosPickerItem?
    @State private var photosPresented = false
    @State private var importer = false
    @State private var source: CreatorMediaFile?
    @State private var sessionID = UUID()
    @State private var ownsOperation = false
    @State private var info: MediaInfo?
    @State private var recipe = CreationRecipe()
    @State private var variants = ""
    @State private var output: ImportedPack?
    @State private var packName = "My reactions"
    @State private var targetID = ""
    @State private var mature = false
    @StateObject private var ai = PersonalCaptionClient()
    @State private var aiAccountPresented = false
    @State private var aiConsentPresented = false
    @State private var aiRightsConfirmed = false
    @State private var aiOnlineConfirmed = false
    @State private var aiPrompt = "Make one short, funny reaction caption for this image"
    @State private var aiJob: PersonalCaptionJob?
    @State private var aiKey = ""
    @State private var aiBusy = false
    @State private var aiTask: Task<Void, Never>?
    @State private var aiOperationID = UUID()
    private var compatible: [SavedPack] { model.packs.filter { $0.kind == output?.kind && $0.stickers.count + (output?.stickers.count ?? 1) <= 30 } }
    private var aiAllowed: Bool {
        info?.animated == false && !mature && (targetID.isEmpty || model.packs.first { $0.id == targetID }?.rating == .GENERAL)
    }

    var body: some View {
        Group {
            if info == nil {
                CreatorStartView(onPhotos: { photosPresented = true }, onFiles: { importer = true })
            } else {
              Form {
            Section {
                Button("Choose different media", systemImage: "photo.on.rectangle") { photosPresented = true }
                Button("Choose from Files", systemImage: "folder") { importer = true }
            } footer: {
                Text("Your current edit stays here if you cancel the picker.")
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
                            if info.duration > recipe.duration {
                                Slider(value: $recipe.start, in: 0...(info.duration - recipe.duration)).accessibilityLabel("Clip start")
                            }
                            Text("Length: \(recipe.duration, specifier: "%.1f") seconds")
                            if info.duration > 0.5 {
                                Slider(value: $recipe.duration, in: 0.5...min(4, info.duration), step: 0.1).accessibilityLabel("Clip length")
                            }
                        }
                        Picker("Motion rate", selection: $recipe.fps) { ForEach([6, 8, 10], id: \.self) { Text("\($0) fps").tag($0) } }
                    } else { Toggle("Remove background on device", isOn: $recipe.removeBackground) }
                    Button("Generate preview", systemImage: "sparkles", action: generate).accessibilityIdentifier("generate-preview")
                }
                if !info.animated {
                    Section("AI caption suggestion") {
                        if !aiAllowed {
                            Text("Private mature packs stay on this iPhone. AI suggestions are for general-audience photos only.")
                                .font(.caption).foregroundStyle(.secondary)
                        } else if ai.ownerID == nil {
                            Text("Sign in to ask for a caption. Your local library and original photo stay on this iPhone.")
                                .font(.caption).foregroundStyle(.secondary)
                            Button("Sign in for AI captions", systemImage: "person.crop.circle") { aiAccountPresented = true }
                        } else if !ai.available {
                            Text("AI captions are not enabled for this account yet. Local creation still works.")
                                .font(.caption).foregroundStyle(.secondary)
                            Button("Check availability") { Task { await ai.checkAvailability() } }
                            Button("Account") { aiAccountPresented = true }
                        } else {
                            TextField("What kind of reaction?", text: $aiPrompt, axis: .vertical)
                                .accessibilityIdentifier("ai-caption-prompt")
                            Text("Only a caption-free sticker preview is sent after you confirm. Use an image you own or may license for this purpose.")
                                .font(.caption).foregroundStyle(.secondary)
                            Button("Suggest a caption", systemImage: "text.bubble") {
                                aiRightsConfirmed = false; aiOnlineConfirmed = false; aiConsentPresented = true
                            }
                                .disabled(aiBusy || aiPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || aiPrompt.count > 200)
                            if aiBusy { ProgressView("Preparing your private request…") }
                            if let aiJob {
                                Text(aiJob.status == "complete" ? "Suggestion ready" : "Request: \(aiJob.status)")
                                    .font(.caption).foregroundStyle(.secondary)
                                if let caption = aiJob.caption, aiJob.status == "complete" {
                                    Text(caption).font(.headline)
                                    Button("Use as caption") { recipe.caption = caption; output = nil }
                                        .accessibilityIdentifier("apply-ai-caption")
                                } else if ["uploading", "queued", "running"].contains(aiJob.status) {
                                    Button("Check suggestion") { checkAISuggestion() }.disabled(aiBusy)
                                    Button("Cancel request", role: .destructive) { cancelAISuggestion() }.disabled(aiBusy)
                                } else if aiJob.status == "failed" {
                                    Text("This request could not finish. You can make a local sticker or try a new suggestion.")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
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
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: info != nil)
        .disabled(model.busy != nil)
        .navigationTitle("Create")
        .toolbar {
            if destination != nil {
                ToolbarItem(placement: .topBarTrailing) { Button("Close") { endSession(); dismiss() }.accessibilityIdentifier("close-creator") }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if destination != nil, model.busy != nil { OperationProgressBar() }
        }
        .operationFeedback(active: destination != nil)
        .task { await ai.checkAvailability() }
        .sheet(isPresented: $aiAccountPresented) { NavigationStack { PersonalCaptionAccountView(client: ai) } }
        .sheet(isPresented: $aiConsentPresented) {
            NavigationStack {
                Form {
                    Section("Your image") {
                        Toggle("I own or have permission to use this image", isOn: $aiRightsConfirmed)
                        Text("Only general-audience photos can be sent. Mature packs and videos stay on this iPhone.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Section("Online processing") {
                        Toggle("I agree to send one caption-free preview to StickerDock and Groq", isOn: $aiOnlineConfirmed)
                        Text("The suggestion returns as text. It is not put in your library until you apply it, preview the sticker and save.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .navigationTitle("AI caption consent")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { aiConsentPresented = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Send preview") { aiConsentPresented = false; requestAISuggestion() }
                            .disabled(!aiRightsConfirmed || !aiOnlineConfirmed)
                    }
                }
            }
        }
        .photosPicker(isPresented: $photosPresented, selection: $photo, matching: .any(of: [.images, .videos]))
        .fileImporter(isPresented: $importer, allowedContentTypes: [.image, .movie]) { result in
            switch result {
            case .success(let url): run("Opening your media") {
                let session = sessionID
                let copied = try copyPickedMedia(url)
                try await select(copied, session: session)
            }
            case .failure(let error): model.error = error.localizedDescription
            }
        }
        .onChange(of: photo) { _, selected in
            guard let selected else { return }
            run("Opening your photo or clip") {
                let session = sessionID
                defer { photo = nil }
                guard let file = try await selected.loadTransferable(type: PickedMediaFile.self) else { throw StickerError.invalid("Could not read this item. Try choosing it from Files.") }
                try await select(file.file, session: session)
            }
        }
        .onChange(of: recipe) { _, _ in output = nil; invalidateAISuggestion() }
        .onChange(of: recipe.duration) { _, duration in
            if let info { recipe.start = min(recipe.start, max(0, info.duration - duration)) }
        }
        .onChange(of: variants) { _, _ in output = nil }
        .onChange(of: aiPrompt) { _, _ in invalidateAISuggestion() }
        .onChange(of: ai.ownerID) { _, _ in invalidateAISuggestion() }
        .onDisappear {
            // Keep the main Create tab's draft when switching tabs. A dismissed
            // pack creator releases its media; system pickers are not dismissal.
            if destination != nil && !photosPresented && !importer { endSession() }
        }
    }

    private func select(_ file: CreatorMediaFile, session: UUID) async throws {
        let inspected = try await model.creator.inspect(file.url)
        try Task.checkCancellation()
        guard sessionID == session else { throw CancellationError() }
        source = file; info = inspected; output = nil
        invalidateAISuggestion()
        recipe.start = 0; recipe.duration = min(2, inspected.duration > 0 ? inspected.duration : 2)
        recipe.removeBackground = false
    }

    private func generate() {
        guard let source else { return }
        output = nil
        let chosen = recipe
        var captions = [chosen.caption]
        for caption in variants.split(separator: "|").map({ $0.trimmingCharacters(in: .whitespacesAndNewlines) }) where !caption.isEmpty && !captions.contains(caption) { captions.append(caption) }
        run("Creating preview") {
            let session = sessionID
            try require(captions.count <= 6, "Use at most five alternative captions.")
            var combined: ImportedPack?
            for (index, caption) in captions.enumerated() {
                var current = chosen; current.caption = caption
                let result = try await model.creator.create(source.url, recipe: current) { text in
                    Task { @MainActor in if model.busy != nil { model.busy = "\(index + 1)/\(captions.count) · \(text)" } }
                }
                if combined == nil { combined = result } else { combined?.stickers.append(contentsOf: result.stickers) }
            }
            try Task.checkCancellation()
            guard sessionID == session else { throw CancellationError() }
            output = combined
            targetID = destination.flatMap { id in model.packs.first { $0.id == id && $0.kind == combined?.kind && $0.stickers.count + (combined?.stickers.count ?? 0) <= 30 }?.id } ?? ""
        }
    }

    private func save() {
        guard var output else { return }
        output.name = packName; output.rating = mature ? .MATURE : .GENERAL
        let target = targetID
        run("Saving your reactions") {
            if target.isEmpty { _ = try await model.store.importPacks([output]) }
            else { try await model.store.appendBatch(output.stickers, to: target, kind: output.kind) }
            self.output = nil
            model.notice = "Saved to your library. Add the pack to WhatsApp when you choose."
            if destination != nil { ownsOperation = false; dismiss() }
        }
    }

    private func requestAISuggestion() {
        guard aiAllowed, ai.available, let source, let owner = ai.ownerID, !aiBusy else { return }
        let prompt = aiPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty && prompt.count <= 200 else { return }
        let selectedSession = sessionID
        let operationID = UUID(); aiOperationID = operationID
        var previewRecipe = recipe
        previewRecipe.caption = ""
        aiBusy = true
        aiTask = Task {
            defer { if aiOperationID == operationID { aiBusy = false; aiTask = nil } }
            do {
                let preview = try await model.creator.create(source.url, recipe: previewRecipe) { _ in }
                try Task.checkCancellation()
                guard sessionID == selectedSession, ai.ownerID == owner,
                      let image = preview.stickers.first?.data else { throw CancellationError() }
                var keyData = image
                keyData.append(contentsOf: prompt.utf8)
                let key = SHA256.hash(data: keyData).map { String(format: "%02x", $0) }.joined()
                let id = ai.pendingID(for: key) ?? UUID()
                ai.savePending(id, for: key)
                let submitted = try await ai.submit(image: image, prompt: prompt, id: id)
                try Task.checkCancellation()
                guard sessionID == selectedSession, ai.ownerID == owner else { throw CancellationError() }
                aiKey = key
                if submitted.status == "complete" { aiJob = try await ai.status(id) }
                else { aiJob = submitted }
                if let status = aiJob?.status, ["failed", "cancelled"].contains(status) { ai.savePending(nil, for: key) }
            } catch is CancellationError {
                // The local draft remains available.
            } catch { if aiOperationID == operationID { model.error = error.localizedDescription } }
        }
    }

    private func checkAISuggestion() {
        guard let id = aiJob.flatMap({ UUID(uuidString: $0.id) }), let owner = ai.ownerID, !aiBusy else { return }
        let selectedSession = sessionID
        let operationID = UUID(); aiOperationID = operationID
        aiBusy = true
        aiTask = Task {
            defer { if aiOperationID == operationID { aiBusy = false; aiTask = nil } }
            do {
                let result = try await ai.status(id)
                try Task.checkCancellation()
                guard sessionID == selectedSession, ai.ownerID == owner else { throw CancellationError() }
                aiJob = result
                if ["failed", "cancelled"].contains(result.status) { ai.savePending(nil, for: aiKey) }
            } catch is CancellationError {} catch { if aiOperationID == operationID { model.error = error.localizedDescription } }
        }
    }

    private func cancelAISuggestion() {
        guard let id = aiJob.flatMap({ UUID(uuidString: $0.id) }), let owner = ai.ownerID, !aiBusy else { return }
        let selectedSession = sessionID
        let operationID = UUID(); aiOperationID = operationID
        aiBusy = true
        aiTask = Task {
            defer { if aiOperationID == operationID { aiBusy = false; aiTask = nil } }
            do {
                let status = try await ai.cancel(id)
                try Task.checkCancellation()
                guard sessionID == selectedSession, ai.ownerID == owner else { throw CancellationError() }
                if status == "cancelled" {
                    ai.savePending(nil, for: aiKey)
                    aiJob = PersonalCaptionJob(id: id.uuidString.lowercased(), status: status, caption: nil, errorCode: nil)
                } else { aiJob = try await ai.status(id) }
            } catch is CancellationError {} catch { if aiOperationID == operationID { model.error = error.localizedDescription } }
        }
    }

    private func run(_ label: String, action: @escaping @MainActor () async throws -> Void) {
        guard model.busy == nil else { return }
        ownsOperation = true
        model.run(label) {
            defer { ownsOperation = false }
            try await action()
        }
    }

    private func invalidateAISuggestion() {
        aiTask?.cancel(); aiTask = nil; aiOperationID = UUID()
        aiJob = nil; aiKey = ""; aiBusy = false
    }

    private func endSession() {
        if ownsOperation { model.cancel() }
        invalidateAISuggestion()
        sessionID = UUID()
        source = nil; info = nil; output = nil; photo = nil
    }
}

private struct CreatorStartView: View {
    let onPhotos: () -> Void
    let onFiles: () -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 12) {
                    Label("YOUR NEXT REACTION", systemImage: "sparkle")
                        .font(.caption.weight(.bold)).tracking(1)
                    Text("Make it yours.").font(.largeTitle.bold())
                    Text("Turn a photo, GIF or short video into a sticker pack you can actually use in chat.")
                        .font(.body)
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Pick", systemImage: "photo")
                        Label("Edit", systemImage: "slider.horizontal.3")
                        Label("Send", systemImage: "message")
                    }.font(.caption.weight(.semibold)).padding(.top, 4)
                }
                .foregroundStyle(Color(red: 0.08, green: 0.09, blue: 0.06))
                .frame(maxWidth: .infinity, alignment: .leading).padding(24)
                .background(Color(red: 0.85, green: 1, blue: 0.42), in: RoundedRectangle(cornerRadius: 26))

                VStack(alignment: .leading, spacing: 12) {
                    Text("Start with your media").font(.title3.bold())
                    Button(action: onPhotos) {
                        Label("Choose from Photos", systemImage: "photo.on.rectangle.angled")
                            .frame(maxWidth: .infinity, alignment: .leading).padding(8)
                    }.buttonStyle(.borderedProminent)
                    Button(action: onFiles) {
                        Label("Choose from Files", systemImage: "folder")
                            .frame(maxWidth: .infinity, alignment: .leading).padding(8)
                    }.buttonStyle(.bordered)
                }
                Label("Preview first. Save when you're happy.", systemImage: "lock.shield")
                    .font(.footnote).foregroundStyle(.secondary)
            }.padding(20).frame(maxWidth: 700)
        }.frame(maxWidth: .infinity)
            .background(Color(uiColor: .systemGroupedBackground))
    }
}

private struct PersonalCaptionAccountView: View {
    @ObservedObject var client: PersonalCaptionClient
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var message = ""

    var body: some View {
        Form {
            if client.ownerID == nil {
                Section("StickerDock account") {
                    TextField("Email", text: $email).textContentType(.emailAddress).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField("Password · at least 10 characters", text: $password).textContentType(.password)
                    Button("Sign in") { authenticate(create: false) }.disabled(busy || email.isEmpty || password.count < 10)
                    Button("Create account") { authenticate(create: true) }.disabled(busy || email.isEmpty || password.count < 10)
                    Text("Confirm a new account by email, then sign in. Cloud backup is optional and off by default.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            } else {
                Section("Signed in") {
                    Text(client.email)
                    Button("Sign out", role: .destructive) { client.signOut(); dismiss() }
                }
            }
            if busy { ProgressView() }
            if !message.isEmpty { Text(message).font(.caption) }
        }
        .navigationTitle("AI account")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
    }

    private func authenticate(create: Bool) {
        guard !busy else { return }
        let entered = password; password = ""; busy = true
        Task {
            defer { busy = false }
            do {
                message = try await client.signIn(email: email, password: entered, create: create)
                await client.checkAvailability()
            } catch { message = error.localizedDescription }
        }
    }
}
