#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SemVer 版本门禁 —— 供 CI 在发布前调用。

用途：把「版本号规范」从口头约定变成可执行的门禁，拦截以下问题：
  1. 版本号不是合法的 X.Y.Z（缺段、前导零、非法字符）
  2. git tag 与代码里声明的版本号不一致
  3. 多个版本声明点之间漂移（例如 theme.json 与 package.json 不一致）
  4. 新版本没有严格大于上一个已发布版本（导致版本号被跳过或回退）
  5. Android versionCode 没有严格递增（导致应用商店拒绝上传）
  6. 提交类型与递增位不匹配（区间内有 feat 却只升 PATCH；有破坏性变更却没升 MAJOR）

用法（JSON 声明点模式，适用于 theme.json / package.json）：
  python3 scripts/version_gate.py --tag v1.11.0 \
      --version-file theme.json:version \
      --version-file package.json:version \
      --check-commits

用法（Android 模式，适用于 app/build.gradle.kts）：
  python3 .scripts/version_gate.py --tag v1.5.18 \
      --android-gradle app/build.gradle.kts --version-name 1.5.18 --version-code 51 \
      --check-commits

  说明：CI 用 workflow 输入注入版本号时，用 --version-name 传入本次发布的版本号
  （文件里的默认值只作本地构建回退）；不传则读文件里的默认值。

附加：历史审计，揪出「版本号被写进代码却从未发布」的历史欠账。
  python3 scripts/version_gate.py --audit-history theme.json:version \
      --audit-allowlist scripts/version-audit-allowlist.txt

退出码：0 = 通过（可能有警告）；1 = 有阻断级问题。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

try:  # 避免 Windows 控制台(cp936)打印中文时崩溃
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # pragma: no cover - 老解释器忽略即可
    pass

# SemVer 2.0.0 官方正则：数字段不得有前导零，prerelease/build 不得为空段
SEMVER_RE = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-((?:[0-9A-Za-z-]+)(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+((?:[0-9A-Za-z-]+)(?:\.[0-9A-Za-z-]+)*))?$"
)

OK, WARN, FAIL = "OK", "WARN", "FAIL"
EXIT_OK, EXIT_FAIL = 0, 1


# --------------------------------------------------------------------------- #
# 基础设施
# --------------------------------------------------------------------------- #
class Report:
    """收集门禁结果；strict 模式下 WARN 也计入失败之外的展示，但只有 FAIL 阻断。"""

    def __init__(self) -> None:
        self.rows: list[tuple[str, str]] = []
        self.notes: list[str] = []

    def add(self, level: str, message: str) -> None:
        self.rows.append((level, message))

    def fail(self, message: str) -> None:
        self.add(FAIL, message)

    def warn(self, message: str) -> None:
        self.add(WARN, message)

    def ok(self, message: str) -> None:
        self.add(OK, message)

    def note(self, message: str) -> None:
        self.notes.append(message)

    @property
    def failed(self) -> bool:
        return any(level == FAIL for level, _ in self.rows)

    def render(self) -> None:
        for level, message in self.rows:
            print(f"  [{level:^4}] {message}")
        for message in self.notes:
            print(f"  [ .. ] {message}")
        if self.failed:
            print("\nSemVer 版本门禁未通过。")
        elif any(level == WARN for level, _ in self.rows):
            print("\nSemVer 版本门禁通过（有警告，不阻断发布）。")
        else:
            print("\nSemVer 版本门禁通过。")


def git(repo: str, *args: str) -> str:
    """执行 git 命令并返回 stdout；失败时抛 RuntimeError。"""
    proc = subprocess.run(
        ["git", "-C", repo, *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} 失败({proc.returncode}): {proc.stderr.strip()}"
        )
    return proc.stdout


# --------------------------------------------------------------------------- #
# SemVer 解析与优先级比较
# --------------------------------------------------------------------------- #
def parse_semver(value: str):
    """合法则返回 (major, minor, patch, prerelease|None)，非法返回 None。"""
    match = SEMVER_RE.match((value or "").strip())
    if not match:
        return None
    return (
        int(match.group(1)),
        int(match.group(2)),
        int(match.group(3)),
        match.group(4),
    )


