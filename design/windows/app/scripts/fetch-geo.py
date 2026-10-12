"""准备规则库到 resources/geo/。

    python scripts/fetch-geo.py

优先从本仓库的 Android assets 直接拷（快、离线、版本一致）；
拷不到再从 appshubcc/bett-rules 下载。

来源与安卓端 `app/src/main/java/com/slte/app/data/geo/GeoResources.kt` 的清单一致
（也就是面板「资源」页那一套），两端不会漂移。

为什么要随包分发：不这么做，mihomo 首次连接会自己去 GitHub 下 GeoIP.dat，
实测 19 秒，且没网就直接起不来。
"""

import hashlib
import io
import os
import shutil
import sys
import time

# Windows 上 Python 3.12 的默认 stdout 编码是 cp1252（不是 UTF-8），打印中文会
# UnicodeEncodeError。本脚本后面要打印「已拷贝 geosite.dat」这类中文。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

E = 'E:/'
HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
GEO = os.path.join(APP, 'resources', 'geo')

# 同仓库的 Android assets（相对 app/ 往上找到仓库根）
REPO_ASSETS_CANDIDATES = [
    os.path.join(APP, '..', '..', '..', 'app', 'src', 'main', 'assets'),
    os.path.join(APP, '..', '..', 'app', 'src', 'main', 'assets'),
]

FILES = ['geoip.metadb', 'geosite.dat', 'ASN.mmdb']

REMOTE = {
    'geoip.metadb': 'https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geoip.metadb',
    'geosite.dat': 'https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/geosite.dat',
    'ASN.mmdb': 'https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release/GeoLite2-ASN.mmdb',
}

# MRS 规则集打包：mihomo 的 .mrs 二进制格式。内核当前用的是远程 RULE-SET providers
# （kernel-core/.../routing_table.go 的 Providers），不消费 .mrs，所以默认不下载。
# 机器上有 7z 时可解包备用（python fetch-geo.py --with-mrs）。
MRS_BUNDLE = 'https://github.com/appshubcc/bett-rules/releases/download/latest/BundleMRS.7z'


def fetch_mrs():
    """下载并解包 BundleMRS.7z。没有 7z 就跳过——它是可选增强，不是硬依赖。"""
    import shutil as _sh
    import subprocess

    seven = next((p for p in ('7z', '7za', '7zr') if _sh.which(p)), None)
    if not seven:
        print('跳过 MRS：机器上没有 7z / 7za / 7zr，无法解包。')
        return
    import requests

    archive = os.path.join(GEO, 'BundleMRS.7z')
    print('  fetch %s' % MRS_BUNDLE)
    r = requests.get(MRS_BUNDLE, timeout=300, stream=True)
    r.raise_for_status()
    with open(archive, 'wb') as fh:
        for chunk in r.iter_content(1 << 20):
            fh.write(chunk)
    subprocess.check_call([seven, 'x', '-y', '-o' + GEO, archive])
    os.remove(archive)
    print('  MRS 已解包到 %s' % GEO)


def local_copy():
    for base in REPO_ASSETS_CANDIDATES:
        p = os.path.abspath(base)
        if not os.path.isdir(p):
            continue
        have = [f for f in FILES if os.path.exists(os.path.join(p, f))]
        if not have:
            continue
        print('使用本地 assets:', p)
        for f in have:
            src = os.path.join(p, f)
            dst = os.path.join(GEO, f)
            if os.path.exists(dst) and os.path.getsize(dst) == os.path.getsize(src):
                print('  skip  %-16s (%d bytes)' % (f, os.path.getsize(src)))
                continue
            shutil.copy2(src, dst)
            print('  copy  %-16s (%d bytes)' % (f, os.path.getsize(dst)))
        return {f for f in have}
    return set()


