'use strict';
/**
 * 自定义分流组「被隐藏但不删」自检（D 单，纯 Node，不联网、不起窗口）。
 *   跑法：node scripts/custom-ruleset-reserved-test.js
 *
 * 总原则：收紧判定只作用于新输入；存量条目**永不删除**，只有「名字与保留名冲突 /
 * 名字或规则不可用」才隐藏，且隐藏必须可见（清单 + 每次读都可见）。
 * 与安卓同口径：判定集合 = 结构组 + 当前内置表名 + mihomo 预注册（旧别名不参与判定）；
 * reason ∈ { reserved, invalid }；清洗只读不回写；四条保存路径基于未过滤的原始列表。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const TMP = path.join(os.homedir(), '.polaris-hidden-test');
fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(TMP, { recursive: true });
process.env.POLARIS_DATA_DIR = TMP;

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}

const rulesets = require('../electron/core/rulesets');
const store = require('../electron/store');
const manager = require('../electron/core/manager');

const RESERVED = 'Ⓜ️ 微软云盘';      // 当前内置表名（改名前叫「Ⓜ️ 微软」）
const OK = '我的规则';
const BAD = '坏名字,带逗号';          // 逗号会破坏 mihomo 规则串 → invalid
const seed = () => [
  { name: RESERVED, out: 'direct', enabled: true, rules: ['DOMAIN-SUFFIX,reserved.example'] },
  { name: OK, out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,ok.example'] },
  { name: BAD, out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,bad.example'] },
];
const names = () => (store.get('custom_rulesets') || []).map((g) => g && g.name);
const reset = () => store.set('custom_rulesets', seed());
const safe = async (fn) => { try { await fn(); } catch (_) { /* 纯 Node 下 reloadConfig 可能没有内核 */ } };
// 旧代码没有 classifyCustom：用空实现兜底，让断言失败而不是把自检打崩（RED 要可读）
const hasClassify = typeof rulesets.classifyCustom === 'function';
const C = hasClassify ? rulesets.classifyCustom : () => ({ list: [], hidden: [] });

