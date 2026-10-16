#!/usr/bin/env node
// Polaris 发版「防重复发布」守卫（独立可跑，纯 Node，无第三方依赖）。
//
// 背景：2026-10-16 实测到同一目标 SHA 被三个执行者各发一条 workflow_dispatch run
// （重复发布风险）。平台侧 concurrency 只能把同一 group 串行化，挡不住
// 「不同 SHA 发同一个 tag」或「手动指定 tag 与构建 SHA 不一致」，所以必须在
// release-precheck 最前面做 fail-closed 判定。
//
// 两条判定（任一不通过即输出 {"ok":false,"reason":"..."} 并 exit 1）：
//   (a) 同一 SHA 已有在跑的发版 run：
//       GET /repos/{repo}/actions/runs?head_sha=<sha>&status=in_progress|queued&event=workflow_dispatch
//       — 必须排除自己（github.run_id）：自己这条 run 执行时就是 in_progress，不排除必然自锁死。
//       — 必须限定 event=workflow_dispatch：否则会撞上 push 触发的 ci.yml/ci-windows.yml。
//   (b) 目标 tag 的 Release 已存在且 draft=false（=已发布）：默认拒绝。
//       仅当 allowRepublish=true（人工有意识地重发同一 tag）时放行。
//
// 用法（workflow 内，环境变量）：
//   GH_TOKEN       必填，读 Actions/Release（workflow 里用 ${{ secrets.GITHUB_TOKEN }}，
//                  权限 actions:read + contents:read）
//   REPO           仓库，默认 ipevel/polaris
//   SHA            本次构建提交（github.sha）
//   RUN_ID         本次 run id（github.run_id），用于排除自己
//   ALLOW_REPUBLISH  'true' 才有意识地放行已发布 Release 的重发，其余一律视为 false
//   RELEASE_TAG    显式 tag；为空时用 VERSION_NAME 拼 v<版本>
//   VERSION_NAME   versionName 输入
//
// 任何读取失败（网络异常 / 非 2xx）都按 fail-closed 处理为 ok=false；
// 只有「确实读到了、且判定冲突」才放行。纯函数 evaluateGuard 供
// tools/release-duplicate-guard.test.mjs 用假数据做夹具测试。

import { pathToFileURL } from 'node:url';

const API = 'https://api.github.com';

// 发版 run 的「在跑」状态：queued 还没起、in_progress 正在跑，两者都算占了位置。
export const IN_FLIGHT_STATUSES = ['in_progress', 'queued'];

// 目标发布 tag：显式 releaseTag 优先，否则 v<versionName>。
export function chooseTargetTag({ releaseTag, versionName } = {}) {
  const explicit = String(releaseTag ?? '').trim();
  if (explicit) return explicit;
  const version = String(versionName ?? '').trim();
  return version ? 'v' + version : '';
}

// 只看「workflow_dispatch 触发、状态在跑、且不是自己」的 run。
export function findConflictingRuns(runs, ownRunId) {
  const own = String(ownRunId ?? '');
  return (Array.isArray(runs) ? runs : []).filter((run) => {
    if (!run || typeof run !== 'object') return false;
    if (run.event !== 'workflow_dispatch') return false;
    if (!IN_FLIGHT_STATUSES.includes(run.status)) return false;
    return String(run.id ?? '') !== own;
  });
}

// 纯判定函数：不碰网络，只吃已取回的 JSON，便于夹具测试覆盖四格。
export function evaluateGuard({
  runs,
  release,
  allowRepublish = false,
  ownRunId = '',
  targetTag = '',
  sha = '',
} = {}) {
  const conflicts = findConflictingRuns(runs, ownRunId);
  if (conflicts.length > 0) {
    const ids = conflicts
      .map((r) => 'run_id=' + r.id + '（status=' + r.status + ', created_at=' + (r.created_at ?? 'n/a') + '）')
      .join('；');
    return {
      ok: false,
      reason:
        '同一提交 ' + (sha || '<sha>') + ' 已有发版流水线在跑，拒绝重复发布：' + ids +
        '。请等它跑完；若确认它已失效，请先取消那条 run 再重发。',
    };
  }

  const published = release !== null && typeof release === 'object' && release.draft === false;
  if (published) {
    if (allowRepublish === true) {
      return {
        ok: true,
        reason: '目标 tag ' + (targetTag || '<tag>') + ' 的 Release 已发布，但 allowRepublish=true，按人工有意重发放行。',
      };
    }
    return {
      ok: false,
      reason:
        '目标 tag ' + (targetTag || '<tag>') + ' 的 Release 已存在且已发布（draft=false），拒绝重复发布；' +
        '若确实要有意识地重发同一 tag，请把 allowRepublish 置为 true。',
    };
  }

  return {
    ok: true,
    reason: '未发现同一提交的在跑发版 run，目标 tag ' + (targetTag || '<tag>') + ' 也尚无已发布 Release，允许继续。',
  };
}

