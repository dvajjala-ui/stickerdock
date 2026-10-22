import Foundation
import Security
import SwiftUI

private final class NoCaptionRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

struct PersonalCaptionJob {
    let id: String
    let status: String
    let caption: String?
    let errorCode: String?
}

/// The phone owns its library. Only a selected, caption-free preview is sent
/// after explicit consent; the service returns text, never a saved sticker.
@MainActor final class PersonalCaptionClient: ObservableObject {
    @Published private(set) var email = ""
    @Published private(set) var ownerID: String?
    @Published private(set) var available = false

    private struct Session: Codable {
        let accessToken: String
        let refreshToken: String
        let email: String
        let ownerID: String
        let expiresAt: Date
    }

    private let service = "app.stickerdock.duo.personal-caption"
    private let account = "supabase-session"
    private let base = URL(string: "https://stickerdock.vercel.app")!
    private let network = URLSession(configuration: .ephemeral, delegate: NoCaptionRedirects(), delegateQueue: nil)
    private var session: Session?
    private var refreshTask: Task<String, Error>?
    private var generation = UUID()

    init() {
        if let data = readSession(), let restored = try? JSONDecoder().decode(Session.self, from: data) {
            session = restored
            email = restored.email
            ownerID = restored.ownerID
        }
    }

    private func readSession() -> Data? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        return SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess ? result as? Data : nil
    }

    private func persist(_ value: Session) throws {
        let data = try JSONEncoder().encode(value)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        let attributes: [String: Any] = [kSecValueData as String: data,
                                          kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let update = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if update == errSecItemNotFound {
            let status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
            guard status == errSecSuccess else { throw StickerError.invalid("Could not secure your account session on this iPhone.") }
        } else if update != errSecSuccess {
            throw StickerError.invalid("Could not secure your account session on this iPhone.")
        }
    }

    func signOut() {
        generation = UUID()
        refreshTask?.cancel(); refreshTask = nil
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        session = nil; ownerID = nil; email = ""; available = false
    }

    private func request(_ path: String, _ payload: [String: Any], bearer: String? = nil) async throws -> [String: Any] {
        guard let url = URL(string: path, relativeTo: base),
              JSONSerialization.isValidJSONObject(payload) else { throw StickerError.invalid("Invalid account request.") }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 90
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        if let bearer { request.setValue("Bearer " + bearer, forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await network.data(for: request)
        guard let response = response as? HTTPURLResponse, data.count <= 4_000_000,
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw StickerError.invalid("StickerDock did not return a valid account response.")
        }
        guard (200...299).contains(response.statusCode) else {
            let message = object["error"] as? String ?? "StickerDock is unavailable. Try again later."
            throw StickerError.invalid(String(message.prefix(200)))
        }
        return object
    }

    private func saveResponse(_ response: [String: Any], expectedGeneration: UUID) throws {
        guard generation == expectedGeneration,
              let access = response["access_token"] as? String, !access.isEmpty,
              let refresh = response["refresh_token"] as? String, !refresh.isEmpty,
              let user = response["user"] as? [String: Any],
              let id = user["id"] as? String, UUID(uuidString: id) != nil,
              let mail = user["email"] as? String,
              let expiry = response["expires_in"] as? NSNumber,
              expiry.doubleValue > 0 else { throw StickerError.invalid("Sign in again to continue.") }
        let updated = Session(accessToken: access, refreshToken: refresh, email: mail,
                              ownerID: id.lowercased(), expiresAt: Date().addingTimeInterval(expiry.doubleValue))
        try persist(updated)
        session = updated; ownerID = updated.ownerID; email = mail
    }

    func signIn(email: String, password: String, create: Bool) async throws -> String {
        let expected = generation
        let result = try await request("/api/cloud", ["action": create ? "signup" : "login",
                                                       "email": email.trimmingCharacters(in: .whitespacesAndNewlines),
                                                       "password": password])
        guard generation == expected else { throw CancellationError() }
        if result["access_token"] != nil {
            try saveResponse(result, expectedGeneration: expected)
            return "Signed in. Your local stickers remain on this iPhone."
        }
        return result["message"] as? String ?? "Confirm your email, then sign in."
    }

    private func bearer() async throws -> String {
        guard let current = session else { throw StickerError.invalid("Sign in first.") }
        if current.expiresAt.timeIntervalSinceNow > 120 { return current.accessToken }
        if let task = refreshTask { return try await task.value }
        let expected = generation
        let task = Task<String, Error> {
            let result = try await request("/api/cloud", ["action": "refresh", "refresh_token": current.refreshToken])
            try saveResponse(result, expectedGeneration: expected)
            return session!.accessToken
        }
        refreshTask = task
        defer { refreshTask = nil }
        return try await task.value
    }

    func checkAvailability() async {
        guard session != nil else { available = false; return }
        do {
            let token = try await bearer()
            let result = try await request("/api/personal_ai", ["action": "availability"], bearer: token)
            available = result["available"] as? Bool == true
        } catch { available = false }
    }

    func submit(image: Data, prompt: String, id: UUID) async throws -> PersonalCaptionJob {
        guard image.count <= 2_800_000 else { throw StickerError.invalid("Choose a smaller sticker preview.") }
        let payload: [String: Any] = ["action": "submit", "request_id": id.uuidString.lowercased(),
                                      "image": "data:image/webp;base64," + image.base64EncodedString(),
                                      "prompt": prompt, "consent": true, "audience": "GENERAL",
                                      "rights": "OWNED_OR_LICENSED"]
        let token = try await bearer()
        let result = try await request("/api/personal_ai", payload, bearer: token)
        return try Self.job(result)
    }

    func status(_ id: UUID) async throws -> PersonalCaptionJob {
        let token = try await bearer()
        let result = try await request("/api/personal_ai", ["action": "status", "id": id.uuidString.lowercased()], bearer: token)
        guard let job = result["job"] as? [String: Any] else { throw StickerError.invalid("AI status was unavailable.") }
        return try Self.job(job)
    }

    private static func job(_ value: [String: Any]) throws -> PersonalCaptionJob {
        guard let id = value["id"] as? String, UUID(uuidString: id) != nil,
              let status = value["status"] as? String,
              ["uploading", "queued", "running", "complete", "failed", "cancelled"].contains(status) else {
            throw StickerError.invalid("Invalid AI job response.")
        }
        return PersonalCaptionJob(id: id, status: status, caption: value["caption"] as? String,
                                  errorCode: value["error_code"] as? String)
    }

    func pendingID(for key: String) -> UUID? {
        guard let ownerID else { return nil }
        return UserDefaults.standard.string(forKey: "personal-ai:\(ownerID):\(key)").flatMap(UUID.init(uuidString:))
    }

    func savePending(_ id: UUID?, for key: String) {
        guard let ownerID else { return }
        let preference = "personal-ai:\(ownerID):\(key)"
        if let id { UserDefaults.standard.set(id.uuidString.lowercased(), forKey: preference) }
        else { UserDefaults.standard.removeObject(forKey: preference) }
    }
}
