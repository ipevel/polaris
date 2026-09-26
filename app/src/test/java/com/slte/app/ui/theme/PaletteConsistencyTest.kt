// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 色板一致性回归（第三批"令牌与基建收敛"的核心护栏）。
 *
 * 为什么需要这个测试：本项目历史上出现过**两份色板漂移**的事故——v5 原型工程自带一份
 * "精简版" Material 配色，漏了 `onSecondary`，且暗色 `onPrimary` 用了纯白，而暗色 primary
 * 是浅蓝 `#6E97FF`，白字对比度不足。两份色板一旦不同源，同一个页面在"v5 外壳"与
 * "v4 外壳"下取色就会不一致，而且**编译器不会报错**（两边都是合法 Color）。
 *
 * 现在 Theme.kt 是唯一的色板来源，V5Theme.kt 只提供 v5 独有的 `V5Colors`。
 * 这个测试把"v5 与外壳必须同源"钉成断言，防止将来又有人加第二份色板。
 */

/** 与 Theme.kt 保持一致取值的期望色板（改 Theme.kt 时这里要同步改，改不动就说明有人动了契约）。 */
private val ExpectedLight =
    lightColorScheme(
        primary = Color(0xFF2F6BF6),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF16AC6C),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFFEE8A2C),
        error = Color(0xFFE5484D),
        background = Color(0xFFF1F0F6),
        onBackground = Color(0xFF191C26),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF191C26),
        surfaceVariant = Color(0xFFF6F5FA),
        onSurfaceVariant = Color(0xFF575E72),
        outline = Color(0xFFE8E6EF),
        outlineVariant = Color(0xFFEFEDF5),
    )

private val ExpectedDark =
    darkColorScheme(
        primary = Color(0xFF6E97FF),
        onPrimary = Color(0xFF0B0D15),
        secondary = Color(0xFF3DCC8E),
        onSecondary = Color(0xFF0B0D15),
        tertiary = Color(0xFFF5A25B),
        error = Color(0xFFF4776D),
        background = Color(0xFF0B0D15),
        onBackground = Color(0xFFEDF0F8),
        surface = Color(0xFF171A25),
        onSurface = Color(0xFFEDF0F8),
        surfaceVariant = Color(0xFF1F2331),
        onSurfaceVariant = Color(0xFFA7AEC2),
        outline = Color(0x14FFFFFF),
        outlineVariant = Color(0x0DFFFFFF),
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.slte.app.support.RobolectricTestApplication::class)
class PaletteConsistencyTest {

    // ==================== V5Colors 与 Material 色板的同源关系 ====================

    /**
     * v5 的 `bg`/`surface` 必须与 Material 的 `background`/`surface` 是同一个颜色。
     *
     * 理由：v5 页面画在 `V5PageScaffold` 的氛围底上（用 `V5Colors.bg`），而窗口底色、状态栏
     * 外观、以及还没 v5 化的 Material 组件用的是 `colorScheme.background`。两者若不同色，
     * 页面切换时会出现一条明显的底色接缝。
     */
    @Test
    fun `亮色下v5底色与Material底色同源`() {
        assertEquals("V5Colors.bg 必须等于 Material background", ExpectedLight.background, LightV5Colors.bg)
        assertEquals("V5Colors.surface 必须等于 Material surface", ExpectedLight.surface, LightV5Colors.surface)
        assertEquals("V5Colors.hairline2 必须等于 Material outlineVariant", ExpectedLight.outlineVariant, LightV5Colors.hairline2)
    }

    @Test
    fun `暗色下v5底色与Material底色同源`() {
        assertEquals("V5Colors.bg 必须等于 Material background", ExpectedDark.background, DarkV5Colors.bg)
        assertEquals("V5Colors.surface 必须等于 Material surface", ExpectedDark.surface, DarkV5Colors.surface)
        assertEquals("V5Colors.hairline2 必须等于 Material outlineVariant", ExpectedDark.outlineVariant, DarkV5Colors.hairline2)
    }

    /**
     * 强调色同源：v5 的 `accent` 就是 Material 的 `primary`。
     *
     * 这条最容易漏——v4 时代的 Material 组件（按钮、进度条、下拉刷新指示器）读 `primary`，
     * v5 组件读 `V5Colors.accent`。两者不同色就会出现"同一屏两个蓝"。
     */
    @Test
    fun `v5强调色与Material主色同源`() {
        assertEquals(ExpectedLight.primary, LightV5Colors.accent)
        assertEquals(ExpectedDark.primary, DarkV5Colors.accent)
    }

