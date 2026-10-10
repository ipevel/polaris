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
  tun: 'system',              // gvisor | system | mixed（默认 system：Windows 上性能最好）
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
  // 默认固定 7890（用户第 11 轮第 2 条：点连接就该直接能用，不该让人手填端口）。
  // 0 = 随机；被别的程序占着时走"端口冲突"流程（manager.assertPortFree + 界面问一句要不要关掉它）。
  mixed_port: 7890,
  controller_port: 0,
  last_group: '节点选择',
  last_node: '',
  // —— 运行态快照（跨启动恢复展示）——
  last_status: null,
  subscription_updated_at: 0,
  subscribe_hosts: [],        // 见过的订阅域名，永远直连（见 core/remote.js）
  // —— 内置分流规则（离线 rule-provider，见 core/rulesets.js）——
  routing_on: true,           // 本地分流总开关：true = 屏蔽面板下发的分流方案，只用本地内置方案
  routing_rules: null,        // null = 用内置表的 defaultOn；数组 = 用户显式开关的组名
  routing_order: null,        // null = 用内置表顺序；数组 = 用户自定义顺序（分流页拖动调整）
  custom_rulesets: [],        // 用户自己写的分流组：[{name,out,enabled,rules:['DOMAIN-SUFFIX,x.com']}]
  // 设置文件的结构版本，用来做一次性迁移（见 migrate()）。
  // 没有这一项的老 settings.json 一律当作 0。
  settings_schema: 2,
};

/**
 * 老设置文件的一次性迁移。
 *
 * 为什么需要：store 的规则是「存过的值优先于默认值」，而 save() 会把整个 cache
 * （默认值 + 用户改过的）一起写盘 —— 所以老版本的用户只要改过任何一项设置，
 * settings.json 里就留着一份 `mixed_port: 0`（旧默认值），
 * 新默认的 7890 对他们永远不会生效（用户第 11 轮第 2 条要的就是"点连接就能用"）。
 *
 * schema 1 → 2：`mixed_port === 0` 且文件里没有 schema 标记 = 从没主动选过端口，
 * 按新默认给 7890。升级后用户自己再改成 0（= 自动）会被记成 schema 2，不再迁移。
 */
function migrate(data) {
  const from = Number(data.settings_schema) || 0;
  if (from >= DEFAULTS.settings_schema) return data;
  if (from < 2 && Number(data.mixed_port) === 0) {
    data.mixed_port = DEFAULTS.mixed_port;
    log.info(`settings migrate: mixed_port 0 -> ${DEFAULTS.mixed_port}（旧默认值改成固定端口）`);
  }
  data.settings_schema = DEFAULTS.settings_schema;
  return data;
}

let cache = null;

function file() { return paths.file('settings.json'); }

function load() {
  if (cache) return cache;
  let data = {};
  let had = false;
  try {
    data = JSON.parse(fs.readFileSync(file(), 'utf8'));
    had = true;
  } catch (_) {
    data = {};
  }
  const before = data.settings_schema;
  data = migrate(data);
  cache = Object.assign({}, DEFAULTS, data);
  // 迁移过的（或第一次跑）落一次盘，免得每次启动都重算
  if (had && before !== cache.settings_schema) save();
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
