import SwiftUI
import FirebaseCore
import GoogleSignIn
import GoogleMaps

@main
struct iOSApp: App {
    init() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        // The key comes from Secrets.xcconfig (gitignored) through Info.plist.
        if let key = Bundle.main.object(forInfoDictionaryKey: "MAPS_API_KEY") as? String, !key.isEmpty {
            GMSServices.provideAPIKey(key)
        } else {
            assertionFailure("MAPS_API_KEY missing: add it to iosApp/Configuration/Secrets.xcconfig")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