def _cmp_prerelease(left, right) -> int:
    """SemVer §11.4：预发布版本优先级低于正式版本；逐段比较，数字段小于字母段。"""
    if left is None and right is None:
        return 0
    if left is None:
        return 1
    if right is None:
        return -1
    left_parts, right_parts = left.split("."), right.split(".")
    for a, b in zip(left_parts, right_parts):
        a_num, b_num = a.isdigit(), b.isdigit()
        if a_num and b_num:
            if int(a) != int(b):
                return -1 if int(a) < int(b) else 1
        elif a_num != b_num:
            return -1 if a_num else 1  # 纯数字标识符优先级更低
        elif a != b:
            return -1 if a < b else 1
    if len(left_parts) != len(right_parts):
        return -1 if len(left_parts) < len(right_parts) else 1
    return 0


def compare_versions(left: str, right: str) -> int:
    """按 SemVer 优先级比较：left < right 返回 -1，相等返回 0。"""
    a, b = parse_semver(left), parse_semver(right)
    if a is None or b is None:
        raise ValueError(f"无法比较非法版本号: {left!r} / {right!r}")
    for index in range(3):
        if a[index] != b[index]:
            return -1 if a[index] < b[index] else 1
    return _cmp_prerelease(a[3], b[3])


# --------------------------------------------------------------------------- #
# 读取版本声明点
# --------------------------------------------------------------------------- #
def read_json_version(path: str, key: str) -> str:
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    value = data.get(key)
    if not isinstance(value, str):
        raise ValueError(f"{path} 中缺少字符串字段 {key}")
    return value


def read_android_versions(text: str):
    """从 app/build.gradle.kts 解析 versionName / versionCode。

    兼容两种写法：
      val slteVersionName = slteValue("POLARIS_VERSION_NAME") ?: "1.5.17"   # slte
      versionName = "0.1.0"                                                 # 常规 Android 工程
    """
    name = None
    code = None
    match = re.search(r'POLARIS_VERSION_NAME[^\n]*?\?:\s*"([^"]+)"', text)
    if not match:
        match = re.search(r'\bversionName\s*=\s*"([^"]+)"', text)
    if match:
        name = match.group(1)
    match = re.search(r'POLARIS_VERSION_CODE[^\n]*?\?:\s*(\d+)', text)
    if not match:
        match = re.search(r"\bversionCode\s*=\s*(\d+)", text)
    if match:
        code = int(match.group(1))
    if name is None or code is None:
        raise ValueError("无法从该 gradle 文件解析出 versionName / versionCode")
    return name, code


# --------------------------------------------------------------------------- #
# tag 集合
# --------------------------------------------------------------------------- #
def collect_version_tags(repo: str):
    """返回 [(tag, version)]，按 SemVer 优先级升序，忽略非 v 前缀 / 非法版本号 tag。"""
    raw = git(repo, "tag", "--list").split()
    pairs = []
    for tag in raw:
        if not tag.startswith("v"):
            continue
        version = tag[1:]
        if parse_semver(version) is None:
            continue
        pairs.append((tag, version))
    pairs.sort(key=lambda item: _SortKey(item[1]))
    return pairs


class _SortKey:
    """让 sorted() 能按 SemVer 优先级而非字典序排序。"""

    __slots__ = ("value",)

    def __init__(self, value: str) -> None:
        self.value = value

    def __lt__(self, other: "_SortKey") -> bool:
        return compare_versions(self.value, other.value) < 0

    def __eq__(self, other: object) -> bool:
        return isinstance(other, _SortKey) and compare_versions(
            self.value, other.value
        ) == 0


def latest_release(tags):
    """已发布版本中的最大者；没有则返回 None。"""
    if not tags:
        return None
    return max(tags, key=lambda item: _SortKey(item[1]))


def previous_release(tags, target_version: str):
    """确定递增性检查的基线 tag。

    规则：新版本必须严格大于**所有**已发布版本。
      - 目标高于最新版      → 基线取最新版
      - 目标等于最新版      → 视为对该版本的重跑，基线取它的前一个
      - 目标低于最新版      → 返回最新版，由调用方报「版本回退」
    """
    newest = latest_release(tags)
    if newest is None:
        return None
    if compare_versions(target_version, newest[1]) > 0:
        return newest
    if compare_versions(target_version, newest[1]) == 0:
        lower = [item for item in tags if compare_versions(item[1], target_version) < 0]
        return lower[-1] if lower else None
    return newest


# --------------------------------------------------------------------------- #
# 提交类型检查
# --------------------------------------------------------------------------- #
BREAKING_RE = re.compile(r"^[\w]+(\([^)]*\))?!:")
FEAT_RE = re.compile(r"^feat(\([^)]*\))?!?:")


