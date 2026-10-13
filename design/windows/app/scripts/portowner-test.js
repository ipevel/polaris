'use strict';
/**
 * 端口占用者探测 / 关闭 自测（离线，不联网）。
 *
 * 背景：1.11.4 真机日志里出现过
 *   `close port owner: pid=16256 name= closed=false forced=true`
 * —— **认不出占用者是谁，却把它强杀了**。用户 2026-10-14 拍板：
 *   探测不出是谁，就不许动它；宁可提示用户手动处理，也不能误杀重要进程。
 *
 * 这个测试盯四件事：
 *  1. 决策函数 closeDecision 的四条分支（无效 PID / 自己 / 认不出 / 认得出）；
 *  2. taskkill 的中英文回话能被翻成机器可读的原因（访问拒绝 / 找不到 / 只能强制终止）；
 *  3. **铁律**：认不出是谁时 close() 一步都不走，目标进程必须还活着；
 *  4. 正常路径仍然真的能关掉（用自己生的 node 子进程当靶子，不打别人）。
 *
 * 用真机 Windows 工具（netstat / tasklist / taskkill），所以只在 Windows 上有意义；
 * 非 Windows 直接跳过（门禁跑在 windows runner 上，不会因此变绿）。
 */
const assert = require('assert');
const net = require('net');
const path = require('path');
const { spawn } = require('child_process');

const appRoot = path.join(__dirname, '..');
const portowner = require(path.join(appRoot, 'electron', 'net', 'portowner.js'));

let pass = 0;
let fail = 0;

function check(name, fn) {
  try {
    fn();
    pass += 1;
    console.log(`  ok   ${name}`);
  } catch (e) {
    fail += 1;
    console.log(`  FAIL ${name}\n       ${e && e.message}`);
  }
}

async function checkAsync(name, fn) {
  try {
    await fn();
    pass += 1;
    console.log(`  ok   ${name}`);
  } catch (e) {
    fail += 1;
    console.log(`  FAIL ${name}\n       ${e && e.message}`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------- 1. 决策函数 ---------- */

check('closeDecision：PID 为 0/空 → invalid（不许动手）', () => {
  assert.deepStrictEqual(portowner.closeDecision(0, 999, 'chrome.exe'), { allow: false, reason: 'invalid' });
  assert.deepStrictEqual(portowner.closeDecision(null, 999, 'chrome.exe'), { allow: false, reason: 'invalid' });
  assert.deepStrictEqual(portowner.closeDecision('abc', 999, 'chrome.exe'), { allow: false, reason: 'invalid' });
});

check('closeDecision：目标是自己 → self（不许动手）', () => {
  assert.deepStrictEqual(portowner.closeDecision(1234, 1234, 'Polaris.exe'), { allow: false, reason: 'self' });
});

check('closeDecision：拿不到进程名 → unidentified（不许动手，这是铁律）', () => {
  assert.deepStrictEqual(portowner.closeDecision(1234, 999, ''), { allow: false, reason: 'unidentified' });
  assert.deepStrictEqual(portowner.closeDecision(1234, 999, '   '), { allow: false, reason: 'unidentified' });
  assert.deepStrictEqual(portowner.closeDecision(1234, 999, null), { allow: false, reason: 'unidentified' });
  assert.deepStrictEqual(portowner.closeDecision(1234, 999, undefined), { allow: false, reason: 'unidentified' });
});

check('closeDecision：认得出、不是自己 → 放行', () => {
  assert.deepStrictEqual(portowner.closeDecision(1234, 999, 'clash-verge.exe'), { allow: true, reason: '' });
  assert.deepStrictEqual(portowner.closeDecision('1234', '999', 'clash-verge.exe'), { allow: true, reason: '' });
});

/* ---------- 2. taskkill 回话翻译 ---------- */

check('killReason：中文「拒绝访问」→ access-denied', () => {
  const zh = '错误: 无法终止 PID 为 16256 的进程。\n原因: 拒绝访问。';
  assert.strictEqual(portowner.killReason(zh, 5), 'access-denied');
});

check('killReason：英文「Access is denied」→ access-denied', () => {
  assert.strictEqual(portowner.killReason('ERROR: The process "x.exe" with PID 16256 could not be terminated. Reason: Access is denied.', 5), 'access-denied');
});

check('killReason：找不到进程 → not-found', () => {
  assert.strictEqual(portowner.killReason('错误: 没有找到进程 "foo.exe"。', 128), 'not-found');
  assert.strictEqual(portowner.killReason('ERROR: The process "foo.exe" not found.', 128), 'not-found');
});

check('killReason：「只能强制终止」→ needs-force（不是失败，是优雅那条路不适用）', () => {
  assert.strictEqual(portowner.killReason('错误: 无法终止 PID 为 1 的进程。\n原因: 这个进程只能强制终止。', 5), 'needs-force');
  assert.strictEqual(portowner.killReason('Reason: This process can only be terminated forcefully.', 5), 'needs-force');
});

check('killReason：退出码 0 且没话说 → 成功；非 0 且不认识 → failed', () => {
  assert.strictEqual(portowner.killReason('', 0), '');
  assert.strictEqual(portowner.killReason('', 1), 'failed');
  assert.strictEqual(portowner.killReason('something weird', 1), 'failed');
});

/* ---------- 3/4. 真跑：真探测、真关 ---------- */

/** 挑一个当前空闲的端口（绑 0 拿到，再立刻释放） */
function freePort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.on('error', reject);
    s.listen(0, '127.0.0.1', () => {
      const p = s.address().port;
      s.close(() => resolve(p));
    });
  });
}

