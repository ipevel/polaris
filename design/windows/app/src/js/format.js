/* 展示格式化 —— 与 electron/util/format.js 口径一致，渲染层本地用。 */
(function () {
  const F = {};

  F.bytes = function (n) {
    const v = Number(n) || 0;
    if (v < 1024) return Math.round(v) + " B";
    const units = ["KB", "MB", "GB", "TB", "PB"];
    let x = v / 1024, i = 0;
    while (x >= 1024 && i < units.length - 1) { x /= 1024; i += 1; }
    return (x < 10 ? x.toFixed(2) : x < 100 ? x.toFixed(1) : Math.round(x)) + " " + units[i];
  };

  /** 速率：主进程已算好 MB/s，这里统一补一位小数与单位 */
  F.speed = function (mbps) {
    const v = Number(mbps) || 0;
    return (v >= 100 ? v.toFixed(0) : v.toFixed(1)) + " MB/s";
  };

  F.duration = function (str) { return str || "00:00:00"; };

  F.percent = function (used, total) {
    const t = Number(total) || 0;
    if (t <= 0) return 0;
    return Math.min(100, Math.max(0, Math.round((Number(used) || 0) / t * 100)));
  };

  F.escape = function (s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  };

  F.relativeTime = function (ts) {
    const t = Number(ts) || 0;
    if (!t) return "从未";
    const d = Date.now() - t;
    if (d < 60e3) return "刚刚";
    if (d < 3600e3) return Math.floor(d / 60e3) + " 分钟前";
    if (d < 86400e3) return Math.floor(d / 3600e3) + " 小时前";
    return Math.floor(d / 86400e3) + " 天前";
  };

  window.PolarisFormat = F;
})();