def commits_in_range(repo: str, base: str, head: str = "HEAD"):
    """返回 (subject, body) 列表，按时间正序，便于报错时展示。"""
    raw = git(repo, "log", "--reverse", "--format=%s%x1f%b%x1e", f"{base}..{head}")
    entries = []
    for chunk in raw.split("\x1e"):
        chunk = chunk.strip("\n")
        if not chunk.strip():
            continue
        subject, _, body = chunk.partition("\x1f")
        entries.append((subject.strip(), body.strip()))
    return entries


def check_commit_types(report, repo, base, new_version, zero_major_lenient) -> None:
    """区间内有破坏性变更必须升 MAJOR；有 feat 必须至少升 MINOR。"""
    old = parse_semver(base[1])
    new = parse_semver(new_version)
    if old is None or new is None:
        report.warn("无法解析上一版/当前版本，跳过提交类型检查。")
        return

    commits = commits_in_range(repo, base[0])
    if not commits:
        report.ok(f"{base[0]}..HEAD 无提交，跳过提交类型检查。")
        return

    breaking = [
        subject
        for subject, body in commits
        if BREAKING_RE.match(subject)
        or "BREAKING CHANGE:" in body
        or "BREAKING-CHANGE:" in body
    ]
    feats = [
        subject for subject, _ in commits if FEAT_RE.match(subject) and subject not in breaking
    ]

    if zero_major_lenient and old[0] == 0:
        report.warn(
            f"当前处于 0.y.z 初始开发期（{base[0]} → v{new_version}），提交类型与递增位"
            f"不匹配仅提示不阻断；稳定到 1.0.0 后本项将强制生效。"
        )
        for subject in (breaking + feats)[:10]:
            report.note(f"  · {subject}")
        return

    if breaking and new[0] == old[0]:
        report.fail(
            f"{base[0]}..HEAD 含 {len(breaking)} 个破坏性变更，但 MAJOR 仍为 {old[0]}："
            f"{new_version}。破坏性变更必须递增 MAJOR。"
        )
        for subject in breaking[:5]:
            report.note(f"  · {subject}")
    elif breaking:
        report.ok(f"检测到 {len(breaking)} 个破坏性变更，MAJOR 已由 {old[0]} 升至 {new[0]}。")

    if not breaking and feats and (new[0], new[1]) == (old[0], old[1]):
        report.fail(
            f"{base[0]}..HEAD 含 {len(feats)} 个 feat 提交，但 MINOR 仍为 {old[1]}："
            f"{new_version}。新增向后兼容功能必须递增 MINOR。"
        )
        for subject in feats[:5]:
            report.note(f"  · {subject}")
    elif feats and (new[0], new[1]) != (old[0], old[1]):
        report.ok(f"检测到 {len(feats)} 个 feat 提交，MINOR 已递增。")
    else:
        report.ok(f"{base[0]}..HEAD 共 {len(commits)} 个提交，无 feat / 破坏性变更标记。")


# --------------------------------------------------------------------------- #
# 历史审计：版本号被写进代码却从未发布
# --------------------------------------------------------------------------- #
def audit_history(repo: str, specs, allowlist_path, report) -> None:
    """扫描版本声明文件的历史，找出「代码里出现过、却从未打过 tag」的版本号。"""
    if not specs:
        return
    path, _, key = specs[0].partition(":")
    tagged = {version for _, version in collect_version_tags(repo)}
    if not tagged:
        report.warn("仓库没有任何合法版本 tag，跳过历史审计。")
        return
    newest = max(tagged, key=_SortKey)

    seen: dict[str, str] = {}
    for commit in git(repo, "log", "--format=%H", "--", path).split():
        try:
            value = read_version_at(repo, commit, path, key)
        except Exception:
            continue  # 该提交里文件不存在或解析失败，跳过
        if value is None or parse_semver(value) is None:
            continue
        if value in tagged or value in seen:
            continue
        summary = git(repo, "log", "-1", "--format=%h %s", commit).strip()
        seen[value] = summary

    allowed = load_allowlist(allowlist_path)
    leaked = {v: c for v, c in seen.items() if v not in allowed}
    waived = {v: c for v, c in seen.items() if v in allowed}

    for value, commit in sorted(waived.items(), key=lambda kv: _SortKey(kv[0])):
        report.warn(f"已知历史欠账（已在允许清单中）：{value} 未发布 —— {commit}")

    if not leaked:
        report.ok(f"历史审计通过：{path} 历史中出现过的版本号均已发布或有明确豁免。")
        return

    for value, commit in sorted(leaked.items(), key=lambda kv: _SortKey(kv[0])):
        if compare_versions(value, newest) > 0:
            report.note(f"  · {value} 出现在 {commit}（高于最新 tag v{newest}，属开发中版本，不计欠账）")
        else:
            report.fail(
                f"版本号 {value} 曾写入 {path}（{commit}）但从未发布为 tag —— "
                f"版本号被消耗，破坏可追溯性。"
            )


