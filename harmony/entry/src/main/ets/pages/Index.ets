import { webview } from '@kit.ArkWeb';

const HOME: string = 'https://duckduckgo.com';
const SEARCH: string = 'https://duckduckgo.com/?q=';

@Entry
@Component
struct Index {
  controller: webview.WebviewController = new webview.WebviewController();
  @State addr: string = '';
  @State progress: number = 0;

  resolve(input: string): string {
    const t: string = input.trim();
    if (t.length === 0) { return HOME; }
    if (/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(t)) { return t; }
    if (t.indexOf(' ') < 0 && t.indexOf('.') >= 0) { return 'https://' + t; }
    return SEARCH + encodeURIComponent(t);
  }

  build() {
    Column() {
      TextInput({ text: this.addr, placeholder: 'Search or enter address' })
        .height(44).margin(8).type(InputType.Normal).enterKeyType(EnterKeyType.Go)
        .onChange((v: string) => { this.addr = v; })
        .onSubmit(() => { this.controller.loadUrl(this.resolve(this.addr)); })
      if (this.progress > 0 && this.progress < 100) {
        Progress({ value: this.progress, total: 100 }).width('100%')
      }
      Web({ src: HOME, controller: this.controller })
        .javaScriptAccess(true)
        .domStorageAccess(true)
        .mixedMode(MixedMode.All)
        .onProgressChange((e) => { if (e) { this.progress = e.newProgress; } })
        .onPageEnd(() => { this.addr = this.controller.getUrl(); })
        .layoutWeight(1)
      Row() {
        Button('Back').onClick(() => { if (this.controller.accessBackward()) { this.controller.backward(); } })
        Button('Forward').onClick(() => { if (this.controller.accessForward()) { this.controller.forward(); } })
        Button('Reload').onClick(() => { this.controller.refresh(); })
        Button('Home').onClick(() => { this.controller.loadUrl(HOME); })
      }
      .width('100%').justifyContent(FlexAlign.SpaceAround).padding(8)
    }
    .width('100%').height('100%')
  }
}
