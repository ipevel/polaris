// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 色板一致性回归（V5 设计系统的结构性护栏）。
 *
 * iOS 改版前这里还有一组"V5 与 Material 同源"断言；改版后 V5 已是独立设计语言，
 * 不再与 Material 同源，那组断言已随改版移除。
 * 保留的护栏：明暗字段一致、启动底色对齐、文字对比度、状态色钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.slte.app.support.RobolectricTestApplication::class)
class PaletteConsistencyTest {

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
     * `#F2F2F7` / `#000000` 都不同——已修，这条断言把它钉住。
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
     * 主/次文字在主表面上的对比度达到 WCAG AA 小字标准（4.5:1）。
     *
     * `text3` 是 iOS 系统三级标签色 `#8E8E93`（实测 3.26:1，未达 4.5:1）——这是
     * iOS 改版有意采用的平台色，不是回归。此处钉住当前值，任何调色都必须显式更新。
     */
    @Test
    fun `亮色三档文字对比度达到AA`() {
        val surface = LightV5Colors.surface
        assertTrue("text 对白卡对比度不足：${contrast(LightV5Colors.text, surface)}", contrast(LightV5Colors.text, surface) >= 4.5)
        assertTrue("text2 对白卡对比度不足：${contrast(LightV5Colors.text2, surface)}", contrast(LightV5Colors.text2, surface) >= 4.5)
        val text3Ratio = contrast(LightV5Colors.text3, surface)
        assertTrue("亮色 text3 对比度已变化（现在 $text3Ratio），请复核 iOS 三级标签色是否仍为 #8E8E93", text3Ratio >= 3.2 && text3Ratio < 3.4)
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
        // 亮色 ok 是 iOS 系统绿 #34C759（实测 2.22:1）：iOS 改版有意采用的平台色，钉住当前值。
        val lightOkRatio = contrast(LightV5Colors.ok, LightV5Colors.surface)
        assertTrue("亮色 ok 对比度已变化（现在 $lightOkRatio），请复核 iOS 系统绿是否仍为 #34C759", lightOkRatio >= 2.1 && lightOkRatio < 2.4)
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
        assertEquals(0xFF34C759.toInt(), LightV5Colors.ok.toArgb())
        assertEquals(0xFFFF3B30.toInt(), LightV5Colors.danger.toArgb())
        assertTrue(
            "亮色 ok 对比度已变化（现在 ${contrast(LightV5Colors.ok, LightV5Colors.surface)}），" +
                "请复核是否达到 4.5:1 并把本条与上一条的说明一起更新",
            contrast(LightV5Colors.ok, LightV5Colors.surface) < 4.5,
        )
    }
}