def read_json_version_at(repo: str, commit: str, path: str, key: str):
    raw = git(repo, "show", f"{commit}:{path}")
    data = json.loads(raw)
    value = data.get(key)
    return value if isinstance(value, str) else None


def read_version_at(repo: str, commit: str, path: str, key: str):
    """读取某个提交里版本声明文件的版本号，JSON 与 Gradle 两种形态都支持。"""
    if path.endswith((".kts", ".gradle")):
        return read_android_versions(git(repo, "show", f"{commit}:{path}"))[0]
    return read_json_version_at(repo, commit, path, key)


def load_allowlist(path):
    if not path or not os.path.exists(path):
        return set()
    values = set()
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.split("#", 1)[0].strip()
            if line:
                values.add(line)
    return values


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def run_release_checks(args) -> int:
    repo = os.path.abspath(args.repo)
    report = Report()
    tag = (args.tag or "").strip()
    version = tag[1:] if tag.startswith("v") else tag

    print(f">>> SemVer 版本门禁 [{os.path.basename(repo)}] tag={tag or '(未指定)'}")

    # --- 1. 当前版本号的来源 ---
    android = bool(args.android_gradle)
    code: int | None = None
    if android:
        gradle_path = args.android_gradle
        with open(os.path.join(repo, gradle_path), "r", encoding="utf-8") as handle:
            file_name, file_code = read_android_versions(handle.read())
        # CI 场景下版本号由 workflow 输入注入，仓库文件里的默认值只是本地构建的回退值；
        # 因此允许用 --version-name 覆盖「本次发布的版本号」，但仍会提示文件是否已同步。
        declared = args.version_name or file_name
        code = args.version_code if args.version_code is not None else file_code
        if args.version_name and args.version_name != file_name:
            report.warn(
                f"{gradle_path} 里的默认 versionName 仍为 {file_name}，与本次发布的 "
                f"{args.version_name} 不一致：本地/离线构建会报出旧版本号，建议同步该默认值。"
            )
    else:
        declared = None
        for spec in args.version_file:
            path, _, key = spec.partition(":")
            value = read_json_version(os.path.join(repo, path), key or "version")
            if declared is None:
                declared = value
            elif value != declared:
                report.fail(f"版本声明点漂移：{path} 为 {value}，但其他声明点为 {declared}。")
        if declared is None:
            report.fail("未提供任何版本声明点（--version-file 或 --android-gradle）。")
            report.render()
            return EXIT_FAIL

    if declared is None:
        report.render()
        return EXIT_FAIL

    # --- 2. 格式与 tag 一致性 ---
    if parse_semver(declared) is None:
        report.fail(f"版本号 {declared!r} 不是合法的 SemVer 2.0.0 版本（必须为 X.Y.Z，无前导零）。")
    else:
        report.ok(f"版本号 {declared} 符合 SemVer 2.0.0。")

    if not tag:
        report.warn("未提供 --tag，跳过 tag 一致性与递增性检查。")
        report.render()
        return EXIT_FAIL if report.failed else EXIT_OK
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?", tag):
        report.fail(f"tag {tag!r} 不符合 v 前缀 + X.Y.Z 的命名约定。")
    elif tag[1:] != declared:
        report.fail(f"tag {tag} 与代码声明的版本 {declared} 不一致（tag 必须精确等于 v+版本号）。")

    tags = collect_version_tags(repo)
    previous = previous_release(tags, version) if parse_semver(version) else None

    # --- 3. 递增性 ---
    if previous is None:
        report.warn("没有找到更早的已发布版本，跳过递增性检查（这是本仓库的首个版本）。")
    else:
        verdict = compare_versions(version, previous[1])
        if verdict < 0:
            report.fail(
                f"版本号回退：本次发布 {version} 低于已发布的 {previous[0]}（v{previous[1]}）。"
                f"已发布版本不可改，也不能用更低的版本号重新发布。"
            )
        else:
            report.ok(f"版本单调递增：{previous[0]} → {tag}。")

    # --- 4. Android versionCode 递增 ---
    if android:
        if previous is None:
            report.ok(f"首个版本，versionCode={code} 无需比较。")
        else:
            try:
                _, prev_code = read_android_versions(
                    git(repo, "show", f"{previous[0]}:{args.android_gradle}")
                )
            except Exception as exc:  # 上一版没有该文件
                report.warn(f"无法读取 {previous[0]} 处的 versionCode（{exc}），跳过检查。")
            else:
                if code is None or prev_code is None:
                    report.warn("versionCode 缺失，跳过检查。")
                elif code <= prev_code:
                    report.fail(
                        f"versionCode {code} 没有严格大于上一版 {previous[0]} 的 {prev_code}"
                        f"——应用商店会拒绝上传。"
                    )
                else:
                    report.ok(f"versionCode 单调递增：{prev_code} → {code}。")
        if code is not None and parse_semver(declared) is not None and declared.startswith("0."):
            report.note("  · 当前处于 0.y.z 初始开发期；1.0.0 之后破坏性变更必须升 MAJOR。")

    # --- 5. tag 是否指向将要发布的提交 ---
    if tag in {name for name, _ in tags}:
        try:
            git(repo, "merge-base", "--is-ancestor", tag, "HEAD")
            report.warn(f"tag {tag} 已存在且是 HEAD 的祖先，本次发布不会包含新提交。")
        except RuntimeError:
            report.ok(f"tag {tag} 将落在新提交上。")

    # --- 6. 提交类型与递增位 ---
    if args.check_commits and previous is not None:
        check_commit_types(report, repo, previous, version, args.zero_major_lenient)

    report.render()
    return EXIT_FAIL if report.failed else EXIT_OK