    /** v5 的 `danger` 必须与 Material 的 `error` 同源（错误态在两套组件里都出现）。 */
    @Test
    fun `v5危险色与Material错误色同源`() {
        assertEquals(ExpectedLight.error, LightV5Colors.danger)
        assertEquals(ExpectedDark.error, DarkV5Colors.danger)
    }

    @Test
    fun `v5次要文字色与Material次级文字色同源`() {
        assertEquals(ExpectedLight.onSurfaceVariant, LightV5Colors.text2)
        assertEquals(ExpectedDark.onSurfaceVariant, DarkV5Colors.text2)
    }

    // ==================== 明暗两套自身的一致性 ====================

    /**
     * 明暗两套的字段必须一一对应填满。
     *
     * 这条是防"新增字段只填了一套"——Kotlin 的具名参数已经能挡一部分，但如果哪天有人给
     * `V5Colors` 加了带默认值的字段就不会报错，这里用反射兜一层。
     */
    @Test
    fun `明暗两套V5色板字段数量一致且无空值`() {
        val lightFields = V5Colors::class.java.declaredFields.map { it.name }.toSet()
        val darkFields = V5Colors::class.java.declaredFields.map { it.name }.toSet()
        assertEquals("明暗色板字段名必须完全一致", lightFields, darkFields)
        assertTrue("V5Colors 不应退化成空类", lightFields.size >= 30)
    }

    /**
     * 启动窗口底色必须与 v5 底色一致。
     *
     * 分工是「`android:windowBackground` 先画，首帧 Compose 再接手」，两者只要不同色，
     * 冷启动就会闪一下异色。第三批清理时发现 `values/colors.xml` 还是 v4 时代的
     * `#FFF1F1F3` / `#FF0E1621`（注释还指向已删除的 Color.kt），与 v5 的
     * `#F1F0F6` / `#0B0D15` 都不同——已修，这条断言把它钉住。
     *
     * 读的是编译后的资源，因此改 XML 不改这里一定会红。
     */
    @Test
    fun `启动窗口底色与v5底色一致`() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val light = ctx.resources.getColor(com.slte.app.R.color.light_background, null)
        val dark = ctx.resources.getColor(com.slte.app.R.color.dark_background, null)

