import SwiftUI

@main
struct FreeBrowserApp: App {
    @StateObject private var store = BrowserStore()
    var body: some Scene {
        WindowGroup { ContentView().environmentObject(store) }
    }
}
