'use strict';
/**
 * 预加载：把渲染层能用的能力以 contextBridge 暴露出去。
 *
 * 渲染层契约与 src/js/api.js 里原本的 Tauri 调用完全一致 —— 命令名不变，
 * 只是 invoke 的落地从 Tauri 换成主进程。src/js 只做两处 1 行改动。
 */

const { contextBridge, ipcRenderer } = require('electron');

const CH_INVOKE = 'polaris:invoke';
const CH_EVENT = 'polaris:event:';

contextBridge.exposeInMainWorld('polaris', {
  isElectron: true,

  /** 调用后端命令；后端抛错时 Promise reject */
  invoke: (cmd, args) => ipcRenderer.invoke(CH_INVOKE, cmd, args || {}),

  /** 订阅主进程事件：status / traffic / log / kernel */
  on(event, handler) {
    const listener = (_e, payload) => handler(payload);
    ipcRenderer.on(CH_EVENT + event, listener);
    return () => ipcRenderer.removeListener(CH_EVENT + event, listener);
  },

  /** 窗口控制（自绘标题栏） */
  win: {
    minimize: () => ipcRenderer.invoke('polaris:win', 'minimize'),
    toggleMaximize: () => ipcRenderer.invoke('polaris:win', 'toggleMaximize'),
    close: () => ipcRenderer.invoke('polaris:win', 'close'),
    isMaximized: () => ipcRenderer.invoke('polaris:win', 'isMaximized'),
  },

  /** 复制到剪贴板（邀请链接等） */
  clipboard: { writeText: (t) => ipcRenderer.invoke('polaris:clipboard', String(t)) },
});