async function main() {
  if (process.platform !== 'win32') {
    console.log('端口占用自测：非 Windows，跳过');
    process.exit(0);
  }

  const port = await freePort();
  const child = spawn(process.execPath, ['-e', `require('net').createServer().listen(${port},'127.0.0.1');setInterval(()=>{},1e9);`], {
    stdio: 'ignore', windowsHide: true,
  });
  let cleaned = false;
  const cleanup = () => {
    if (cleaned) return;
    cleaned = true;
    try { child.kill('SIGKILL'); } catch (_) {}
  };
  process.on('exit', cleanup);

  try {
    // 等子进程真的把端口占上
    let busy = null;
    for (let i = 0; i < 30 && !busy; i++) {
      await sleep(200);
      const o = portowner.owner(port);
      if (o.busy) busy = o;
    }
    assert.ok(busy, `子进程没能占上 ${port} 端口，自测前提不成立`);

    await checkAsync(`owner(${port})：查得出占用者，且认得出名字`, async () => {
      assert.strictEqual(busy.busy, true);
      assert.strictEqual(busy.port, port);
      assert.strictEqual(busy.pid, child.pid, `PID 应为子进程 ${child.pid}，实际 ${busy.pid}`);
      assert.strictEqual(busy.identified, true, `没认出进程名（name=${busy.name}）`);
      assert.ok(/node/i.test(busy.name), `进程名应含 node，实际 ${busy.name}`);
      assert.strictEqual(busy.reason, '');
    });

    check('owner(0)：无效端口 → 不 busy', () => {
      assert.deepStrictEqual(portowner.owner(0), { busy: false, port: 0 });
    });

    await checkAsync('铁律：认不出是谁（nameForTest=空）→ 拒绝动手，靶子必须还活着', async () => {
      const r = await portowner.close(child.pid, { nameForTest: '', graceMs: 300 });
      assert.strictEqual(r.closed, false, '不该关成功');
      assert.strictEqual(r.forced, false, '更不该强杀');
      assert.strictEqual(r.reason, 'unidentified', `reason 应为 unidentified，实际 ${r.reason}`);
      assert.strictEqual(portowner.alive(child.pid), true, '靶子被误杀了 —— 铁律被破坏');
      // 端口还应该被占着（说明一步都没走）
      assert.strictEqual(portowner.owner(port).busy, true, '占用消失了，说明真的动了它');
    });

    await checkAsync('close(0) / close(自己)：拒绝，且自己没被关掉', async () => {
      const a = await portowner.close(0, { graceMs: 200 });
      assert.strictEqual(a.reason, 'invalid');
      assert.strictEqual(a.closed, false);
      const b = await portowner.close(process.pid, { graceMs: 200 });
      assert.strictEqual(b.reason, 'self');
      assert.strictEqual(b.closed, false);
      assert.strictEqual(portowner.alive(process.pid), true);
    });

    await checkAsync('close(子进程)：认得出 → 真的关掉（优雅不成才强杀）', async () => {
      const r = await portowner.close(child.pid, { graceMs: 800 });
      assert.strictEqual(r.closed, true, `没关掉（reason=${r.reason}）`);
      assert.ok(/node/i.test(r.name), `进程名应含 node，实际 ${r.name}`);
      assert.strictEqual(r.reason, '', `成功时 reason 应为空，实际 ${r.reason}`);
      assert.strictEqual(portowner.alive(child.pid), false, '进程还活着');
      // 内核/界面看的是端口有没有松开；等它退出后端口应不再被占
      let stillBusy = true;
      for (let i = 0; i < 15 && stillBusy; i++) {
        await sleep(200);
        stillBusy = portowner.owner(port).busy;
      }
      assert.strictEqual(stillBusy, false, `${port} 端口仍被占`);
    });
  } finally {
    cleanup();
  }

  console.log(`\n端口占用自测：通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail ? 1 : 0);
}

main().catch((e) => {
  console.error('端口占用自测崩了：' + (e && e.stack ? e.stack : e));
  process.exit(1);
});
