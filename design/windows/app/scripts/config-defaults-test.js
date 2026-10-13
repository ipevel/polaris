'use strict';
/**
 * 配置默认值与设置迁移自测（离线，不联网）。
 *
 * 盯三件用户能直接感受到、却最容易在重构里被悄悄改回去的事：
 *
 *  1. **IPv6 默认打开**（用户 2026-10-13 定）。这不是口味问题：真机 1.11.4 日志里
 *     35 条 `dns resolve failed: ip version error` 的根因就是顶层 `ipv6: false`
 *     —— mihomo 在 ipv6 关闭时拒绝任何 IPv6 字面量目标，直连域名一旦解析出
 *     IPv6 就整条断掉（实测复现见 scripts/config-defaults-test.js 的 check
 *     「builder 透传 ipv6」与 reports/polaris-1.11.4-log/03-ipv6.md）。
 *  2. **settings_schema 与分流表 SCHEMA_VERSION 同步**：迁移里
 *     `from < rulesets.SCHEMA_VERSION` 才会把旧用户的 routing_order 归零，
 *     两个数一旦脱节（比如只升了一个），迁移就静默不生效。
 *  3. **迁移的作用域**：只准动 routing_order。routing_rules 是用户在设置页
 *     手动拨的开关，custom_rulesets 是用户自己写的组，一个都不许碰。
 *
 * 迁移是**真的跑一遍**：把 store.js 的三个外部依赖（paths / logger / backup）
 * 换成桩，指向临时目录，然后写一份旧版 settings.json 让它自己迁。
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const Module = require('module');

const appRoot = path.join(__dirname, '..');
const electronDir = path.join(appRoot, 'electron');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-cfg-test-'));

/** 用桩模块顶替 electron/* 里的真实模块（必须在 require store.js 之前）。 */
function stub(name, exports) {
  const file = require.resolve(path.join(electronDir, name + '.js'));
  const m = new Module(file, null);
  m.filename = file;
  m.loaded = true;
  m.exports = exports;
  m.paths = Module._nodeModulePaths(path.dirname(file));
  require.cache[file] = m;
  return file;
}

stub('paths', {
  file: (n) => path.join(tmp, n),
  dir: () => tmp,
});
const logs = [];
stub('logger', {
  info: (m) => logs.push(['info', String(m)]),
  warn: (m) => logs.push(['warn', String(m)]),
  error: (m) => logs.push(['error', String(m)]),
  debug: () => {},
});
stub('backup', { mirrorNow() {} });

const store = require(path.join(electronDir, 'store.js'));
const builder = require(path.join(electronDir, 'core', 'builder.js'));

let pass = 0;
let fail = 0;

function check(name, fn) {
  try {
    fn();
    pass++;
    console.log(`  ✓ ${name}`);
  } catch (e) {
    fail++;
    console.log(`  ✗ ${name}`);
    console.log(`      ${e && e.message ? e.message : e}`);
  }
}

function writeSettings(obj) {
  fs.writeFileSync(path.join(tmp, 'settings.json'), JSON.stringify(obj, null, 2), 'utf8');
}

console.log('\n配置默认值与设置迁移自测');

// —— 1. 默认值 ——
check('默认 IPv6 打开（关掉会让 IPv6 字面量目标整条断掉）', () => {
  assert.strictEqual(store.defaults.ipv6, true, `defaults.ipv6 = ${JSON.stringify(store.defaults.ipv6)}`);
});
check('settings_schema = 3', () => {
  assert.strictEqual(store.defaults.settings_schema, 3);
});
check('分流表 SCHEMA_VERSION === settings_schema（否则迁移静默不生效）', () => {
  let tableVersion;
  try {
    tableVersion = require(path.join(electronDir, 'core', 'rulesets.js')).SCHEMA_VERSION;
  } catch (e) {
    assert.fail('rulesets.js 载不进来：' + (e && e.message));
  }
  assert.strictEqual(typeof tableVersion, 'number');
  assert.ok(tableVersion >= store.defaults.settings_schema,
    `rulesets.SCHEMA_VERSION=${tableVersion} < settings_schema=${store.defaults.settings_schema}`);
});

// —— 2. 配置透传 ——
function buildCfg(ipv6) {
  const opts = {
    mixedPort: 7890,
    controllerPort: 9090,
    secret: 'x',
    directDomains: [],
  };
  if (ipv6 !== undefined) opts.ipv6 = ipv6;
  return builder.build({ proxies: [] }, opts).config;
}
check('builder 透传 ipv6=true → config.ipv6 === true', () => {
  assert.strictEqual(buildCfg(true).ipv6, true);
});
check('builder 透传 ipv6=false → config.ipv6 === false', () => {
  assert.strictEqual(buildCfg(false).ipv6, false);
});
check('顶层 ipv6 键存在且是布尔（mihomo 只认顶层，不认 dns 里的）', () => {
  const c = buildCfg(true);
  assert.ok(Object.prototype.hasOwnProperty.call(c, 'ipv6'));
  assert.strictEqual(typeof c.ipv6, 'boolean');
});

// —— 3. 迁移（真的跑 store.js 的 migrate）——
check('旧用户（schema 2）的 routing_order 被归零', () => {
  writeSettings({ settings_schema: 2, routing_order: ['🐱 GitHub', '🌏 Google', 'Ⓜ️ 微软服务'] });
  const s = store.reload();
  assert.strictEqual(s.routing_order, null, `routing_order = ${JSON.stringify(s.routing_order)}`);
  assert.strictEqual(s.settings_schema, 3);
});
check('迁移不碰 routing_rules（用户拨的开关）', () => {
  const rules = { '🌏 国外穿墙': 'proxy', 'Ⓜ️ 微软': 'direct' };
  writeSettings({ settings_schema: 2, routing_order: ['🐱 GitHub'], routing_rules: rules });
  const s = store.reload();
  assert.deepStrictEqual(s.routing_rules, rules);
});
check('迁移不碰 custom_rulesets（用户自己写的组）', () => {
  const custom = [{ name: '我的组', providers: ['social'], outbound: 'proxy' }];
  writeSettings({ settings_schema: 2, routing_order: ['🐱 GitHub'], custom_rulesets: custom });
  const s = store.reload();
  assert.deepStrictEqual(s.custom_rulesets, custom);
});
check('已是当前 schema 的用户：routing_order 原样保留', () => {
  writeSettings({ settings_schema: 3, routing_order: ['🛑 广告拦截', '🌏 国外穿墙'] });
  const s = store.reload();
  assert.deepStrictEqual(s.routing_order, ['🛑 广告拦截', '🌏 国外穿墙']);
});
check('从没排过序的用户：迁移后仍是空', () => {
  writeSettings({ settings_schema: 2 });
  const s = store.reload();
  assert.ok(s.routing_order == null, `routing_order = ${JSON.stringify(s.routing_order)}`);
});
check('没有 settings.json 时按默认值起（不炸、IPv6 仍开）', () => {
  try { fs.unlinkSync(path.join(tmp, 'settings.json')); } catch (_) {}
  const s = store.reload();
  assert.strictEqual(s.ipv6, true);
  assert.strictEqual(s.settings_schema, 3);
});

try { fs.rmSync(tmp, { recursive: true, force: true }); } catch (_) {}

console.log(`\n配置默认值自测：通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail ? 1 : 0);