async function apiGet(url, token) {
  return fetch(url, {
    headers: {
      accept: 'application/vnd.github+json',
      authorization: 'Bearer ' + token,
      'x-github-api-version': '2022-11-28',
      'user-agent': 'polaris-release-duplicate-guard',
    },
  });
}

async function main() {
  const token = String(process.env.GH_TOKEN || '');
  const repo = String(process.env.REPO || 'ipevel/polaris');
  const sha = String(process.env.SHA || '').trim();
  const ownRunId = String(process.env.RUN_ID || '').trim();
  const allowRepublish = String(process.env.ALLOW_REPUBLISH || '').trim().toLowerCase() === 'true';
  const targetTag = chooseTargetTag({
    releaseTag: process.env.RELEASE_TAG,
    versionName: process.env.VERSION_NAME,
  });

  const fail = (reason) => {
    console.log(JSON.stringify({ ok: false, reason }));
    process.exit(1);
  };

  if (!token) return fail('缺少 GH_TOKEN，无法读取 Actions/Release（fail-closed，不放行）。');
  if (!sha) return fail('缺少 SHA，无法判定同一提交是否已有在跑的发版 run（fail-closed，不放行）。');
  if (!ownRunId) return fail('缺少 RUN_ID，无法排除自己这条 run（fail-closed，不放行）。');
  if (!targetTag) return fail('releaseTag 与 versionName 均为空，无法确定目标发布 tag（fail-closed，不放行）。');

  // (a) 同 SHA 在跑的发版 run（限定 workflow_dispatch，服务端 + 本地双重过滤）。
  const runs = [];
  for (const status of IN_FLIGHT_STATUSES) {
    const url =
      API + '/repos/' + repo + '/actions/runs' +
      '?head_sha=' + encodeURIComponent(sha) +
      '&status=' + status +
      '&event=workflow_dispatch' +
      '&per_page=100';
    let res;
    try {
      res = await apiGet(url, token);
    } catch (err) {
      return fail('读取 actions runs 失败（status=' + status + '，fail-closed，不放行）：' + (err && err.message ? err.message : err));
    }
    if (!res.ok) {
      return fail('读取 actions runs 失败（status=' + status + '，HTTP ' + res.status + '，fail-closed，不放行）。');
    }
    let payload;
    try {
      payload = await res.json();
    } catch (err) {
      return fail('解析 actions runs 响应失败（status=' + status + '，fail-closed，不放行）：' + (err && err.message ? err.message : err));
    }
    if (Array.isArray(payload && payload.workflow_runs)) {
      runs.push(...payload.workflow_runs);
    }
  }

  // (b) 目标 tag 的 Release：404 = 不存在（放行），2xx = 取到后看 draft，其余一律 fail-closed。
  let release = null;
  const releaseUrl = API + '/repos/' + repo + '/releases/tags/' + encodeURIComponent(targetTag);
  let relRes;
  try {
    relRes = await apiGet(releaseUrl, token);
  } catch (err) {
    return fail('读取 Release 失败（fail-closed，不放行）：' + (err && err.message ? err.message : err));
  }
  if (relRes.status === 404) {
    release = null;
  } else if (relRes.ok) {
    try {
      release = await relRes.json();
    } catch (err) {
      return fail('解析 Release 响应失败（fail-closed，不放行）：' + (err && err.message ? err.message : err));
    }
  } else {
    return fail('读取 Release 失败（HTTP ' + relRes.status + '，fail-closed，不放行）。');
  }

  const verdict = evaluateGuard({ runs, release, allowRepublish, ownRunId, targetTag, sha });
  console.log(JSON.stringify(verdict));
  process.exit(verdict.ok ? 0 : 1);
}

const invokedDirectly = (() => {
  try {
    return Boolean(process.argv[1]) && import.meta.url === pathToFileURL(process.argv[1]).href;
  } catch {
    return false;
  }
})();

if (invokedDirectly) {
  main().catch((err) => {
    console.log(
      JSON.stringify({
        ok: false,
        reason: '守卫异常退出（fail-closed，不放行）：' + (err && err.stack ? err.stack : String(err)),
      }),
    );
    process.exit(1);
  });
}
