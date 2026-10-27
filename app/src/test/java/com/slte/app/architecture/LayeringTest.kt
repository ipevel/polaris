package com.slte.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分层约束：源码依赖只能向内。
 * domain 不依赖 Android、不依赖 data/ui；kernel/api 不依赖任何实现。
 */
class LayeringTest {
    private val src = File("src/main/java/com/slte/app")

    private fun filesIn(pkg: String): List<File> = File(src, pkg).walkTopDown().filter { it.extension == "kt" }.toList()

    private fun importsOf(file: File): List<String> = file.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

    @Test
    fun `domain 层不依赖 Android`() {
        val offenders =
            filesIn("domain").flatMap { f ->
                importsOf(f).filter { it.startsWith("android.") || it.startsWith("androidx.") }.map { "${f.name}: $it" }
            }
        assertTrue("domain 层出现 Android 依赖:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `domain 层不依赖外层`() {
        val forbidden = listOf("com.slte.app.data.", "com.slte.app.ui.", "com.slte.app.kernel.")
        val offenders =
            filesIn("domain").flatMap { f ->
                importsOf(f).filter { imp -> forbidden.any { imp.startsWith(it) } }.map { "${f.name}: $it" }
            }
        assertTrue("domain 层反向依赖:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `kernel api 层不依赖具体实现`() {
        val forbidden = listOf("com.slte.app.kernel.binder.", "com.slte.app.kernel.mihomo.", "com.slte.app.kernel.factory.")
        val offenders =
            filesIn("kernel/api").flatMap { f ->
                importsOf(f).filter { imp -> forbidden.any { imp.startsWith(it) } }.map { "${f.name}: $it" }
            }
        assertTrue("kernel/api 出现实现依赖:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `ui 层不直接调用内核行为接口`() {
        // 值模型（KernelType/KernelVpnState 等）可直用；行为接口必须经 KernelProxy
        val forbidden =
            listOf(
                "com.slte.app.kernel.api.KernelApi",
                "com.slte.app.kernel.api.KernelClash",
                "com.slte.app.kernel.api.KernelProfiles",
                "com.slte.app.kernel.binder.",
                "com.slte.app.kernel.factory.",
            )
        val offenders =
            filesIn("ui").flatMap { f ->
                importsOf(f).filter { imp -> forbidden.any { imp == it || imp.startsWith("$it.") } }.map { "${f.name}: $it" }
            }
        assertTrue("UI 层绕过 KernelProxy:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