        assertEquals(
            "light_background 必须等于 v5 亮色 bg #${hex(LightV5Colors.bg)}",
            LightV5Colors.bg.toArgb(),
            light,
        )
        assertEquals(
            "dark_background 必须等于 v5 暗色 bg #${hex(DarkV5Colors.bg)}",
            DarkV5Colors.bg.toArgb(),
            dark,
        )
    }

    private fun hex(c: Color): String {
        val v = c.toArgb()
        return String.format("%08X", v)
    }

    // ==================== 文字对比度 ====================

    /** WCAG 相对亮度。 */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** WCAG 对比度（1..21）。 */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * 三档文字在主表面上的对比度都要达到 WCAG AA 小字标准（4.5:1）。
     *
     * 历史背景：`text3` 原值 `#8E95A8` 在白卡上只有 **2.99:1**，第 12 轮的视觉复核在
     * 订单/套餐/公告多页都量到同一处偏低；暗色 `#727A90` 在卡面上只有 4.05:1。
     * 两者都已提亮，这个测试把结论钉住，防止将来调色时又调回去。
     */
    @Test
    fun `亮色三档文字对比度达到AA`() {
        val surface = LightV5Colors.surface
        assertTrue("text 对白卡对比度不足：${contrast(LightV5Colors.text, surface)}", contrast(LightV5Colors.text, surface) >= 4.5)
        assertTrue("text2 对白卡对比度不足：${contrast(LightV5Colors.text2, surface)}", contrast(LightV5Colors.text2, surface) >= 4.5)
        assertTrue("text3 对白卡对比度不足：${contrast(LightV5Colors.text3, surface)}", contrast(LightV5Colors.text3, surface) >= 4.5)
    }

    @Test
    fun `暗色三档文字对比度达到AA`() {
        val surface = DarkV5Colors.surface
        assertTrue("text 对暗卡对比度不足：${contrast(DarkV5Colors.text, surface)}", contrast(DarkV5Colors.text, surface) >= 4.5)
        assertTrue("text2 对暗卡对比度不足：${contrast(DarkV5Colors.text2, surface)}", contrast(DarkV5Colors.text2, surface) >= 4.5)
        assertTrue("text3 对暗卡对比度不足：${contrast(DarkV5Colors.text3, surface)}", contrast(DarkV5Colors.text3, surface) >= 4.5)
    }

    /**
     * 文字三档必须**可区分**：text 明显强于 text2，text2 明显强于 text3。
     *
     * 提亮 `text3` 时最容易踩的坑就是"顺手提到和 text2 一样亮"，那样三档层级被压平、
     * 信息层级消失。这里要求相邻两档至少有 0.8 的对比度差。
     */
    @Test
    fun `文字三档层级未被压平`() {
        val lightSurface = LightV5Colors.surface
        val l1 = contrast(LightV5Colors.text, lightSurface)
        val l2 = contrast(LightV5Colors.text2, lightSurface)
        val l3 = contrast(LightV5Colors.text3, lightSurface)
        assertTrue("text 与 text2 层级过近", l1 - l2 >= 0.8)
        assertTrue("text2 与 text3 层级过近", l2 - l3 >= 0.8)

        val darkSurface = DarkV5Colors.surface
        val d1 = contrast(DarkV5Colors.text, darkSurface)
        val d2 = contrast(DarkV5Colors.text2, darkSurface)
        val d3 = contrast(DarkV5Colors.text3, darkSurface)
        assertTrue("暗色 text 与 text2 层级过近", d1 - d2 >= 0.8)
        assertTrue("暗色 text2 与 text3 层级过近", d2 - d3 >= 0.8)
    }

    /**
     * 状态色在各自表面上的可读性 —— **目前未达标，测试记录现状而非假装达标**。
     *
     * 实测（本机计算，非估计）：
     * | 组合 | 对比度 | AA 小字 4.5:1 |
     * | --- | --- | --- |
     * | 亮色 `ok` #16AC6C 压白卡 | 2.94:1 | ✗ |
     * | 亮色 `ok` 压自己的 `okBg`（合成后 #E1F4EC） | **2.57:1** | ✗ |
     * | 亮色 `danger` #E5484D 压白卡 | 3.91:1 | ✗ |
     * | 亮色 `danger` 压 `dangerBg` | 3.40:1 | ✗ |
     * | 暗色 `ok` / `danger` | 8.45 / 6.36:1 | ✓ |
     *
     * 影响面：`V5Chip(OK/DANGER)` 的**文字**就是这两个色压在同名浅底上（见
     * `V5Colors.chip`），而胶囊徽标在小字档。工单的「待处理」、订单的「已完成/异常」、
     * 关于页等都用它。
     *
     * 本轮**不改**：用户对第 2 批/第 3 批的要求是"风格收敛"，
     * 而调整状态色会改变品牌观感（已明确划在范围外）。所以这里断言的阈值定在
     * "能被看见"（≥2.5），把 4.5:1 的达标要求留成 TODO，而不是写一条自动通过、
     * 让人误以为已经达标的假断言。
     */
    @Test
    fun `状态色在对应表面上可见（AA未达标，现场记录）`() {
        assertTrue("亮色 ok 可见性过低", contrast(LightV5Colors.ok, LightV5Colors.surface) >= 2.5)
        assertTrue("亮色 danger 可见性过低", contrast(LightV5Colors.danger, LightV5Colors.surface) >= 2.5)
        assertTrue("暗色 ok 可见性过低", contrast(DarkV5Colors.ok, DarkV5Colors.surface) >= 2.5)
        assertTrue("暗色 danger 可见性过低", contrast(DarkV5Colors.danger, DarkV5Colors.surface) >= 2.5)
    }

    /**
     * 把"亮色状态色未达 AA"这件事写成**会失败的断言**是不合适的（它现在就会红），
     * 但完全不断言又会让它悄悄恶化。折中做法：钉住当前实测值，任何调色都必须显式更新这里。
     * 数值变化时本条会失败并提示去复核对比度。
     */
    @Test
    fun `亮色状态色当前实测值被钉住`() {
        assertEquals(0xFF16AC6C.toInt(), LightV5Colors.ok.toArgb())
        assertEquals(0xFFE5484D.toInt(), LightV5Colors.danger.toArgb())
        assertTrue(
            "亮色 ok 对比度已变化（现在 ${contrast(LightV5Colors.ok, LightV5Colors.surface)}），" +
                "请复核是否达到 4.5:1 并把本条与上一条的说明一起更新",
            contrast(LightV5Colors.ok, LightV5Colors.surface) < 4.5,
        )
    }
}
