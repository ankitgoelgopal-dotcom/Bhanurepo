const { app, BrowserWindow, WebContentsView, ipcMain, session, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

const TOP = 88; // tab strip (38) + toolbar (50)
const defaults = {
  home: 'https://duckduckgo.com',
  search: 'https://duckduckgo.com/?q=%s',
  bypass: true, allowBadCerts: false, headers: '', extensions: []
};
let settings = { ...defaults };
let win, tabs = [], active = 0, nextId = 1, viewVisible = true;
const extIds = new Map();
const pending = new Map();

const sfile = () => path.join(app.getPath('userData'), 'settings.json');
function loadSettings() { try { settings = { ...defaults, ...JSON.parse(fs.readFileSync(sfile(), 'utf8')) }; } catch {} }
function saveSettings() { fs.mkdirSync(path.dirname(sfile()), { recursive: true }); fs.writeFileSync(sfile(), JSON.stringify(settings, null, 2)); }

function parseHeaders(text) {
  const out = {};
  for (const line of (text || '').split('\n')) {
    const i = line.indexOf(':');
    if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return out;
}

function resolveInput(input) {
  const t = (input || '').trim();
  if (!t) return settings.home;
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(t) || t.startsWith('about:')) return t;
  if (!t.includes(' ') && (t.includes('.') || t.startsWith('localhost'))) return 'https://' + t;
  return settings.search.replace('%s', encodeURIComponent(t));
}

// ---- header rewriting (X-Frame-Options, CSP, CORS) ----
const STRIP = new Set(['x-frame-options', 'content-security-policy', 'content-security-policy-report-only',
  'cross-origin-opener-policy', 'cross-origin-embedder-policy', 'cross-origin-resource-policy']);

function setupSession() {
  const ses = session.defaultSession;
  ses.webRequest.onBeforeSendHeaders((d, cb) => {
    const h = d.requestHeaders;
    if (h.Origin) { if (pending.size > 2000) pending.clear(); pending.set(d.id, { o: h.Origin, hd: h['Access-Control-Request-Headers'] }); }
    Object.assign(h, parseHeaders(settings.headers));
    cb({ requestHeaders: h });
  });
  ses.webRequest.onHeadersReceived((d, cb) => {
    const req = pending.get(d.id); pending.delete(d.id);
    if (!settings.bypass) return cb({});
    const h = {};
    for (const [k, v] of Object.entries(d.responseHeaders || {})) {
      const lk = k.toLowerCase();
      if (STRIP.has(lk) || lk.startsWith('access-control-')) continue;
      h[k] = v;
    }
    h['Access-Control-Allow-Origin'] = [req ? req.o : '*'];
    if (req) h['Access-Control-Allow-Credentials'] = ['true'];
    h['Access-Control-Allow-Methods'] = ['GET, POST, PUT, PATCH, DELETE, OPTIONS'];
    h['Access-Control-Allow-Headers'] = [(req && req.hd) || '*'];
    cb({ responseHeaders: h });
  });
}

app.on('certificate-error', (e, wc, url, err, cert, cb) => {
  if (settings.allowBadCerts) { e.preventDefault(); cb(true); } else cb(false);
});

// ---- extensions (unpacked Chrome extensions) ----
const extApi = () => session.defaultSession.extensions || session.defaultSession;
async function loadExt(e) {
  try {
    const x = await extApi().loadExtension(e.path, { allowFileAccess: true });
    extIds.set(e.path, x.id); e.name = x.name; e.error = undefined;
  } catch (err) { e.error = String(err.message || err); }
}
function unloadExt(e) {
  const id = extIds.get(e.path);
  if (id) { try { extApi().removeExtension(id); } catch {} extIds.delete(e.path); }
}

// ---- tabs ----
const activeTab = () => tabs.find(t => t.id === active);
const canBack = wc => wc.navigationHistory ? wc.navigationHistory.canGoBack() : wc.canGoBack();
const canFwd = wc => wc.navigationHistory ? wc.navigationHistory.canGoForward() : wc.canGoForward();

function layout() {
  const t = activeTab(); if (!t || !win) return;
  const { width, height } = win.getContentBounds();
  t.view.setBounds({ x: 0, y: TOP, width, height: Math.max(0, height - TOP) });
}
function showActive() {
  for (const t of tabs) { try { win.contentView.removeChildView(t.view); } catch {} }
  const t = activeTab();
  if (t && viewVisible) { win.contentView.addChildView(t.view); layout(); }
}
function sendState() {
  if (!win || win.isDestroyed()) return;
  const t = activeTab();
  win.webContents.send('state', {
    active,
    canBack: t ? canBack(t.view.webContents) : false,
    canForward: t ? canFwd(t.view.webContents) : false,
    tabs: tabs.map(x => ({ id: x.id, title: x.view.webContents.getTitle() || 'New tab', url: x.url || '', loading: x.view.webContents.isLoading() }))
  });
}

function createTab(input) {
  const view = new WebContentsView({ webPreferences: { contextIsolation: true, sandbox: true } });
  const tab = { id: nextId++, view, url: '' };
  tabs.push(tab);
  const wc = view.webContents;
  wc.setWindowOpenHandler(({ url }) => { createTab(url); return { action: 'deny' }; });
  for (const ev of ['page-title-updated', 'did-start-loading', 'did-stop-loading']) wc.on(ev, sendState);
  for (const ev of ['did-navigate', 'did-navigate-in-page']) wc.on(ev, (_e, url) => { tab.url = url; sendState(); });
  wc.on('did-fail-load', (_e, code, desc, url, isMain) => {
    if (!isMain || code === -3) return;
    const esc = s => String(s).replace(/</g, '&lt;');
    wc.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
      `<body style="font-family:system-ui;padding:40px"><h2>Can't open page</h2><p>${esc(desc)} (${code})</p><p>${esc(url)}</p></body>`));
  });
  wc.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown' || !(input.control || input.meta)) return;
    const k = input.key.toLowerCase();
    if (k === 't') { e.preventDefault(); createTab(settings.home); }
    else if (k === 'w') { e.preventDefault(); closeTab(tab.id); }
    else if (k === 'l') { e.preventDefault(); win.webContents.focus(); win.webContents.send('focus-address'); }
    else if (k === 'r') { e.preventDefault(); wc.reload(); }
  });
  active = tab.id;
  showActive();
  wc.loadURL(resolveInput(input)).catch(() => {});
  sendState();
}

