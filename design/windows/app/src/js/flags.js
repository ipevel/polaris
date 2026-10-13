/* 国旗资源清单 + 取图助手 —— 与安卓端同一批图（`app/src/main/assets/flags/*.svg`）。
 *
 * 为什么把 257 个码硬列在这里，而不是「拼出 src、失败再靠 onerror 兜」：
 * - index.html 的 CSP 是 `script-src 'self'`，**行内事件处理器（onerror=）会被拒**，
 *   靠 onerror 兜底的写法在这个页面里根本不会执行；
 * - `img.src` 指向一个不存在的文件只会得到一个裂图，没有任何 JS 回调能纠正它。
 * 所以走「白名单 + 先判断再拼 src」：码不在表里就一个字都不输出，
 * 由调用方退回原来的文字徽标 —— 宁可少一面旗，也不给裂图。
 *
 * 表由脚本从 `src/assets/flags/` 目录生成（257 个）。**加了新图要同步这张表**，
 * 否则新图不会显示；`scripts/flag-codes-test.js` 会做这个一致性检查。
 */
(function () {
  const FLAG_CODES = new Set([
    "ad", "ae", "af", "ag", "ai", "al", "am", "ao", "aq", "ar", "as", "at", "au", "aw", "ax", "az",
    "ba", "bb", "bd", "be", "bf", "bg", "bh", "bi", "bj", "bl", "bm", "bn", "bo", "bq", "br", "bs",
    "bt", "bv", "bw", "by", "bz", "ca", "cc", "cd", "cf", "cg", "ch", "ci", "ck", "cl", "cm", "cn",
    "co", "cp", "cr", "cu", "cv", "cw", "cx", "cy", "cz", "de", "dg", "dj", "dk", "dm", "do", "dz",
    "ec", "ee", "eg", "eh", "er", "es", "et", "eu", "fi", "fj", "fk", "fm", "fo", "fr", "ga", "gb",
    "gd", "ge", "gf", "gg", "gh", "gi", "gl", "gm", "gn", "gp", "gq", "gr", "gs", "gt", "gu", "gw",
    "gy", "hk", "hm", "hn", "hr", "ht", "hu", "ic", "id", "ie", "il", "im", "in", "io", "iq", "ir",
    "is", "it", "je", "jm", "jo", "jp", "ke", "kg", "kh", "ki", "km", "kn", "kp", "kr", "kw", "ky",
    "kz", "la", "lb", "lc", "li", "lk", "lr", "ls", "lt", "lu", "lv", "ly", "ma", "mc", "md", "me",
    "mf", "mg", "mh", "mk", "ml", "mm", "mn", "mo", "mp", "mq", "mr", "ms", "mt", "mu", "mv", "mw",
    "mx", "my", "mz", "na", "nc", "ne", "nf", "ng", "ni", "nl", "no", "np", "nr", "nu", "nz", "om",
    "pa", "pc", "pe", "pf", "pg", "ph", "pk", "pl", "pm", "pn", "pr", "ps", "pt", "pw", "py", "qa",
    "re", "ro", "rs", "ru", "rw", "sa", "sb", "sc", "sd", "se", "sg", "sh", "si", "sj", "sk", "sl",
    "sm", "sn", "so", "sr", "ss", "st", "sv", "sx", "sy", "sz", "tc", "td", "tf", "tg", "th", "tj",
    "tk", "tl", "tm", "tn", "to", "tr", "tt", "tv", "tw", "tz", "ua", "ug", "um", "un", "us", "uy",
    "uz", "va", "vc", "ve", "vg", "vi", "vn", "vu", "wf", "ws", "xk", "xx", "ye", "yt", "za", "zm",
    "zw",
  ]);

  /**
   * 取一面旗的 `<img>` 标签；码不合法或没有对应图时返回空串。
   *
   * `code` 来自主进程 `region.detect()`，是两位 ISO 码；这里仍然做一次
   * `/^[a-z]{2}$/` 校验 —— 绝不让任意文本（可能含 `../`）进入 src。
   */
  function flagImg(code) {
    const c = String(code || '').toLowerCase();
    if (!/^[a-z]{2}$/.test(c) || !FLAG_CODES.has(c)) return '';
    return '<img class="flag" src="assets/flags/' + c + '.svg" alt="" loading="lazy">';
  }

  window.PolarisFlags = { flagImg, has: (c) => FLAG_CODES.has(String(c || '').toLowerCase()), count: FLAG_CODES.size };
})();
