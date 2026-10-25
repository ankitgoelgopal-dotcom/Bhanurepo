import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject var store: BrowserStore
    @Environment(\.dismiss) var dismiss
    @State private var showImporter = false
    @State private var editing: UserScriptItem?

    var body: some View {
        NavigationView {
            Form {
                Section("Browsing") {
                    TextField("Home page", text: $store.home).textInputAutocapitalization(.never).disableAutocorrection(true)
                    TextField("Search URL (%s = query)", text: $store.searchTemplate).textInputAutocapitalization(.never).disableAutocorrection(true)
                    Toggle("Request desktop sites", isOn: $store.desktopMode)
                }
                Section(header: Text("Security"), footer: Text("Lets pages with expired or invalid certificates load. Your traffic to those sites is not protected. Leave off unless you need it.")) {
                    Toggle("Allow invalid certificates", isOn: $store.allowBadCerts)
                }
                Section(header: Text("Custom request headers"), footer: Text("One per line, Name: value. Sent with pages you open from the address bar.")) {
                    TextEditor(text: $store.headersText).frame(minHeight: 70).font(.system(.body, design: .monospaced))
                }
                Section(header: Text("Extensions (scripts)"), footer: Text("Scripts run on matching pages. Match pattern example: https://*.example.com/* or <all_urls>. Reload the page after changes.")) {
                    ForEach($store.scripts) { $s in
                        HStack {
                            Button { editing = s } label: {
                                VStack(alignment: .leading) {
                                    Text(s.name); Text(s.match).font(.caption).foregroundColor(.secondary)
                                }
                            }
                            Spacer()
                            Toggle("", isOn: $s.enabled).labelsHidden()
                        }
                    }
                    .onDelete { store.scripts.remove(atOffsets: $0) }
                    Button("Add script") {
                        editing = UserScriptItem(name: "New script", match: "<all_urls>", code: "")
                    }
                    Button("Import .js file") { showImporter = true }
                }
            }
            .navigationTitle("Settings")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { dismiss() } } }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.javaScript, .plainText]) { result in
                if case .success(let url) = result, url.startAccessingSecurityScopedResource() {
                    defer { url.stopAccessingSecurityScopedResource() }
                    if let code = try? String(contentsOf: url) {
                        store.scripts.append(UserScriptItem(name: url.lastPathComponent, match: "<all_urls>", code: code))
                    }
                }
            }
            .sheet(item: $editing) { item in
                ScriptEditor(item: item) { saved in
                    if let i = store.scripts.firstIndex(where: { $0.id == saved.id }) { store.scripts[i] = saved }
                    else { store.scripts.append(saved) }
                }
            }
        }
    }
}

struct ScriptEditor: View {
    @State var item: UserScriptItem
    var onSave: (UserScriptItem) -> Void
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationView {
            Form {
                TextField("Name", text: $item.name)
                TextField("Match pattern", text: $item.match).textInputAutocapitalization(.never).disableAutocorrection(true)
                Section("JavaScript") {
                    TextEditor(text: $item.code).frame(minHeight: 220).font(.system(.body, design: .monospaced))
                        .textInputAutocapitalization(.never).disableAutocorrection(true)
                }
            }
            .navigationTitle("Script")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .navigationBarTrailing) { Button("Save") { onSave(item); dismiss() } }
            }
        }
    }
}
