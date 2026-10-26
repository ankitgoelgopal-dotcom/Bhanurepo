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

## Windows (.exe)
`desktop/` is an Electron (Chromium) app. The *Build Windows EXE* workflow produces a portable, unsigned `FreeBrowser.exe` (artifact `FreeBrowser-exe`). It strips X-Frame-Options/CSP/COOP/COEP/CORP, adds CORS headers, supports custom request headers, tabs, and unpacked Chrome extensions (partial API support).

## Linux (AppImage)
The *Build Linux AppImage* workflow builds `FreeBrowser.AppImage` from `desktop/`. Run: `chmod +x FreeBrowser.AppImage && ./FreeBrowser.AppImage`. On Ubuntu 22.04+ install `libfuse2`; if it fails with a sandbox error on Ubuntu 23.10+, try `./FreeBrowser.AppImage --no-sandbox`.

## LG webOS TV (2019 / webOS 4.5)
`webos/` is a web-app launcher (address bar, search via DuckDuckGo, editable tiles) packaged as an `.ipk` by the *Build webOS IPK* workflow. It navigates the TV's Chromium (v53) to the site you choose; Back returns to the launcher. Header stripping and extensions are not possible on webOS. Install with the LG Developer Mode app + `ares-install`, or webOS Dev Manager.

## Universal JAR (java-app/)
Swing + FlatLaf starter. Build: `cd java-app && mvn package`; run: `java -jar target/app.jar` (Java 17+). CI artifact: `app-jar`.
