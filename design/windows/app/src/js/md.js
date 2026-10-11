/* 极简 Markdown 渲染（安全子集）—— 面板下发的公告与套餐说明是 Markdown 原文，
 * 以前是「原样转义 + pre-line」显示，用户看到的是一堆 ** 和 | 的符号。
 *
 * 为什么自己写而不是引 marked.js：
 *   1) CSP 是 script-src 'self'，只能加载本地文件，不能走 CDN；
 *   2) 打包产物要小，一个文件几百行足够覆盖面板实际用到的语法；
 *   3) 先转义再解析，输出里只可能出现我们自己生成的标签 —— 面板文本永远不能注入 HTML。
 *
 * 支持：标题 # ~ ######、**粗体**、*斜体*、`行内代码`、```代码块```、
 *      - / * 无序列表、1. 有序列表、> 引用、--- 分隔线、
 *      [文字](链接) 与裸链接、Markdown 表格、段落内换行（渲染成 <br>）。
 * 段落内换行渲染成 <br> 是刻意选的：面板的公告是「一行一条」的写法，
 * 按 CommonMark 的软换行会挤成一坨。
 */
(function () {
  const ESC = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ESC[c]);
  const SAFE_URL = /^https?:\/\/[^\s"'<>\\]+$/i;
  const PH = "\u0000"; // 占位符：先摘出链接，最后还原，避免裸链接规则把链接内部再套一层

  /** 行内语法。入参必须已经 esc() 过。 */
  function inline(text) {
    const codes = [];
    const links = [];
    let out = text;

    // 1) 行内代码优先（内容里什么符号都不解析）
    out = out.replace(/`([^`]+)`/g, (m, c) => {
      codes.push(c);
      return PH + "C" + (codes.length - 1) + PH;
    });
    // 2) 图片 ![alt](url)
    out = out.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (m, alt, url) => (
      SAFE_URL.test(url) ? `<img class="md-img" src="${url}" alt="${alt}">` : alt
    ));
    // 3) 链接 [文字](url) —— 存成 data-mdlink，交给 app.js 走主进程校验后开系统浏览器
    out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (m, t, url) => {
      if (!SAFE_URL.test(url)) return t;
      links.push(url);
      return `<a class="md-link" data-mdlink="${url}">${t}</a>`;
    });
    // 4) 裸链接（没写成 [文字](url) 的）
    out = out.replace(/(^|[\s(（])((?:https?:\/\/)[^\s"'<>）)]+)/g, (m, pre, url) => (
      SAFE_URL.test(url) ? pre + `<a class="md-link" data-mdlink="${url}">${url}</a>` : m
    ));
    // 5) 粗体 / 斜体
    out = out.replace(/\*\*([^*\n]+)\*\*/g, "<b>$1</b>");
    out = out.replace(/__([^_\n]+)__/g, "<b>$1</b>");
    out = out.replace(/(^|[^*\w])\*([^*\n]+)\*/g, "$1<i>$2</i>");
    out = out.replace(/(^|[^\w_])_([^_\n]+)_/g, "$1<i>$2</i>");
    // 6) 行内代码还原
    out = out.replace(new RegExp(PH + "C(\\d+)" + PH, "g"), (m, i) => `<code class="md-code">${codes[+i]}</code>`);
    return out;
  }

  const isBlank = (l) => !l || !l.trim();
  const isHr = (l) => /^\s*([-*_])\s*(\1\s*){2,}$/.test(l);
  const heading = (l) => (l.match(/^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/) || null);
  const ulItem = (l) => l.match(/^\s*[-*+]\s+(.*)$/);
  const olItem = (l) => l.match(/^\s*\d{1,3}[.)]\s+(.*)$/);
  const isTableSep = (l) => /^\s*\|?[\s:|-]*-[\s:|-]*\|[\s:|-]*$/.test(l) && l.indexOf("|") >= 0;
  const cells = (l) => l.replace(/^\s*\|/, "").replace(/\|\s*$/, "").split("|").map((c) => c.trim());

  function render(src) {
    const lines = esc(src).replace(/\r\n?/g, "\n").split("\n");
    const out = [];
    let i = 0;

    while (i < lines.length) {
      const line = lines[i];

      if (isBlank(line)) { i++; continue; }

      // 代码块 ``` ... ```（内容不再做任何行内解析）
      const fence = line.match(/^\s*```+\s*([\w+-]*)\s*$/);
      if (fence) {
        const buf = [];
        i++;
        while (i < lines.length && !/^\s*```+\s*$/.test(lines[i])) { buf.push(lines[i]); i++; }
        i++; // 吃掉收尾的 ```
        out.push(`<pre class="md-pre"><code>${buf.join("\n")}</code></pre>`);
        continue;
      }

      if (isHr(line)) { out.push('<hr class="md-hr">'); i++; continue; }

      const h = heading(line);
      if (h) { out.push(`<div class="md-h md-h${h[1].length}">${inline(h[2])}</div>`); i++; continue; }

      // 引用
      if (/^\s*>\s?/.test(line)) {
        const buf = [];
        while (i < lines.length && /^\s*>\s?/.test(lines[i])) { buf.push(lines[i].replace(/^\s*>\s?/, "")); i++; }
        out.push(`<blockquote class="md-quote">${inline(buf.join("\n")).replace(/\n/g, "<br>")}</blockquote>`);
        continue;
      }

      // 表格：当前行含 |，下一行是分隔行
      if (line.indexOf("|") >= 0 && i + 1 < lines.length && isTableSep(lines[i + 1])) {
        const head = cells(line);
        i += 2;
        const body = [];
        while (i < lines.length && !isBlank(lines[i]) && lines[i].indexOf("|") >= 0) { body.push(cells(lines[i])); i++; }
        out.push('<div class="md-table-wrap"><table class="md-table"><thead><tr>' +
          head.map((c) => `<th>${inline(c)}</th>`).join("") + "</tr></thead><tbody>" +
          body.map((r) => "<tr>" + head.map((_, k) => `<td>${inline(r[k] == null ? "" : r[k])}</td>`).join("") + "</tr>").join("") +
          "</tbody></table></div>");
        continue;
      }

      // 列表（ul / ol 各自成段，嵌套层级只支持一层，够用）
      if (ulItem(line) || olItem(line)) {
        const ordered = !!olItem(line) && !ulItem(line);
        const buf = [];
        while (i < lines.length) {
          const m = ordered ? olItem(lines[i]) : ulItem(lines[i]);
          const other = ordered ? ulItem(lines[i]) : olItem(lines[i]);
          if (!m || other) break;
          buf.push(inline(m[1]));
          i++;
        }
        const tag = ordered ? "ol" : "ul";
        out.push(`<${tag} class="md-list">${buf.map((t) => `<li>${t}</li>`).join("")}</${tag}>`);
        continue;
      }

      // 段落：吃到空行或下一个块的起始行为止，块内换行渲染成 <br>
      const buf = [];
      while (i < lines.length && !isBlank(lines[i]) && !heading(lines[i]) && !isHr(lines[i]) &&
        !ulItem(lines[i]) && !olItem(lines[i]) && !/^\s*>/.test(lines[i]) && !/^\s*```/.test(lines[i])) {
        buf.push(lines[i]);
        i++;
      }
      if (buf.length) out.push(`<p class="md-p">${inline(buf.join("\n")).replace(/\n/g, "<br>")}</p>`);
      else i++; // 兜底：不该出现，真出现了也不能死循环
    }
    return out.join("");
  }

  window.PolarisMarkdown = { render, escape: esc, hasMarkdown: (s) => /(^|\n)\s*(#{1,6}\s|[-*+]\s|\d+[.)]\s|>|\||```|---)|\*\*|\[[^\]]+\]\(|\|/.test(String(s || "")) };
})();
