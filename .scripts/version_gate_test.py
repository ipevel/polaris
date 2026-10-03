# -*- coding: utf-8 -*-
"""version_gate.py 的自测：在临时 git 仓库里复现历史违规，确认门禁真的能拦住。

用法：python3 <本文件所在目录>/version_gate_test.py

不用 pytest，只依赖标准库与 git，好让四个仓库都能在 CI 里直接跑。存在的理由是
门禁有过一次「看起来绿、其实一个提交都没扫」的静默失效（历史审计走 JSON 解析
却拿到了 .gradle.kts 文本，异常被 except 吞掉）——门禁自己坏掉时不会有人发现，
所以它必须自己验证自己。
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
VG = os.path.join(HERE, "version_gate.py")
PY = sys.executable
FAILURES = []


def run(*args, cwd=None):
    p = subprocess.run(args, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       text=True, encoding="utf-8", errors="replace")
    return p.returncode, p.stdout


def sh(repo, *args):
    return run("git", "-C", repo, *args)


def expect(name, cond, detail=""):
    if not cond:
        FAILURES.append(name)
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}")
    if not cond:
        for line in detail.strip().splitlines():
            print(f"         | {line}")


def gate(repo, *args):
    return run(PY, VG, "--repo", repo, *args)


def set_versions(repo, version, files=("theme.json", "package.json")):
    for f in files:
        with open(os.path.join(repo, f), "w", encoding="utf-8") as h:
            h.write(json.dumps({"version": version}))


def commit(repo, message, version=None):
    if version:
        set_versions(repo, version)
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", message)


def new_repo(prefix):
    repo = tempfile.mkdtemp(prefix=prefix)
    sh(repo, "init", "-q")
    sh(repo, "config", "user.email", "t@t")
    sh(repo, "config", "user.name", "t")
    sh(repo, "config", "tag.gpgsign", "false")
    return repo


# --------------------------------------------------------------------------- #
print("\n=== A. JSON 模式：feat 却只升 PATCH（复现 monitor-theme v1.9.0->v1.9.1）===")
repo = new_repo("vg_a_")
commit(repo, "init 1.0.0", "1.0.0")
sh(repo, "tag", "-a", "v1.0.0", "-m", "1.0.0")

commit(repo, "feat: 卡片加进度条与图标", "1.0.1")
sh(repo, "tag", "-a", "v1.0.1", "-m", "1.0.1")

rc, out = gate(repo, "--tag", "v1.0.1",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version",
               "--check-commits")
print(out)
expect("A1 feat 只升 PATCH 被拦截", rc == 1 and "必须递增 MINOR" in out, out)
expect("A2 指出了具体的 feat 提交", "feat: 卡片加进度条与图标" in out, out)

commit(repo, "chore(release): 1.1.0", "1.1.0")
sh(repo, "tag", "-a", "v1.1.0", "-m", "1.1.0")
rc, out = gate(repo, "--tag", "v1.1.0",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version",
               "--check-commits")
print(out)
expect("A3 改为升 MINOR 后通过", rc == 0, out)

# --------------------------------------------------------------------------- #
print("\n=== B. 声明点漂移 / tag 不一致 / 非法版本号 ===")
with open(os.path.join(repo, "package.json"), "w", encoding="utf-8") as h:
    h.write(json.dumps({"version": "1.2.0"}))
commit(repo, "chore: 只改了 package.json")
rc, out = gate(repo, "--tag", "v1.1.0",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
print(out)
expect("B1 两个声明点漂移被拦截", rc == 1 and "版本声明点漂移" in out, out)

rc, out = gate(repo, "--tag", "v9.9.9",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
expect("B2 tag 与声明版本不一致被拦截", rc == 1 and "不一致" in out, out)

set_versions(repo, "1.01.0")
commit(repo, "chore: 写了前导零")
rc, out = gate(repo, "--tag", "v1.01.0",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
print(out)
expect("B3 带前导零的版本号被拦截", rc == 1 and "不是合法的 SemVer" in out, out)

rc, out = gate(repo, "--tag", "1.1.0",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
expect("B4 缺 v 前缀的 tag 被拦截", rc == 1 and "不符合 v 前缀" in out, out)

rc, out = gate(repo, "--tag", "v1.1",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
expect("B5 只有两段的 tag 被拦截", rc == 1, out)

# --------------------------------------------------------------------------- #
print("\n=== C. Android 模式：versionCode 不递增 ===")
repo2 = new_repo("vg_c_")
os.makedirs(os.path.join(repo2, "app"))
GRADLE = 'android {\n    defaultConfig {\n        versionCode = %d\n        versionName = "%s"\n    }\n}\n'
for code, ver, msg in ((1, "0.1.0", "init"), (1, "0.1.0", "fix: 修 bug"),
                       (1, "0.1.1", "chore(release): 0.1.1")):
    with open(os.path.join(repo2, "app", "build.gradle.kts"), "w", encoding="utf-8") as h:
        h.write(GRADLE % (code, ver))
    sh(repo2, "add", "-A")
    sh(repo2, "commit", "-qm", msg)
    sh(repo2, "tag", "-a", "v" + ver, "-m", ver)

rc, out = gate(repo2, "--tag", "v0.1.1", "--android-gradle", "app/build.gradle.kts",
               "--version-code", "1", "--check-commits")
print(out)
expect("C1 versionCode 未递增被拦截", rc == 1 and "应用商店会拒绝上传" in out, out)

rc, out = gate(repo2, "--tag", "v0.1.1", "--android-gradle", "app/build.gradle.kts",
               "--version-code", "2", "--check-commits")
print(out)
expect("C2 versionCode 递增后通过", rc == 0, out)

os.makedirs(os.path.join(repo2, "app2"))
with open(os.path.join(repo2, "app2", "build.gradle.kts"), "w", encoding="utf-8") as h:
    h.write('fun slteValue(key: String): String? = null\n'
            'val slteVersionCode = slteValue("POLARIS_VERSION_CODE")?.toIntOrNull() ?: 9\n'
            'val slteVersionName = slteValue("POLARIS_VERSION_NAME") ?: "1.5.18"\n')
with open(os.path.join(repo2, "_t.py"), "w", encoding="utf-8") as h:
    h.write('import sys; sys.path.insert(0, r"%s")\n'
            'import version_gate as vg\n'
            'print(vg.read_android_versions(open(r"%s", encoding="utf-8").read()))\n'
            % (HERE, os.path.join(repo2, "app2", "build.gradle.kts")))
rc, out = run(PY, os.path.join(repo2, "_t.py"))
expect("C3 兼容 slte 的 POLARIS_ 环境变量写法", "('1.5.18', 9)" in out, out)

# --------------------------------------------------------------------------- #
print("\n=== D. 历史审计：版本号被消耗却从未发布（复现 monitor-theme 1.0.1/1.0.2）===")
repo3 = new_repo("vg_d_")
for ver in ("1.0.0", "1.0.1", "1.0.2", "1.0.3"):
    commit(repo3, "theme.json version %s" % ver, ver)
    if ver != "1.0.2":
        sh(repo3, "tag", "-a", "v" + ver, "-m", ver)

rc, out = gate(repo3, "--audit-history", "theme.json:version")
print(out)
expect("D1 无豁免时 1.0.2 被判为欠账", rc == 1 and "1.0.2 曾写入" in out, out)

allow = os.path.join(repo3, "allow.txt")
with open(allow, "w", encoding="utf-8") as h:
    h.write("# 历史上 1.0.1/1.0.2 未发布，主题内容已并入 1.0.3\n1.0.2\n")
rc, out = gate(repo3, "--audit-history", "theme.json:version", "--audit-allowlist", allow)
print(out)
expect("D2 豁免后降级为警告并通过", rc == 0 and "已知历史欠账" in out, out)

# D3：Gradle 形态的历史审计。slte 的 workflow 就是拿 app/build.gradle.kts 去审计的，
# 早先 read_json_version_at 会在这条路径上抛 JSONDecodeError 并被静默 continue，
# 于是审计「通过」但实际什么都没扫——这条用例专门锁住那个静默失效。
repo3g = new_repo("vg_d3_")
os.makedirs(os.path.join(repo3g, "app"))


def set_gradle(repo, version, code):
    with open(os.path.join(repo, "app", "build.gradle.kts"), "w", encoding="utf-8") as h:
        h.write('android {\n    defaultConfig {\n'
                '        versionCode = %d\n        versionName = "%s"\n    }\n}\n'
                % (code, version))


for ver, code in (("0.1.0", 1), ("0.2.0", 2), ("0.3.0", 3)):
    set_gradle(repo3g, ver, code)
    sh(repo3g, "add", "-A")
    sh(repo3g, "commit", "-qm", "versionName " + ver)
    if ver != "0.2.0":
        sh(repo3g, "tag", "-a", "v" + ver, "-m", ver)

rc, out = gate(repo3g, "--audit-history", "app/build.gradle.kts:versionName")
print(out)
expect("D3 Gradle 形态的历史审计抓出 0.2.0 欠账", rc == 1 and "0.2.0 曾写入" in out, out)

allow3g = os.path.join(repo3g, "allow.txt")
with open(allow3g, "w", encoding="utf-8") as h:
    h.write("0.2.0\n")
rc, out = gate(repo3g, "--audit-history", "app/build.gradle.kts:versionName",
               "--audit-allowlist", allow3g)
print(out)
expect("D4 Gradle 形态豁免后通过", rc == 0 and "已知历史欠账" in out, out)

rc, out = gate(repo3g, "--android-gradle", "app/build.gradle.kts", "--print-version")
expect("D5 --print-version 从 gradle 读出当前版本号", out.strip() == "0.3.0", out)

# --------------------------------------------------------------------------- #
print("\n=== E. 预发布优先级 ===")
set_versions(repo, "1.2.0-rc.1")
commit(repo, "chore: 预发布 1.2.0-rc.1")
rc, out = gate(repo, "--tag", "v1.2.0-rc.1",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
print(out)
expect("E1 1.2.0-rc.1 高于 1.1.0，判定为递增", rc == 0, out)

set_versions(repo, "1.1.0-rc.1")
commit(repo, "chore: 回头补一个 rc")
rc, out = gate(repo, "--tag", "v1.1.0-rc.1",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
print(out)
expect("E2 回填预发布号被判为版本回退", rc == 1 and "版本号回退" in out, out)

set_versions(repo, "1.1.0")
commit(repo, "chore: 回到 1.1.0")
rc, out = gate(repo, "--tag", "v1.1.0",
               "--version-file", "theme.json:version",
               "--version-file", "package.json:version")
print(out)
expect("E3 重跑同一版本只告警不阻断", rc == 0 and "已存在且是 HEAD 的祖先" in out, out)

# --------------------------------------------------------------------------- #
print("\n=== F. 0.y.z 宽容模式（独立干净仓库）===")
repo4 = new_repo("vg_f_")
for ver, msg in (("0.4.0", "init 0.4.0"), ("0.4.1", "feat: 图标全面换 SVG sprite")):
    commit(repo4, msg, ver)
    sh(repo4, "tag", "-a", "v" + ver, "-m", ver)
common = ("--tag", "v0.4.1", "--version-file", "theme.json:version",
          "--version-file", "package.json:version", "--check-commits")
rc, out = gate(repo4, *common)
print(out)
expect("F1 0.y.z 区间 feat 只升 PATCH 默认失败", rc == 1 and "必须递增 MINOR" in out, out)
rc, out = gate(repo4, *common, "--zero-major-lenient")
print(out)
expect("F2 开启 --zero-major-lenient 后降级为警告", rc == 0 and "初始开发期" in out, out)

# --------------------------------------------------------------------------- #
print("\n=== G. 逃生阀 ===")
rc, out = gate(repo4, *common, "--mode", "warn")
print(out)
expect("G1 --mode warn 把失败降级为不阻断", rc == 0 and "降级为警告" in out, out)
rc, out = gate(repo4, *common, "--mode", "off")
print(out)
expect("G2 --mode off 完全跳过", rc == 0 and "已被 --mode off 跳过" in out, out)

for d in (repo, repo2, repo3, repo3g, repo4):
    shutil.rmtree(d, ignore_errors=True)

print("\n" + "=" * 60)
if FAILURES:
    print("失败用例: %s" % ", ".join(FAILURES))
    sys.exit(1)
print("全部用例通过")
