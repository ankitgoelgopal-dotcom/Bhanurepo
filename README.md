# FreeBrowser

An Android browser (Kotlin, Jetpack Compose, WebView) with header-bypass and a lightweight extension loader.

## Features
- **Header bypass**: `shouldInterceptRequest` re-fetches GET requests with OkHttp and strips `X-Frame-Options`, `Content-Security-Policy(-Report-Only)`, `COOP/COEP/CORP`, and adds `Access-Control-Allow-Origin`. Toggle in Settings.
- **Extensions**: import unpacked Chrome-style extensions as `.zip` (must contain `manifest.json`). Supports `content_scripts` (`matches`, `js`, `css`, `run_at`) and `background.scripts`, plus a tiny `chrome.runtime` / `chrome.storage.local` shim. Toggle or delete in Settings.
- **UI**: address bar with search, back/forward/refresh/home, multiple tabs, settings (home page, search URL, custom request headers).

## Build the APK
**Option A – Android Studio:** open this folder, let it sync, then *Build > Build APK(s)*.
**Option B – command line** (JDK 17, Android SDK, Gradle 8.7+): `gradle assembleRelease (with your own signing key)` → `app/build/outputs/apk/release/app-release.apk`.
**Option C – GitHub Actions:** the *Build APK* workflow builds on every push; download `FreeBrowser-release-apk` from the run's artifacts.

Install: `adb install app-release.apk`, or copy the APK to your phone and open it (allow "install unknown apps").

## Limitations
- Only GET requests can be rewritten; POST/other methods load with original headers.
- CORS preflight (OPTIONS) requests and `<meta>` CSP tags are not rewritten.
- Redirects are followed inside the interceptor, so the address bar may show the final URL.
- Extension support is partial: no `chrome.tabs`/`webRequest` APIs, content scripts run in the top frame only, and background scripts run in a hidden page.
- Disabling CSP/frame protection removes safeguards against clickjacking and XSS. Use it on sites you trust.

## iPhone (.ipa)
`ios/` is a SwiftUI/WKWebView port. The *Build iOS IPA* workflow produces an **unsigned** `FreeBrowser.ipa` (artifact `FreeBrowser-ipa`); sign and install it with AltStore or Sideloadly. iOS cannot rewrite response headers (X-Frame-Options/CSP/CORS), so that feature is Android-only; extensions are user scripts with match patterns.
