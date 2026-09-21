package com.slte.app.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceLocaleParityTest {

    private fun resDir(): File {
        var dir = File("src/main/res")
        if (dir.isDirectory) return dir
        dir = File("app/src/main/res")
        if (dir.isDirectory) return dir
        var probe: File? = File(System.getProperty("user.dir"))
        while (probe != null) {
            val candidate = File(probe, "app/src/main/res")
            if (candidate.isDirectory) return candidate
            probe = probe.parentFile
        }
        error("找不到 app/src/main/res")
    }

    private fun stringNames(dir: File): Map<String, String> {
        val text = File(dir, "strings.xml").readText()
        return Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun localeDirs(): List<File> = resDir()
        .listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").isFile }
        .orEmpty()
        .sortedBy { it.name }

    @Test
    fun `各语言字符串键集合一致`() {
        val dirs = localeDirs()
        assertTrue("未找到任何 values 目录", dirs.isNotEmpty())
        val baseline = stringNames(File(resDir(), "values"))
        dirs.forEach { dir ->
            val names = stringNames(dir)
            val missing = baseline.keys - names.keys
            val extra = names.keys - baseline.keys
            assertEquals("$dir 缺少字符串: $missing", emptyList<String>(), missing.toList().sorted())
            assertEquals("$dir 多出字符串: $extra", emptyList<String>(), extra.toList().sorted())
        }
    }

    @Test
    fun `货币符号不因语言而多出文字`() {
        val symbols = localeDirs().map { it.name to (stringNames(it)["currency_symbol"] ?: "") }
        val distinct = symbols.map { it.second }.distinct()
        assertEquals("货币符号在各语言下应一致，实际: $symbols", 1, distinct.size)
        assertTrue("货币符号不应包含多余文字: $symbols", distinct.first().none { it.isLetterOrDigit() })
    }
}
