package com.slte.app.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetSubtitleConventionTest {

    private fun uiDir(): File {
        var dir = File("src/main/java/com/slte/app/ui")
        if (dir.isDirectory) return dir
        dir = File("app/src/main/java/com/slte/app/ui")
        if (dir.isDirectory) return dir
        var probe: File? = File(System.getProperty("user.dir"))
        while (probe != null) {
            val candidate = File(probe, "app/src/main/java/com/slte/app/ui")
            if (candidate.isDirectory) return candidate
            probe = probe.parentFile
        }
        error("找不到 app/src/main/java/com/slte/app/ui")
    }

    private fun violations(): List<String> {
        val found = mutableListOf<String>()
        val contentPattern =
            Regex(
                "Text\\(\\s*\\n?\\s*text = stringResource\\(R\\.string\\.(\\w+)\\)" +
                    "[\\s\\S]{0,160}?style = SlteType\\.bodySmall",
            )
        uiDir().walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val source = file.readText()
            Regex("SlteSheet\\(([\\s\\S]{0,500}?)\\{([\\s\\S]{0,400})").findAll(source).forEach { m ->
                val head = m.groupValues[1]
                val body = m.groupValues[2]
                if (head.contains("subtitle")) return@forEach
                val hit = contentPattern.find(body)
                if (hit != null) {
                    val name = hit.groupValues[1]
                    val line = source.take(m.range.first).count { it == '\n' } + 1
                    found += "${file.path}:$line 用内容模拟副标题（$name），应改用 SlteSheet(subtitle = …)"
                }
            }
        }
        return found
    }

    @Test
    fun `弹窗副标题统一走 subtitle 参数`() {
        val found = violations()
        assertTrue("副标题间距不一致的来源：\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `SlteSheet 提供 subtitle 参数`() {
        val sheet = uiDir().walkTopDown().first { it.name == "SlteSheet.kt" }
        val source = sheet.readText()
        assertTrue("SlteSheet 应保留 subtitle 参数以统一副标题间距", source.contains("subtitle: String?"))
        assertTrue("SlteSheet 应使用统一的副标题间距", source.contains("Dimens.gap.sm"))
    }

    @Test
    fun `退出弹窗使用 subtitle`() {
        val file = uiDir().walkTopDown().first { it.name == "ProfileSheets.kt" }
        val source = file.readText()
        val block = Regex("SlteSheet\\(([\\s\\S]{0,300}?)\\{").find(source)?.groupValues?.get(1).orEmpty()
        assertEquals("退出弹窗应通过 subtitle 渲染说明文字", true, block.contains("subtitle ="))
    }
}
