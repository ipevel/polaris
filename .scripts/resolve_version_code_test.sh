#!/usr/bin/env bash
# resolve_version_code.sh 自检（issue #30 的本地证据）：三种输入 + 「读不到声明值」负例。
#   bash .scripts/resolve_version_code_test.sh
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
S=$HERE/resolve_version_code.sh
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
GOOD=$TMP/build.gradle.kts
{
  echo 'val slteVersionCode = slteValue("POLARIS_VERSION_CODE")?.toIntOrNull() ?: 29867917'
  echo 'versionCode = slteVersionCode'
} > "$GOOD"
NODECL=$TMP/no-decl.gradle.kts
echo 'val somethingElse = 1' > "$NODECL"

pass=0
fail=0
check() {
  local name=$1 want_code=$2 want_out=$3 want_err=$4
  shift 4
  local out code err ok=1
  out=$("$@" 2>"$TMP/err")
  code=$?
  err=$(cat "$TMP/err")
  [ "$code" = "$want_code" ] || ok=0
  if [ "$want_out" != IGNORE ] && [ "$out" != "$want_out" ]; then ok=0; fi
  if [ "$want_err" != IGNORE ] && ! printf '%s' "$err" | grep -q "$want_err"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    pass=$((pass+1)); echo "  [ OK ] $name"
  else
    fail=$((fail+1)); echo "  [FAIL] $name (exit=$code out='$out' err='$(printf '%s' "$err" | head -c 160)')"
  fi
}

echo 'resolve_version_code 自检'
check '传值 = 声明值 → 放行' 0 29867917 IGNORE bash "$S" --gradle "$GOOD" --input 29867917
check '不传 → 用仓库声明值（notice）' 0 29867917 使用仓库声明值 bash "$S" --gradle "$GOOD"
check '传非法值 12a → 红（必须是纯数字）' 1 IGNORE 必须是纯数字 bash "$S" --gradle "$GOOD" --input 12a
check '传空串 → 等价不传（用声明值）' 0 29867917 IGNORE bash "$S" --gradle "$GOOD" --input ''
check '传值 不等于 声明值 → 红（点名声明值）' 1 IGNORE 不等于仓库声明值 bash "$S" --gradle "$GOOD" --input 29868123
check 'gradle 文件不存在 → warn 回退 + 红' 1 IGNORE 回退 bash "$S" --gradle "$TMP/nope.gradle.kts"
check 'gradle 里没有 slteVersionCode → warn 回退 + 红' 1 IGNORE 读不到 bash "$S" --gradle "$NODECL"
check '未知参数 → 退出码 2' 2 IGNORE 未知参数 bash "$S" --bogus

echo "resolve_version_code：$pass passed / $fail failed（共 $((pass+fail)) 项）"
[ "$fail" = 0 ]