def main() -> int:
    parser = argparse.ArgumentParser(
        description="SemVer 版本门禁：校验发布前的版本号一致性、递增性与提交类型。",
    )
    parser.add_argument("--repo", default=".", help="仓库根目录，默认当前目录")
    parser.add_argument("--tag", default=None, help="本次发布的 tag，如 v1.5.18")
    parser.add_argument(
        "--mode", choices=["strict", "warn", "off"], default="strict",
        help="strict=有问题即失败(默认)；warn=问题只告警；off=跳过全部检查",
    )
    parser.add_argument(
        "--version-file", action="append", default=[], metavar="PATH:KEY",
        help="JSON 版本的声明点，可重复，如 theme.json:version",
    )
    parser.add_argument(
        "--android-gradle", default=None, metavar="PATH",
        help="Android 工程的构建文件，如 app/build.gradle.kts",
    )
    parser.add_argument("--version-code", type=int, default=None, help="本次发布的 versionCode")
    parser.add_argument(
        "--version-name", default=None, metavar="X.Y.Z",
        help="本次发布的版本号（覆盖 gradle 文件里的默认值，供 CI 注入场景使用）",
    )
    parser.add_argument(
        "--print-version", action="store_true",
        help="只打印声明点里的版本号后退出（供 CI 在未打 tag 时推导 tag，避免重复解析逻辑）",
    )
    parser.add_argument(
        "--check-commits", action="store_true",
        help="检查上一版 tag 到 HEAD 的提交类型与递增位是否匹配",
    )
    parser.add_argument(
        "--zero-major-lenient", action="store_true",
        help="0.y.z 初始开发期把提交类型检查降级为警告",
    )
    parser.add_argument(
        "--audit-history", default=None, metavar="PATH:KEY",
        help="审计历史：找出写入过却从未发布的版本号（JSON 或 *.gradle.kts 皆可）",
    )
    parser.add_argument(
        "--audit-allowlist", default=None, metavar="PATH",
        help="历史审计的已知欠账豁免清单",
    )
    args = parser.parse_args()

    if args.print_version:
        repo = os.path.abspath(args.repo)
        if args.android_gradle:
            with open(os.path.join(repo, args.android_gradle), "r", encoding="utf-8") as handle:
                print(read_android_versions(handle.read())[0])
        elif args.version_file:
            path, _, key = args.version_file[0].partition(":")
            print(read_json_version(os.path.join(repo, path), key or "version"))
        else:
            print("--print-version 需要同时给出 --android-gradle 或 --version-file", file=sys.stderr)
            return EXIT_FAIL
        return EXIT_OK

    if args.mode == "off":
        print("SemVer 版本门禁已被 --mode off 跳过。")
        return EXIT_OK

    if args.audit_history and not args.tag:
        report = Report()
        print(f">>> SemVer 版本历史审计 [{os.path.basename(os.path.abspath(args.repo))}]")
        audit_history(args.repo, [args.audit_history], args.audit_allowlist, report)
        report.render()
        return EXIT_FAIL if report.failed else EXIT_OK

    code = run_release_checks(args)
    if args.mode == "warn" and code == EXIT_FAIL:
        print(">>> --mode warn：门禁问题已降级为警告，不阻断本次流程。")
        return EXIT_OK
    return code


if __name__ == "__main__":
    sys.exit(main())
