import sys
sys.path.insert(0, "/tmp")
from cdp_shot import cdp_screenshot
import os
BASE = "/home/hatch/workspace/polaris-win-design"
pages = ["home","nodes","traffic","settings","me","login","plans","orders","tickets","invite","giftcard","notices","proxy","routing","update","tray"]
for p in pages:
    url = f"file://{BASE}/pages/{p}.html"
    out = f"{BASE}/shots/{p}.png"
    try:
        cdp_screenshot(url, out)
    except Exception as e:
        print("FAIL", p, str(e)[:120])