function closeTab(id) {
  const i = tabs.findIndex(t => t.id === id); if (i < 0) return;
  const [t] = tabs.splice(i, 1);
  try { win.contentView.removeChildView(t.view); } catch {}
  t.view.webContents.close();
  if (!tabs.length) return createTab(settings.home);
  if (active === id) active = tabs[Math.min(i, tabs.length - 1)].id;
  showActive(); sendState();
}

function createWindow() {
  win = new BrowserWindow({
    width: 1200, height: 800, backgroundColor: '#1e1e1e', title: 'FreeBrowser', autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.setMenu(null);
  win.loadFile(path.join(__dirname, 'ui.html'));
  win.webContents.on('will-navigate', e => e.preventDefault());
  win.on('resize', layout);
  win.webContents.once('did-finish-load', () => createTab(settings.home));
}

ipcMain.handle('go', (_e, input) => { const t = activeTab(); if (t) t.view.webContents.loadURL(resolveInput(input)).catch(() => {}); });
ipcMain.handle('back', () => { const t = activeTab(); if (t) t.view.webContents.goBack(); });
ipcMain.handle('forward', () => { const t = activeTab(); if (t) t.view.webContents.goForward(); });
ipcMain.handle('reload', () => { const t = activeTab(); if (t) t.view.webContents.reload(); });
ipcMain.handle('home', () => { const t = activeTab(); if (t) t.view.webContents.loadURL(resolveInput(settings.home)).catch(() => {}); });
ipcMain.handle('newTab', () => createTab(settings.home));
ipcMain.handle('closeTab', (_e, id) => closeTab(id));
ipcMain.handle('selectTab', (_e, id) => { active = id; showActive(); sendState(); });
ipcMain.handle('view:visible', (_e, v) => { viewVisible = v; showActive(); });
ipcMain.handle('settings:get', () => settings);
ipcMain.handle('settings:set', (_e, s) => {
  for (const k of ['home', 'search', 'headers']) if (typeof s[k] === 'string') settings[k] = s[k];
  for (const k of ['bypass', 'allowBadCerts']) if (typeof s[k] === 'boolean') settings[k] = s[k];
  saveSettings(); return settings;
});
ipcMain.handle('ext:add', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory'], title: 'Select an unpacked extension folder (contains manifest.json)' });
  if (r.canceled || !r.filePaths[0]) return settings;
  const p = r.filePaths[0];
  if (!settings.extensions.some(e => e.path === p)) {
    const e = { path: p, enabled: true, name: path.basename(p) };
    await loadExt(e); settings.extensions.push(e); saveSettings();
  }
  return settings;
});
ipcMain.handle('ext:toggle', async (_e, p, on) => {
  const e = settings.extensions.find(x => x.path === p); if (!e) return settings;
  e.enabled = on; if (on) await loadExt(e); else unloadExt(e);
  saveSettings(); return settings;
});
ipcMain.handle('ext:remove', (_e, p) => {
  const e = settings.extensions.find(x => x.path === p); if (e) unloadExt(e);
  settings.extensions = settings.extensions.filter(x => x.path !== p);
  saveSettings(); return settings;
});

app.whenReady().then(async () => {
  loadSettings(); setupSession();
  for (const e of settings.extensions) if (e.enabled) await loadExt(e);
  createWindow();
});
app.on('window-all-closed', () => app.quit());
