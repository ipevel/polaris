#!/usr/bin/env bash
# 解析本次发布的 versionCode（issue #30：确定性 —— 实际发布的版本号必须等于仓库声明值）。
#
#   bash .scripts/resolve_version_code.sh --gradle app/build.gradle.kts [--input <versionCode>]
#
# 规则：
#   1) 传了 --input：必须是纯数字，否则红；
#   2) 没传：用仓库声明值（build.gradle.kts 里 slteVersionCode 的默认值），并打 ::notice::；
#   3) 声明值读不到：先按 epoch 分钟回退并显式 ::warning::（保留诊断），然后照样红 ——
#      缺声明不允许发版（否则「声明值 不等于 实际值」会再次发生，README 版本表就不准了）；
#   4) 无论走哪条路径，最终都断言「本次 VC 等于仓库声明值」，不等即红。
#
# 成功时只把 VC 打到 stdout（供 GITHUB_OUTPUT 使用），其它信息一律 stderr。
set -euo pipefail

GRADLE="app/build.gradle.kts"
INPUT_VC=""
while [ $# -gt 0 ]; do
  case "$1" in
    --gradle) GRADLE="${2:-}"; shift 2 ;;
    --input) INPUT_VC="${2:-}"; shift 2 ;;
    *) echo "::error::未知参数 $1" >&2; exit 2 ;;
  esac
done

# 读仓库声明值（形如 val slteVersionCode = slteValue("POLARIS_VERSION_CODE")?.toIntOrNull() ?: 29867917）
DECLARED=""
if [ -f "$GRADLE" ]; then
  DECLARED=$(grep -E '^[[:space:]]*val[[:space:]]+slteVersionCode[[:space:]]*=' "$GRADLE" | head -1 | sed -E 's/.*\?[[:space:]]*:[[:space:]]*([0-9]+).*/\1/' || true)
  case "$DECLARED" in
    ''|*[!0-9]*) DECLARED="" ;;
  esac
fi

if [ -n "$INPUT_VC" ]; then
  case "$INPUT_VC" in
    ''|*[!0-9]*) echo "::error::versionCode $INPUT_VC 必须是纯数字" >&2; exit 1 ;;
  esac
  VC="$INPUT_VC"
elif [ -n "$DECLARED" ]; then
  VC="$DECLARED"
  echo "::notice::未传 versionCode，使用仓库声明值 $VC（来自 $GRADLE 的 slteVersionCode）" >&2
else
  VC=$(( $(date +%s) / 60 ))
  echo "::warning::读不到仓库声明的 versionCode（$GRADLE 的 slteVersionCode），回退 epoch 分钟 $VC；缺声明不放行，见下一条断言" >&2
fi

if [ -z "$DECLARED" ]; then
  echo "::error::读不到仓库声明的 versionCode（$GRADLE 的 slteVersionCode 默认值）：拒绝发版（issue #30：实际发布的 versionCode 必须等于仓库声明值）" >&2
  exit 1
fi
if [ "$VC" != "$DECLARED" ]; then
  echo "::error::本次 versionCode $VC 不等于仓库声明值 $DECLARED：请显式传与仓库一致的值，或先在发版 PR 里改 app/build.gradle.kts 的 slteVersionCode（issue #30）" >&2
  exit 1
fi
echo "$VC"
