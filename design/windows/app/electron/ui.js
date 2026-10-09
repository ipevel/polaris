'use strict';
/** 渲染层辅助：向所有窗口推送事件、驱动页面跳转。 */

const { BrowserWindow } = require('electron');

function emit(channel, payload) {
  for (const w of BrowserWindow.getAllWindows()) {
    if (!w.isDestroyed()) w.webContents.send(`polaris:event:${channel}`, payload);
  }
}

function navigate(route) { emit('navigate', { route }); }

function toast(message) { emit('toast', { message }); }

module.exports = { emit, navigate, toast };
