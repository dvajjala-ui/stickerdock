import SwiftUI
import SDWebImage
import SDWebImageWebPCoder

private enum Thumbnails {
    static let cache: NSCache<NSString, UIImage> = {
        let result = NSCache<NSString, UIImage>()
        result.totalCostLimit = 8 * 1024 * 1024
        result.countLimit = 64
        return result
    }()

    static func decode(_ data: Data, pixels: Int, key: String?) -> UIImage? {
        if let key, let cached = cache.object(forKey: key as NSString) { return cached }
        let image = SDImageWebPCoder.shared.decodedImage(with: data, options: [.decodeFirstFrameOnly: true, .decodeThumbnailPixelSize: CGSize(width: CGFloat(pixels), height: CGFloat(pixels))])
        if let key, let image { cache.setObject(image, forKey: key as NSString, cost: pixels * pixels * 4) }
        return image
    }
}

struct StickerImage: View {
    var url: URL? = nil
    var data: Data? = nil
    var pixels: Int = 144
    var animated = false
    @State private var image: UIImage?
    @State private var movingData: Data?
    @State private var visible = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if animated, let movingData {
                MovingImage(data: movingData, playing: visible && scenePhase == .active)
            } else if let image {
                Image(uiImage: image).resizable().scaledToFit()
            } else {
                RoundedRectangle(cornerRadius: 16).fill(.quaternary).overlay { Image(systemName: "photo").foregroundStyle(.secondary) }
            }
        }
        .task(id: (url?.path ?? MediaRules.hash(data ?? Data())) + "-\(pixels)-\(animated)") {
            let input = data; let file = url; let size = pixels; let motion = animated
            let result = await Task.detached(priority: .userInitiated) { () -> (UIImage?, Data?) in
                guard let bytes = input ?? (try? file.map { try MediaRules.read($0, limit: 500_000) }) else { return (nil, nil) }
                if motion { return (nil, bytes) }
                return (Thumbnails.decode(bytes, pixels: size, key: file.map { $0.path + "-\(size)" }), nil)
            }.value
            if !Task.isCancelled { image = result.0; movingData = result.1 }
        }
        .onAppear { visible = true }
        .onDisappear { visible = false; movingData = nil }
    }
}

private struct MovingImage: UIViewRepresentable {
    let data: Data
    let playing: Bool
    func makeUIView(context: Context) -> SDAnimatedImageView {
        let view = SDAnimatedImageView()
        view.contentMode = .scaleAspectFit
        view.autoPlayAnimatedImage = false
        view.maxBufferSize = 8 * 1024 * 1024
        view.image = SDAnimatedImage(data: data)
        return view
    }
    func updateUIView(_ view: SDAnimatedImageView, context: Context) {
        if playing { view.startAnimating() } else { view.stopAnimating() }
    }
    static func dismantleUIView(_ view: SDAnimatedImageView, coordinator: ()) { view.stopAnimating(); view.image = nil }
}
