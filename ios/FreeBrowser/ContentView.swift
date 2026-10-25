import SwiftUI
import WebKit

struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ v: WKWebView, context: Context) {}
}

struct ContentView: View {
    @EnvironmentObject var store: BrowserStore
    var body: some View {
        let t = store.currentTab
        BrowserView(tab: t).id(t.id)
    }
}

struct BrowserView: View {
    @ObservedObject var tab: Tab
    @EnvironmentObject var store: BrowserStore
    @State private var addr = ""
    @State private var showTabs = false
    @State private var showSettings = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            TextField("Search or enter address", text: $addr)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.webSearch)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .submitLabel(.go)
                .focused($focused)
                .onSubmit { tab.load(addr); focused = false }
                .padding(8)
            if tab.progress < 1 { ProgressView(value: tab.progress) }
            WebViewHost(webView: tab.webView)
            Divider()
            HStack {
                Spacer()
                Button { tab.webView.goBack() } label: { Image(systemName: "chevron.left") }.disabled(!tab.canGoBack)
                Spacer()
                Button { tab.webView.goForward() } label: { Image(systemName: "chevron.right") }.disabled(!tab.canGoForward)
                Spacer()
                Button { tab.webView.reload() } label: { Image(systemName: "arrow.clockwise") }
                Spacer()
                Button { tab.load(store.home) } label: { Image(systemName: "house") }
                Spacer()
                Button { showTabs = true } label: { Image(systemName: "square.on.square") }
                Spacer()
                Button { showSettings = true } label: { Image(systemName: "gearshape") }
                Spacer()
            }
            .font(.title3).padding(.vertical, 10)
        }
        .onAppear { addr = tab.url }
        .onChange(of: tab.url) { if !focused { addr = $0 } }
        .sheet(isPresented: $showTabs) { TabsView().environmentObject(store) }
        .sheet(isPresented: $showSettings) { SettingsView().environmentObject(store) }
    }
}

struct TabsView: View {
    @EnvironmentObject var store: BrowserStore
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationView {
            List {
                ForEach(Array(store.tabs.enumerated()), id: \.element.id) { i, t in
                    Button {
                        store.current = i; dismiss()
                    } label: {
                        VStack(alignment: .leading) {
                            Text(t.title.isEmpty ? "New tab" : t.title).lineLimit(1)
                            Text(t.url).font(.caption).foregroundColor(.secondary).lineLimit(1)
                        }
                    }
                }
                .onDelete { idx in idx.forEach { store.closeTab(at: $0) } }
            }
            .navigationTitle("Tabs")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { store.newTab(); dismiss() } label: { Image(systemName: "plus") }
                }
                ToolbarItem(placement: .navigationBarLeading) { Button("Done") { dismiss() } }
            }
        }
    }
}