async function main() {
console.log('自定义分流组「隐藏但不删」自检');

/* 0. 判定集合：旧别名不参与判定 */
check('判定 旧别名「Ⓜ️ 微软」不在保留名里（只用于迁移）', rulesets.isReservedName('Ⓜ️ 微软') === false);
check('判定 当前表名「Ⓜ️ 微软云盘」是保留名', rulesets.isReservedName(RESERVED) === true);
check('判定 结构组「🚀 节点选择」是保留名', rulesets.isReservedName('🚀 节点选择') === true);
check('判定 mihomo 预注册 DIRECT 是保留名', rulesets.isReservedName('DIRECT') === true);

/* 1. classifyCustom 的分类结果 */
const cls = C(seed());
check('分类 可用列表只剩合法条目', cls.list.length === 1 && cls.list[0].name === OK, JSON.stringify(cls.list.map((g) => g.name)));
check('分类 冲突条目进 hidden 且 reason=reserved', cls.hidden.some((x) => x.name === RESERVED && x.reason === 'reserved'), JSON.stringify(cls.hidden));
check('分类 非法名字进 hidden 且 reason=invalid', cls.hidden.some((x) => x.name === BAD && x.reason === 'invalid'), JSON.stringify(cls.hidden));
check('分类 被隐藏条目不进配置（normalizeCustom 只有 1 条）', rulesets.normalizeCustom(seed()).length === 1);
const LONG25 = 'x'.repeat(25);
const LONG32 = 'y'.repeat(32);
const PIPE = '含|管道符';
const cls2 = C([
  { name: LONG25, out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,a.example'] },
  { name: LONG32, out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,b.example'] },
  { name: PIPE, out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,c.example'] },
]);
check('存量 25 字符的名字仍然是可用的（收紧不追溯）', cls2.list.some((g) => g.name === LONG25));
check('存量 32 字符的名字仍然是可用的（宽松侧 32）', cls2.list.some((g) => g.name === LONG32));
check('存量 观察名单字符（|）暂不隐藏，等真机实测', cls2.list.some((g) => g.name === PIPE));
const cls3 = C([{ name: 'z'.repeat(33), out: 'proxy', enabled: true, rules: ['DOMAIN-SUFFIX,d.example'] }]);
check('存量 超过 32 字符 → 隐藏（invalid）', cls3.hidden.length === 1 && cls3.hidden[0].reason === 'invalid');

/* 2. 四条保存路径都不得抹掉被隐藏条目（RED：旧代码会删） */
reset();
await safe(() => manager.setRuleset(OK, false));
check('保存路径①开关自定义组后，被隐藏条目仍在盘上', names().includes(RESERVED) && names().includes(BAD), JSON.stringify(names()));
reset();
await safe(() => manager.reorderRuleset(OK, 0));
check('保存路径②拖动自定义组后，被隐藏条目仍在盘上', names().includes(RESERVED) && names().includes(BAD), JSON.stringify(names()));
reset();
await safe(() => manager.saveCustomRuleset({ name: '新组', out: 'proxy', rules: 'DOMAIN-SUFFIX,new.example' }));
check('保存路径③新建自定义组后，被隐藏条目仍在盘上', names().includes(RESERVED) && names().includes(BAD), JSON.stringify(names()));
check('保存路径③新建的组确实写进去了', names().includes('新组'), JSON.stringify(names()));
reset();
await safe(() => manager.deleteCustomRuleset(OK));
check('保存路径④删除另一个自定义组后，被隐藏条目仍在盘上', names().includes(RESERVED) && names().includes(BAD), JSON.stringify(names()));
check('保存路径④被删的那个确实没了', !names().includes(OK), JSON.stringify(names()));

/* 3. 用户显式删除被隐藏条目仍然能删掉（隐藏 != 删不掉） */
reset();
await safe(() => manager.deleteCustomRuleset(RESERVED));
check('显式删除被隐藏条目 → 真的删掉', !names().includes(RESERVED), JSON.stringify(names()));

/* 4. rulesetState 每次读都返回 hidden 清单（判据=文件里存在，而不是本次丢了几条） */
reset();
const st = await manager.rulesetState();
check('状态 hidden_count = 2', st.hidden_count === 2, String(st.hidden_count));
check('状态 hidden 带名字与 reason', Array.isArray(st.hidden) && st.hidden.some((x) => x.name === RESERVED && x.reason === 'reserved') && st.hidden.some((x) => x.name === BAD && x.reason === 'invalid'), JSON.stringify(st.hidden));
check('状态 custom 只剩可用条目', st.custom.length === 1 && st.custom[0].name === OK, JSON.stringify(st.custom.map((g) => g.name)));
const st2 = await manager.rulesetState();
check('状态 再读一次仍然可见（不是一次性的）', st2.hidden_count === 2);

/* 5. serializeCustom 不过滤（源码断言） */
const src = fs.readFileSync(path.join(__dirname, '..', 'electron', 'core', 'rulesets.js'), 'utf8');
check('serializeCustom 不再调用 normalizeCustom', /function serializeCustom\(list\) \{[\s\S]{0,120}?return \(Array\.isArray\(list\)/.test(src));
const msrc = fs.readFileSync(path.join(__dirname, '..', 'electron', 'core', 'manager.js'), 'utf8');
const bodyOf = (fn) => {
  const i = msrc.indexOf('async function ' + fn + '(');
  if (i < 0) return '';
  const j = msrc.indexOf('\\n}', i);
  return msrc.slice(i, j < 0 ? i + 800 : j);
};
check('四条保存路径的函数体里都不再出现 normalizeCustom',
  ['setRuleset', 'reorderRuleset', 'saveCustomRuleset', 'deleteCustomRuleset'].every((fn) => bodyOf(fn) && !/normalizeCustom/.test(bodyOf(fn))),
  ['setRuleset', 'reorderRuleset', 'saveCustomRuleset', 'deleteCustomRuleset'].map((fn) => fn + ':' + /normalizeCustom/.test(bodyOf(fn))).join(' '));
check('四条保存路径都基于 rawCustomList（源码计数 ≥ 4）', (msrc.match(/rawCustomList\(\)/g) || []).length >= 4, String((msrc.match(/rawCustomList\(\)/g) || []).length));

/* 6. 入口拦截仍然拦得住（新输入不能撞保留名） */
let threw = false;
try { rulesets.validateCustom({ name: RESERVED, out: 'proxy', rules: 'DOMAIN-SUFFIX,x.example' }); } catch (_) { threw = true; }
check('新建/改名撞保留名 → 入口就拒绝', threw);
let threw2 = false;
try { rulesets.validateCustom({ name: BAD, out: 'proxy', rules: 'DOMAIN-SUFFIX,x.example' }); } catch (_) { threw2 = true; }
check('新建/改名含非法字符 → 入口就拒绝', threw2);

fs.rmSync(TMP, { recursive: true, force: true });
console.log('\ncustom-ruleset-reserved：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项）');
process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => { console.log('  [FAIL] 自检自身异常：' + ((e && e.stack) || e)); process.exit(1); });