def remote_copy(missing):
    if not missing:
        return
    try:
        import requests
    except ImportError:
        print('缺少 requests，无法下载缺失的规则库:', sorted(missing))
        return
    proxy = None
    if os.environ.get('HTTPS_PROXY'):
        proxy = {'https': os.environ['HTTPS_PROXY'], 'http': os.environ.get('HTTP_PROXY', os.environ['HTTPS_PROXY'])}
    for f in sorted(missing):
        url = REMOTE.get(f)
        if not url:
            print('  no remote for', f)
            continue
        print('  fetch %s' % url)
        r = requests.get(url, proxies=proxy, timeout=300, stream=True)
        r.raise_for_status()
        with open(os.path.join(GEO, f), 'wb') as fh:
            for chunk in r.iter_content(1 << 20):
                fh.write(chunk)
        print('  saved %-16s (%d bytes)' % (f, os.path.getsize(os.path.join(GEO, f))))


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def refresh(geo_dir):
    """按 24 小时周期刷新运行期规则库（data/geo/）。

    与安卓端 `GeoResources.sync()` 同一套策略：三份文件随包内置、内核只读本地，
    这里只负责让本地这份跟上上游；距上次成功更新不足 24 小时就一个请求都不发。

    失败只打印，不删旧文件——旧规则能正常分流，坏文件才会让内核起不来。
    """
    os.makedirs(geo_dir, exist_ok=True)
    marker = os.path.join(geo_dir, 'geo_last_sync')
    if os.path.exists(marker):
        age = time.time() - os.path.getmtime(marker)
        if age < 24 * 3600:
            print('geo 规则库 %.1f 小时前刚更新过，跳过。' % (age / 3600))
            return

    try:
        import requests
    except ImportError:
        print('缺少 requests，跳过 geo 刷新。')
        return
    proxy = None
    if os.environ.get('HTTPS_PROXY'):
        proxy = {'https': os.environ['HTTPS_PROXY'], 'http': os.environ.get('HTTP_PROXY', os.environ['HTTPS_PROXY'])}

    updated = []
    for name in FILES:
        url = REMOTE.get(name)
        if not url:
            continue
        tmp = os.path.join(geo_dir, name + '.part')
        try:
            print('  fetch %s' % url)
            r = requests.get(url, proxies=proxy, timeout=300, stream=True)
            r.raise_for_status()
            with open(tmp, 'wb') as fh:
                for chunk in r.iter_content(1 << 20):
                    fh.write(chunk)
            if os.path.getsize(tmp) <= 0:
                raise RuntimeError('下载到 0 字节')

            # 上游为每个资产发布 <name>.sha256sum；校验不过绝不覆盖旧文件。
            try:
                sr = requests.get(url + '.sha256sum', proxies=proxy, timeout=60)
                sr.raise_for_status()
                expected = sr.text.strip().split()[0]
            except Exception:
                expected = None
            if expected:
                actual = sha256_of(tmp)
                if actual.lower() != expected.lower():
                    raise RuntimeError('sha256 不匹配：期望 %s，实际 %s' % (expected, actual))

            os.replace(tmp, os.path.join(geo_dir, name))
            updated.append(name)
            print('  saved %-16s (%d bytes)' % (name, os.path.getsize(os.path.join(geo_dir, name))))
        except Exception as e:
            print('  失败 %s: %s' % (name, e))
            if os.path.exists(tmp):
                try:
                    os.remove(tmp)
                except OSError:
                    pass

    if updated:
        with open(marker, 'w') as fh:
            fh.write(str(int(time.time())))
        print('geo 规则库已更新：%s' % ', '.join(updated))
    else:
        print('geo 规则库本次没有更新成功，继续使用本地这份。')


def main():
    if '--with-mrs' in sys.argv:
        fetch_mrs()
        return
    if '--refresh' in sys.argv:
        i = sys.argv.index('--refresh')
        target = sys.argv[i + 1] if len(sys.argv) > i + 1 else GEO
        refresh(os.path.abspath(target))
        return
    os.makedirs(GEO, exist_ok=True)
    print('目标目录:', GEO)
    got = local_copy()
    remote_copy(set(FILES) - got)

    print('\nresources/geo/ 内容：')
    total = 0
    for f in sorted(os.listdir(GEO)):
        s = os.path.getsize(os.path.join(GEO, f))
        total += s
        print('  %-18s %12d' % (f, s))
    print('  合计 %.1f MB' % (total / 1024 / 1024))

    missing = [f for f in FILES if not os.path.exists(os.path.join(GEO, f))]
    if missing:
        print('\n警告：仍缺 %s —— 首次连接 mihomo 会尝试联网下载，可能很慢或失败。' % missing)
        sys.exit(1)


if __name__ == '__main__':
    main()
