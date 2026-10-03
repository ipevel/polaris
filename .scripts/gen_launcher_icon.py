#!/usr/bin/env python3
"""生成北辰 Polaris 的启动图标位图资源。

自适应图标（API 26+）本身用的是 app/src/main/res/drawable/ 下的三个矢量层
（ic_launcher_foreground.xml / ic_launcher_background.xml / ic_launcher_monochrome.xml），
本脚本只负责两类位图：

  1. 传统 mipmap 位图 —— API < 26 的启动器，以及某些会直接按密度读 PNG 的工具
     （minSdk 是 28，所以这只是兜底，但必须和矢量层长得一样，否则会看到旧图标）
  2. Google Play 商店用的 512×512 图

几何定义与 drawable/ic_launcher_foreground.xml 一一对应，改字形时两边要一起改。

用法：
    python .scripts/gen_launcher_icon.py

为什么用 Python 而不是原来的 .scripts/svg2png.js：
那个脚本硬编码的是旧的白色手绘星形，而且依赖仓库里并不存在的 sharp。
Pillow 是运行环境本来就有的（本机 bundled Python 自带 12.3.0），不需要装任何东西。
"""

from __future__ import annotations

import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - 只为了给出人话错误
    sys.exit("需要 Pillow：pip install pillow")


# ---------------------------------------------------------------- 几何（单位：自适应图标画布的 108 单位）

UNIT = 108.0  # 自适应图标画布边长
VISIBLE = 72.0  # 启动器实际显示的是画布中央 72/108 那块（每边各留 18 单位给视差溢出）
BLEED = (UNIT - VISIBLE) / 2.0  # = 18.0

# 竖杠：12×50，圆头（rx=6），x=35..47、y=29..79
STEM = (35.0, 29.0, 47.0, 79.0)
STEM_R = 6.0

# 圆环碗部：圆心 (56,46)，外半径 17，内半径 9
RING_C = (56.0, 46.0)
RING_OUTER = 17.0
RING_INNER = 9.0

# 颜色：与 ic_launcher_background.xml / ic_launcher_foreground.xml 保持一致
BG = (247, 248, 252, 255)  # #F7F8FC
FG = (47, 107, 246, 255)  # #2F6BF6

# 传统位图的密度档位：mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

ROUNDED_CORNER = 0.22  # 圆角半径占边长的比例（启动器普遍在 20%~25%）
SUPERSAMPLE = 8  # 先放大 8 倍画，再 LANCZOS 缩回来，得到抗锯齿边缘

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
# 商店图不是 App 资源（不进 APK），但它是用同一个脚本生成、每次发版都要上传的
# 发布素材，所以放在 design/ 下入库；design/ 里被 .gitignore 忽略的几个子目录
# （emulator/、screenshots/ 等）放的是本机临时截图，不要混在一起。
STORE_DIR = os.path.join(ROOT, "design", "launcher")


def _draw_glyph(d: ImageDraw.ImageDraw, to_px, hole_fill) -> None:
    """在已经画好底色的图上画 O3 字形。

    to_px: 把画布单位换算成像素的 callable（含取整）
    hole_fill: 圆环内孔填什么颜色。位图底色不透明，所以直接填底色即可；
               矢量层那边用的是 fillType="evenOdd" 留真透明，两者视觉一致。
    """
    # 竖杠（圆头圆角矩形）
    x0, y0, x1, y1 = (to_px(v) for v in STEM)
    d.rounded_rectangle(
        [x0, y0, x1, y1],
        radius=max(1.0, (x1 - x0) / 2.0),  # 竖杠宽 12，圆角半径就是半宽 6
        fill=FG,
    )

    # 圆环：外圆填前景色，内圆填底色，等于挖空
    cx, cy = (to_px(v) for v in RING_C)
    k = (x1 - x0) / (STEM[2] - STEM[0])  # 单位→像素的比例，复用竖杠算出来的
    ro = RING_OUTER * k
    ri = RING_INNER * k
    d.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=FG)
    d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=hole_fill)


def render_legacy(size: int, *, round_icon: bool) -> Image.Image:
    """传统启动器位图：把自适应图标的可见区（中央 72×72）铺满整张画布。

    启动器就是这么裁的 —— 108 画布每边 18 单位被掩膜切掉 —— 所以按 72 铺满，
    位图和矢量在桌面上看起来才是同一个大小。
    """
    ss = size * SUPERSAMPLE
    img = Image.new("RGBA", (ss, ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 底色：圆形档画整圆，方形档画圆角方（圆角外留透明，与旧资源一致）
    if round_icon:
        d.ellipse([0, 0, ss - 1, ss - 1], fill=BG)
    else:
        d.rounded_rectangle(
            [0, 0, ss - 1, ss - 1], radius=ss * ROUNDED_CORNER, fill=BG
        )

    scale = ss / VISIBLE

    def to_px(u: float) -> float:
        return (u - BLEED) * scale

    _draw_glyph(d, to_px, BG)
    return img.resize((size, size), Image.LANCZOS)


def render_play(size: int = 512) -> Image.Image:
    """Google Play 商店图：整幅 108 画布铺满正方形，不透明、不预切圆角。

    Play 自己会套掩膜，所以源图必须是满幅方图。这里按完整 108 画布映射，
    于是字形的占比（宽约 57%）和自适应图标画布一致，不会被 Play 的圆角切到。
    """
    ss = size * SUPERSAMPLE
    img = Image.new("RGBA", (ss, ss), BG)
    d = ImageDraw.Draw(img)
    scale = ss / UNIT
    _draw_glyph(d, lambda u: u * scale, BG)
    return img.resize((size, size), Image.LANCZOS)


def main() -> int:
    written: list[str] = []

    for name, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)
        for filename, is_round in (
            ("ic_launcher.png", False),
            ("ic_launcher_round.png", True),
        ):
            path = os.path.join(out_dir, filename)
            render_legacy(size, round_icon=is_round).save(path, optimize=True)
            written.append(os.path.relpath(path, ROOT))

    os.makedirs(STORE_DIR, exist_ok=True)
    play = os.path.join(STORE_DIR, "play_store_512.png")
    render_play(512).save(play, optimize=True)
    written.append(os.path.relpath(play, ROOT))

    for line in written:
        print(line)
    print(f"\n共 {len(written)} 个文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
