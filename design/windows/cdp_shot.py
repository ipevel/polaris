import json, sys, time, base64, urllib.request, websocket

def cdp_screenshot(url, out_path, width=1600, height=1000):
    # get targets
    with urllib.request.urlopen("http://127.0.0.1:9222/json/list", timeout=10) as r:
        targets = json.loads(r.read())
    page = next(t for t in targets if t["type"] == "page")
    ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=15)
    seq = [0]
    def send(method, params=None):
        seq[0] += 1
        ws.send(json.dumps({"id": seq[0], "method": method, "params": params or {}}))
        while True:
            msg = json.loads(ws.recv())
            if msg.get("id") == seq[0]:
                return msg
    send("Page.enable")
    send("Emulation.setDeviceMetricsOverride", {"width": width, "height": height, "deviceScaleFactor": 1, "mobile": False})
    send("Page.navigate", {"url": url})
    time.sleep(2.5)
    res = send("Page.captureScreenshot", {"format": "png"})
    data = base64.b64decode(res["result"]["data"])
    open(out_path, "wb").write(data)
    ws.close()
    print("saved", out_path, len(data), "bytes")

if __name__ == "__main__":
    cdp_screenshot(sys.argv[1], sys.argv[2])
