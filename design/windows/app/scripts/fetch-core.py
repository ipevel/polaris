"""拉取内核二进制到 app/core/。运行前需要 requests。

    python scripts/fetch-core.py

版本来源：https://github.com/MetaCubeX/mihomo/releases
wintun：  https://www.wintun.net/builds/

Windows 用 amd64-**compatible** 变体（不带 AVX 指令），老 CPU 也能跑 —— 便携版
面向"任何 Win10+ 机器"，兼容性优先于那点性能差异。
"""

import io
import json
import os
import shutil
import subprocess
import sys
import zipfile

try:
    import requests
except ImportError:
    sys.exit('需要 requests：pip install requests')

MIHOMO_VERSION = 'v1.19.32'
MIHOMO_ASSET = 'mihomo-windows-amd64-compatible-%s.zip' % MIHOMO_VERSION
WINTUN_VERSION = '0.14.1'
WINTUN_URL = 'https://www.wintun.net/builds/wintun-%s.zip' % WINTUN_VERSION

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
CORE = os.path.join(APP, 'core')
TMP = os.path.join(APP, '.devdata', '_dl')

PROXY = None
if os.environ.get('HTTPS_PROXY'):
    PROXY = {'https': os.environ['HTTPS_PROXY'], 'http': os.environ.get('HTTP_PROXY', os.environ['HTTPS_PROXY'])}

os.makedirs(CORE, exist_ok=True)
os.makedirs(TMP, exist_ok=True)


def fetch(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 100_000:
        print('cached  %s' % os.path.basename(dest))
        return dest
    print('fetch   %s' % url)
    r = requests.get(url, proxies=PROXY, timeout=300, stream=True)
    r.raise_for_status()
    with open(dest, 'wb') as f:
        for chunk in r.iter_content(1 << 20):
            f.write(chunk)
    return dest


mz = fetch('https://github.com/MetaCubeX/mihomo/releases/download/%s/%s' % (MIHOMO_VERSION, MIHOMO_ASSET),
           os.path.join(TMP, MIHOMO_ASSET))
with zipfile.ZipFile(mz) as z:
    for n in z.namelist():
        base = os.path.basename(n)
        if base.lower().endswith('.exe'):
            with z.open(n) as s, open(os.path.join(CORE, 'mihomo.exe'), 'wb') as d:
                shutil.copyfileobj(s, d)

wz = fetch(WINTUN_URL, os.path.join(TMP, 'wintun.zip'))
with zipfile.ZipFile(wz) as z:
    picked = next((n for n in z.namelist() if n.lower().endswith('amd64/wintun.dll')), None) \
        or next((n for n in z.namelist() if os.path.basename(n).lower() == 'wintun.dll'), None)
    if not picked:
        sys.exit('wintun.zip 里没找到 wintun.dll：%s' % z.namelist())
    with z.open(picked) as s, open(os.path.join(CORE, 'wintun.dll'), 'wb') as d:
        shutil.copyfileobj(s, d)
    for n in z.namelist():
        base = os.path.basename(n)
        if base.lower().startswith(('license', 'readme')):
            with z.open(n) as s, open(os.path.join(CORE, 'wintun-' + base), 'wb') as d:
                shutil.copyfileobj(s, d)

lock = {
    'mihomo_version': MIHOMO_VERSION,
    'mihomo_asset': MIHOMO_ASSET,
    'mihomo_flavor': 'windows-amd64-compatible',
    'wintun_version': WINTUN_VERSION,
}
io.open(os.path.join(APP, 'core.lock.json'), 'w', encoding='utf-8').write(
    json.dumps(lock, ensure_ascii=False, indent=2))

print('\ncore/ 内容：')
for f in sorted(os.listdir(CORE)):
    print('  %-26s %10d' % (f, os.path.getsize(os.path.join(CORE, f))))

r = subprocess.run([os.path.join(CORE, 'mihomo.exe'), '-v'], capture_output=True, timeout=60)
print('\n' + (r.stdout + r.stderr).decode('utf-8', 'replace').strip())