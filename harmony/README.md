# FreeBrowser for HarmonyOS NEXT (source only, untested)

Minimal ArkTS/ArkWeb browser: address bar with DuckDuckGo search, back/forward/reload/home.
Not included on HarmonyOS: header stripping (X-Frame-Options/CSP/CORS), tabs, extensions.

## Build
1. Install **DevEco Studio** (developer.huawei.com) on Windows/macOS.
2. Open this `harmony/` folder, let it sync the SDK.
3. Create a free Huawei developer account; in DevEco: *File > Project Structure > Signing Configs > Automatically generate signature* (needs your phone connected with USB debugging / Developer Mode on).
4. Press Run to install on the phone, or *Build > Build Hap(s)*.
