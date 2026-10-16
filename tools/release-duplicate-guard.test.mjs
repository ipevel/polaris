#!/usr/bin/env node
// 夹具测试：tools/release-duplicate-guard.mjs 的纯判定函数 evaluateGuard。
// 纯 Node，无第三方依赖，不联网、不读环境变量。
//   node tools/release-duplicate-guard.test.mjs
// 覆盖四格：
//   ① 列表里只有自己            → 通过
//   ② 列表里有别人的第二条在跑   → 拒绝
//   ③ target Release 已发布     → 拒绝
//   ④ allowRepublish=true       → 放行
// 另加三条边界（push 触发的 CI 不算冲突 / draft 不算已发布 / (a) 优先于 (b)）。

import assert from 'node:assert/strict';
import {
  evaluateGuard,
  chooseTargetTag,
  findConflictingRuns,
  IN_FLIGHT_STATUSES,
} from './release-duplicate-guard.mjs';

const SHA = 'a'.repeat(40);
const OWN_RUN_ID = '1234567890';

const selfRun = { id: 1234567890, event: 'workflow_dispatch', status: 'in_progress', head_sha: SHA };
const otherRun = { id: 987654321, event: 'workflow_dispatch', status: 'queued', head_sha: SHA, created_at: '2026-10-16T05:00:00Z' };
const pushRun = { id: 555000111, event: 'push', status: 'in_progress', head_sha: SHA };
const publishedRelease = { tag_name: 'v1.13.0', draft: false };
const draftRelease = { tag_name: 'v1.13.0', draft: true };

const base = { runs: [selfRun], release: null, allowRepublish: false, ownRunId: OWN_RUN_ID, targetTag: 'v1.13.0', sha: SHA };

const cases = [
  { name: '① 列表里只有自己 → 通过', patch: {}, expected: true },
  { name: '② 列表里有别人的第二条在跑 → 拒绝', patch: { runs: [selfRun, otherRun] }, expected: false },
  { name: '③ target Release 已存在且已发布 → 拒绝', patch: { release: publishedRelease }, expected: false },
  { name: '④ 带 allowRepublish=true → 放行', patch: { release: publishedRelease, allowRepublish: true }, expected: true },
  { name: '⑤ push 触发的在跑 run 不算冲突 → 通过', patch: { runs: [selfRun, pushRun] }, expected: true },
  { name: '⑥ Release 是 draft（未发布）→ 通过', patch: { release: draftRelease }, expected: true },
  { name: '⑦ 同时在跑 + 已发布 → 拒绝（(a) 优先）', patch: { runs: [selfRun, otherRun], release: publishedRelease }, expected: false },
];

let failed = 0;
for (const c of cases) {
  const result = evaluateGuard(Object.assign({}, base, c.patch));
  const pass = result.ok === c.expected;
  console.log((pass ? 'PASS' : 'FAIL') + ' ' + c.name + ' -> ' + JSON.stringify(result));
  if (!pass) {
    failed += 1;
    console.log('  期望 ok=' + c.expected + '，实际 ok=' + result.ok);
  }
}

// 辅助纯函数断言
assert.equal(chooseTargetTag({ versionName: '1.13.0' }), 'v1.13.0');
assert.equal(chooseTargetTag({ releaseTag: 'v1.13.0-rc1', versionName: '1.13.0' }), 'v1.13.0-rc1');
assert.equal(chooseTargetTag({ releaseTag: '  ', versionName: '' }), '');
assert.deepEqual(IN_FLIGHT_STATUSES, ['in_progress', 'queued']);
assert.equal(findConflictingRuns([selfRun, otherRun, pushRun], OWN_RUN_ID).length, 1);
assert.equal(findConflictingRuns([selfRun, otherRun, pushRun], OWN_RUN_ID)[0].id, otherRun.id);

console.log('');
console.log('夹具用例：' + (cases.length - failed) + '/' + cases.length + ' 通过');
if (failed > 0) process.exit(1);
