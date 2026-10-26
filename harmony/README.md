# FreeBrowser for HarmonyOS NEXT (HarmonyOS 5)

Source for a simple ArkTS browser (ArkWeb). **Not built or tested by CI** - HarmonyOS apps must be built and signed with Huawei's DevEco Studio.

Features: address bar with DuckDuckGo search, back/forward/reload/home, tabs.
Not included: header stripping and extensions.

## Build and install
1. Install **DevEco Studio** (free, from developer.huawei.com) and sign in with a Huawei ID.
2. File > New > Create Project > **Empty Ability**. Name it FreeBrowser, API 12 or newer.
3. Replace `entry/src/main/ets/pages/Index.ets` with `Index.ets` from this folder.
4. In `entry/src/main/module.json5`, inside `"module": { ... }`, add:
   `"requestPermissions": [ { "name": "ohos.permission.INTERNET" } ]`
5. On the phone: Settings > About phone > tap the version number 7 times, then Settings > System > Developer options > turn on USB debugging (HDC).
6. Connect by USB, choose the phone as the target in DevEco Studio, then File > Project Structure > Signing Configs > tick "Automatically generate signature". Press Run.
