const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('api', {
  invoke: (c, ...a) => ipcRenderer.invoke(c, ...a),
  on: (c, f) => ipcRenderer.on(c, (_e, ...a) => f(...a))
});
