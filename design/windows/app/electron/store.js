'use strict';
/**
 * 持久化：data/settings.json（原子写）+ 内存缓存。
 * 凭据不放这里，见 panel/credentials.js（走 Electron safeStorage 加密）。
 */

const fs = require('fs');
const path = require('path');
const paths = require('./paths');
const log = require('./logger');

const DEFAULTS = {
  // —— 前端可见（api.js get_settings 的形状）——
  theme: 'system',            // system | light | dark
  lang: 'zh-CN',
  tun: 'gvisor',              // gvisor | system | mixed
  expire_notify: true,
  traffic_notify: true,
  autostart: false,
  version: '',                // 由 package.json 注入
  // —— 面板 ——
  panel_url: '',
  last_email: '',
  auto_update: true,
  // —— 内核 / 网络 ——
  proxy_mode: 'rule',         // rule | global | direct
  sys_proxy: true,            // 连接时是否设置系统代理
  tun_mode: false,            // 是否启用 TUN（需管理员）
  allow_lan: false,
  ipv6: false,
  mixed_port: 0,              // 0 = 随机
  controller_port: 0,
  last_group: '节点选择',
  last_node: '',
  // —— 运行态快照（跨启动恢复展示）——
  last_status: null,
  subscription_updated_at: 0,
  subscribe_hosts: [],        // 见过的订阅域名，永远直连（见 core/remote.js）
  // —— 内置分流规则（离线 rule-provider，见 core/rulesets.js）——
  routing_rules: null,        // null = 用内置表的 defaultOn；数组 = 用户显式开关的组名
  routing_order: null,        // null = 用内置表顺序；数组 = 用户自定义顺序（预留）
};

let cache = null;

function file() { return paths.file('settings.json'); }

function load() {
  if (cache) return cache;
  let data = {};
  try {
    data = JSON.parse(fs.readFileSync(file(), 'utf8'));
  } catch (_) {
    data = {};
  }
  cache = Object.assign({}, DEFAULTS, data);
  return cache;
}

function save() {
  if (!cache) return;
  const target = file();
  const tmp = `${target}.tmp`;
  try {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(tmp, JSON.stringify(cache, null, 2), 'utf8');
    fs.renameSync(tmp, target);
  } catch (e) {
    log.warn('settings save failed:', e && e.message);
  }
}

module.exports = {
  defaults: DEFAULTS,
  all: () => Object.assign({}, load()),
  get: (key) => load()[key],
  set(key, value) {
    load()[key] = value;
    save();
    return value;
  },
  patch(obj) {
    Object.assign(load(), obj || {});
    save();
    return Object.assign({}, load());
  },
  reload() { cache = null; return load(); },
};
