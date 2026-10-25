import SwiftUI
import WebKit

struct UserScriptItem: Codable, Identifiable {
    var id = UUID()
    var name: String
    var match: String
    var code: String
    var enabled: Bool = true
}

func globToRegex(_ g: String) -> String {
    if g.isEmpty || g == "<all_urls>" { return "^https?://.*" }
    let esc = NSRegularExpression.escapedPattern(for: g).replacingOccurrences(of: "\\*", with: ".*")
    return "^" + esc + "$"
}

func jsString(_ s: String) -> String {
    guard let d = try? JSONSerialization.data(withJSONObject: s, options: [.fragmentsAllowed]),
          let r = String(data: d, encoding: .utf8) else { return "\"\"" }
    return r
}

final class Tab: NSObject, ObservableObject, Identifiable, WKNavigationDelegate, WKUIDelegate {
    let id = UUID()
    @Published var title = "New tab"
    @Published var url = ""
    @Published var progress = 0.0
    @Published var canGoBack = false
    @Published var canGoForward = false
    let webView: WKWebView
    weak var store: BrowserStore?
    private var observers: [NSKeyValueObservation] = []

    init(store: BrowserStore) {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        webView = WKWebView(frame: .zero, configuration: cfg)
        super.init()
        self.store = store
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        observers = [
            webView.observe(\.title) { [weak self] w, _ in self?.title = w.title ?? "" },
            webView.observe(\.url) { [weak self] w, _ in self?.url = w.url?.absoluteString ?? "" },
            webView.observe(\.estimatedProgress) { [weak self] w, _ in self?.progress = w.estimatedProgress },
            webView.observe(\.canGoBack) { [weak self] w, _ in self?.canGoBack = w.canGoBack },
            webView.observe(\.canGoForward) { [weak self] w, _ in self?.canGoForward = w.canGoForward }
        ]
        applyScripts()
    }

    func load(_ input: String) {
        guard let s = store?.resolve(input), let u = URL(string: s) else { return }
        var req = URLRequest(url: u)
        store?.customHeaders().forEach { req.setValue($1, forHTTPHeaderField: $0) }
        webView.load(req)
    }

    func applyScripts() {
        let ucc = webView.configuration.userContentController
        ucc.removeAllUserScripts()
        for s in store?.scripts ?? [] where s.enabled {
            let src = "(function(){var re=new RegExp(\(jsString(globToRegex(s.match))));if(!re.test(location.href))return;try{\n\(s.code)\n}catch(e){console.error(e)}})();"
            ucc.addUserScript(WKUserScript(source: src, injectionTime: .atDocumentEnd, forMainFrameOnly: false))
        }
    }

    // MARK: WKNavigationDelegate
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 preferences: WKWebpagePreferences,
                 decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        if let u = action.request.url, let sch = u.scheme?.lowercased(),
           !["http", "https", "about", "data", "blob", "file"].contains(sch) {
            UIApplication.shared.open(u)
            decisionHandler(.cancel, preferences); return
        }
        preferences.preferredContentMode = (store?.desktopMode ?? false) ? .desktop : .recommended
        decisionHandler(.allow, preferences)
    }

    func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge,
                 completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           store?.allowBadCerts == true, let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let e = error as NSError
        if e.code == NSURLErrorCancelled { return }
        let msg = e.localizedDescription.replacingOccurrences(of: "<", with: "&lt;")
        webView.loadHTMLString("<meta name=viewport content='width=device-width'><body style='font-family:-apple-system;padding:24px'><h2>Can't open page</h2><p>\(msg)</p></body>", baseURL: nil)
    }

    // MARK: WKUIDelegate – open target=_blank links in the same tab
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if action.targetFrame == nil { webView.load(action.request) }
        return nil
    }
}

final class BrowserStore: ObservableObject {
    @Published var tabs: [Tab] = []
    @Published var current = 0
    @Published var scripts: [UserScriptItem] {
        didSet {
            if let d = try? JSONEncoder().encode(scripts) { defaults.set(d, forKey: "scripts") }
            tabs.forEach { $0.applyScripts() }
        }
    }
    @Published var home: String { didSet { defaults.set(home, forKey: "home") } }
    @Published var searchTemplate: String { didSet { defaults.set(searchTemplate, forKey: "search") } }
    @Published var headersText: String { didSet { defaults.set(headersText, forKey: "headers") } }
    @Published var desktopMode: Bool { didSet { defaults.set(desktopMode, forKey: "desktop") } }
    @Published var allowBadCerts: Bool { didSet { defaults.set(allowBadCerts, forKey: "badcerts") } }
    private let defaults = UserDefaults.standard

    init() {
        home = defaults.string(forKey: "home") ?? "https://duckduckgo.com"
        searchTemplate = defaults.string(forKey: "search") ?? "https://duckduckgo.com/?q=%s"
        headersText = defaults.string(forKey: "headers") ?? ""
        desktopMode = defaults.bool(forKey: "desktop")
        allowBadCerts = defaults.bool(forKey: "badcerts")
        scripts = (try? JSONDecoder().decode([UserScriptItem].self, from: defaults.data(forKey: "scripts") ?? Data())) ?? []
        newTab()
    }

    var currentTab: Tab { tabs[min(max(current, 0), tabs.count - 1)] }

    func newTab(_ url: String? = nil) {
        let t = Tab(store: self)
        tabs.append(t); current = tabs.count - 1
        t.load(url ?? home)
    }

    func closeTab(at i: Int) {
        guard tabs.count > 1 else { return }
        tabs.remove(at: i)
        current = min(current, tabs.count - 1)
    }

    func customHeaders() -> [String: String] {
        var out: [String: String] = [:]
        for line in headersText.split(separator: "\n") {
            if let i = line.firstIndex(of: ":") {
                let k = line[..<i].trimmingCharacters(in: .whitespaces)
                let v = line[line.index(after: i)...].trimmingCharacters(in: .whitespaces)
                if !k.isEmpty { out[k] = v }
            }
        }
        return out
    }

    func resolve(_ input: String) -> String {
        let t = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return home }
        if t.range(of: "^[a-zA-Z][a-zA-Z0-9+.-]*://", options: .regularExpression) != nil || t.hasPrefix("about:") { return t }
        if !t.contains(" ") && (t.contains(".") || t.hasPrefix("localhost")) { return "https://" + t }
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        let q = t.addingPercentEncoding(withAllowedCharacters: allowed) ?? t
        return searchTemplate.replacingOccurrences(of: "%s", with: q)
    }
}
