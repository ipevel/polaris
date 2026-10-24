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
  // 上次查到的线上最新版（空串 = 没查到更新）。设置页与侧边栏的红点由它与
  // 当前 version 现算，存版本号而不是布尔值，装完新版本红点自己消失（用户第 13 轮第 2 条）。
  update_latest: '',
  update_checked_at: 0,
  // 上次查到的更新包地址。**必须落盘**：红点是跨重启的，重启后点「前往下载」/「下载更新」
  // 时 check_update 的返回值已经不在内存里了，只能从这儿取（否则 GitHub 通道查到的地址会丢）。
  update_url: '',
  // 与 update_url 成对：发布侧 SHA256SUMS.txt 里那个 zip 的 sha256（空串 = 没有可核对的摘要）。
  // 只在地址与上面这个完全一致时才拿来核对下载物，见 ipc.js 的 download_update。
  update_sha256: '',
  // 上次「检查更新」失败的分档码（'' = 查成了）：'net' / 'http:403' / 'parse' / 'config'
  update_check_failed: '',
  // 摘要连续取不到的次数（硬要求②：连续 N 次要升级成显式错误/醒目提示；取到一次就归零）
  update_sha_missing_streak: 0,
  // 上次内核启动定点核对的结果（硬要求①：跳过也要可诊断、关于页可见）
  last_kernel_verify: null,
  // —— 更新来源 ——
  // 远程配置（可选的**覆盖**来源，可以指向镜像 / 自建 CDN）。安卓端在构建期注入同一份地址
  // （.github/workflows/build.yml 的 REMOTE_CONFIG_URLS）；Windows 端以前没人写这个键，
  // 于是 remote.load() 永远拿到空配置 —— 红点永远不会亮（2026-10-11 第 13 轮发现）。
  remote_config_urls: 'https://raw.githubusercontent.com/ipevel/polaris/main/config/remote.json',
  // GitHub Releases 接口基址。空串 = https://api.github.com（与安卓端 AppUpdateChecker 同一套口径）。
  release_api_base: '',
  // —— 内核 / 网络 ——
  proxy_mode: 'rule',         // rule | global | direct
  sys_proxy: true,            // 连接时是否设置系统代理
  tun_mode: false,            // 是否启用 TUN（需管理员）
  allow_lan: false,
  // IPv6：默认**打开**（用户 2026-10-13 定）。
  // 打开后内核会对 v6 目标做解析与直连判定，国内直连拿到的 v6 字面量不再报
  // 「dns resolve failed: ip version error」（见 polaris-plan.md 5.3）。
  ipv6: true,
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
  settings_schema: 4,
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
 *
 * schema 2 → 3（2026-10-13 分组简化，23 → 12 组）：**重置用户自定义的组顺序**。
 *
 * 只重置 `routing_order`，**不动** `routing_rules`（开关）。理由：
 *  - 开关有明确语义，且旧名→新名由 rulesets.js 的 LEGACY_GROUP_ALIASES 迁移
 *    （社交媒体四合一、流媒体二合一、微软二合一、哔哩哔哩/网易音乐并入国内直连），
 *    用户的开关意图能正确带过去；被取消的组（GitHub / Google Play / Google /
 *    微软Bing / 微软服务）查不到映射就自然消失，不会把它们的开关状态误带到别的组
 *    （例如把「关掉 Google」变成「关掉国外穿墙」是灾难性的）。
 *  - 顺序无法迁移：组数从 23 变 12，用户存的顺序里混着一堆已取消的组名，
 *    `orderedTable()` 会跳过它们、剩下 12 个的相对次序仍有效，但整体读起来是
 *    "重叠过的旧顺序"，不如直接回到新表的默认顺序（新表的顺序本来就是按
 *    用户 2026-10-13 的口径重排过的）。
 * 用户自己写的**自定义分流组**（custom_rulesets）完全不受影响。
 */
function migrate(data) {
  const from = Number(data.settings_schema) || 0;
  if (from >= DEFAULTS.settings_schema) return data;
  if (from < 2 && Number(data.mixed_port) === 0) {
    data.mixed_port = DEFAULTS.mixed_port;
    log.info(`settings migrate: mixed_port 0 -> ${DEFAULTS.mixed_port}（旧默认值改成固定端口）`);
  }
  // 外层不设 from < 3 的硬门槛：只要用户自定义过顺序且内置表结构版本落后于当前
  // SCHEMA_VERSION（含 schema 3 → 4 新增「☁️ PikPak」组），就重置顺序，否则新组会被
  // orderedTable() 追加到末尾、排在国内直连之后，规则顺序就错了。
  if (data.routing_order != null) {
    // 这里用延迟 require：store.js 是启动最早的模块之一，rulesets.js 又可能间接依赖
    // 它（manager.js → store），顶层 require 会把循环依赖的风险提前到加载期。
    // 只借它的 SCHEMA_VERSION 做对账，拿不到就退回硬编码的 3。
    let tableVersion = 3;
    try { tableVersion = require('./core/rulesets').SCHEMA_VERSION; } catch (_) {}
    if (from < tableVersion) {
      data.routing_order = null;
      log.info(`settings migrate: 内置分流表结构更新（v${from} → v${tableVersion}），重置组顺序为默认`);
    }
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
    // 顺手在 %LOCALAPPDATA% 留一份救命备份：便携目录被整包替换/云同步清掉时能回填
    require('./backup').mirrorNow();
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
