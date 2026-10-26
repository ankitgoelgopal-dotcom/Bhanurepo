import { webview } from '@kit.ArkWeb';

const HOME: string = 'https://duckduckgo.com';
const SEARCH: string = 'https://duckduckgo.com/?q=';

class TabInfo {
  id: number;
  src: string;
  title: string = 'New tab';
  url: string;
  controller: webview.WebviewController = new webview.WebviewController();

  constructor(id: number, src: string) {
    this.id = id;
    this.src = src;
    this.url = src;
  }
}

@Entry
@Component
struct Index {
  @State tabs: TabInfo[] = [new TabInfo(1, HOME)];
  @State activeId: number = 1;
  @State addr: string = '';
  @State progress: number = 100;
  @State showTabs: boolean = false;
  private nextId: number = 2;

  active(): TabInfo {
    return this.tabs.find((t: TabInfo) => t.id === this.activeId) ?? this.tabs[0];
  }

  resolve(input: string): string {
    const t: string = input.trim();
    if (t.length === 0) {
      return HOME;
    }
    if (t.startsWith('http://') || t.startsWith('https://')) {
      return t;
    }
    if (t.indexOf(' ') < 0 && t.indexOf('.') >= 0) {
      return 'https://' + t;
    }
    return SEARCH + encodeURIComponent(t);
  }

  newTab(): void {
    const tab: TabInfo = new TabInfo(this.nextId++, HOME);
    this.tabs.push(tab);
    this.activeId = tab.id;
    this.addr = '';
    this.showTabs = false;
  }

  closeTab(id: number): void {
    if (this.tabs.length <= 1) {
      return;
    }
    const idx: number = this.tabs.findIndex((t: TabInfo) => t.id === id);
    this.tabs.splice(idx, 1);
    if (this.activeId === id) {
      this.activeId = this.tabs[Math.min(idx, this.tabs.length - 1)].id;
    }
  }

  onBackPress(): boolean {
    const c: webview.WebviewController = this.active().controller;
    if (c.accessBackward()) {
      c.backward();
      return true;
    }
    return false;
  }

  build() {
    Stack() {
      Column() {
        Row() {
          TextInput({ text: this.addr, placeholder: 'Search or enter address' })
            .layoutWeight(1)
            .height(40)
            .enterKeyType(EnterKeyType.Go)
            .onChange((v: string) => {
              this.addr = v;
            })
            .onSubmit(() => {
              this.active().controller.loadUrl(this.resolve(this.addr));
            })
        }
        .padding(8)

        if (this.progress < 100) {
          Progress({ value: this.progress, total: 100 }).width('100%')
        }

        Stack() {
          ForEach(this.tabs, (t: TabInfo) => {
            Web({ src: t.src, controller: t.controller })
              .javaScriptAccess(true)
              .domStorageAccess(true)
              .mixedMode(MixedMode.All)
              .width('100%')
              .height('100%')
              .visibility(t.id === this.activeId ? Visibility.Visible : Visibility.None)
              .onPageBegin((event) => {
                if (event && t.id === this.activeId) {
                  this.addr = event.url;
                }
                if (event) {
                  t.url = event.url;
                }
              })
              .onTitleReceive((event) => {
                if (event) {
                  t.title = event.title;
                }
              })
              .onProgressChange((event) => {
                if (event && t.id === this.activeId) {
                  this.progress = event.newProgress;
                }
              })
          }, (t: TabInfo) => t.id.toString())
        }
        .layoutWeight(1)

        Row() {
          Button('<').onClick(() => {
            const c: webview.WebviewController = this.active().controller;
            if (c.accessBackward()) {
              c.backward();
            }
          })
          Button('>').onClick(() => {
            const c: webview.WebviewController = this.active().controller;
            if (c.accessForward()) {
              c.forward();
            }
          })
          Button('Reload').onClick(() => {
            this.active().controller.refresh();
          })
          Button('Home').onClick(() => {
            this.active().controller.loadUrl(HOME);
          })
          Button(`Tabs (${this.tabs.length})`).onClick(() => {
            this.showTabs = true;
          })
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceAround)
        .padding(8)
      }
      .width('100%')
      .height('100%')

      if (this.showTabs) {
        Column({ space: 8 }) {
          Row() {
            Text('Tabs').fontSize(22).layoutWeight(1)
            Button('New tab').onClick(() => {
              this.newTab();
            })
            Button('Close').margin({ left: 8 }).onClick(() => {
              this.showTabs = false;
            })
          }
          .width('100%')

          ForEach(this.tabs, (t: TabInfo) => {
            Row() {
              Column() {
                Text(t.title.length > 0 ? t.title : 'New tab').maxLines(1).fontSize(16)
                Text(t.url).maxLines(1).fontSize(12).fontColor('#888888')
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
              .onClick(() => {
                this.activeId = t.id;
                this.addr = t.url;
                this.showTabs = false;
              })

              Button('x').onClick(() => {
                this.closeTab(t.id);
              })
            }
            .width('100%')
            .padding(8)
          }, (t: TabInfo) => t.id.toString())
        }
        .width('100%')
        .height('100%')
        .padding(16)
        .backgroundColor('#FFFFFF')
      }
    }
    .width('100%')
    .height('100%')
  }
}
