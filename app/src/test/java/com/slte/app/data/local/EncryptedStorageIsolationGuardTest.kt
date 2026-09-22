// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedStorageIsolationGuardTest {
    @Test
    fun `弃用的加密存储 API 只允许被 SecurePreferences 引用`() {
        val root = File("src/main/java")
        assertTrue("源码目录不存在: ${root.absolutePath}", root.isDirectory)

        val offenders = scan(root)

        assertTrue(
            "EncryptedSharedPreferences/MasterKey 已弃用且无 drop-in 替代，迁移方案确定前只允许出现在 $ALLOWED_PATH（引用位置：\n${offenders.joinToString("\n")}）",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `护栏能发现 SecurePreferences 之外的引用`() {
        val root = tempRoot()
        write(root, "com/slte/app/data/local/OtherStore.kt", "import androidx.security.crypto.EncryptedSharedPreferences\n")

        assertEquals(1, scan(root).size)
    }

    @Test
    fun `护栏能发现挪到其它包的同名文件`() {
        val root = tempRoot()
        write(root, "com/slte/app/ui/storage/SecurePreferences.kt", "val key = MasterKey.Builder(context, alias).build()\n")

        assertEquals(1, scan(root).size)
    }

    @Test
    fun `护栏不误报授权文件`() {
        val root = tempRoot()
        write(root, "com/slte/app/data/local/SecurePreferences.kt", "import androidx.security.crypto.EncryptedSharedPreferences\n")

        assertTrue(scan(root).isEmpty())
    }

    @Test
    fun `去掉允许列表后能扫到授权文件里的弃用 API`() {
        val root = File("src/main/java")

        val hits = scan(root, allowSecurePreferences = false)

        assertTrue(
            "SecurePreferences.kt 应仍在引用弃用 API（若已完成迁移，请连同本护栏一起更新）：$hits",
            hits.any { it.contains(ALLOWED_PATH) },
        )
    }

    private fun scan(
        root: File,
        allowSecurePreferences: Boolean = true,
    ): List<String> {
        val offenders = mutableListOf<String>()
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { allowSecurePreferences && it.invariantSeparatorsPath.endsWith(ALLOWED_PATH) }
            .forEach { file ->
                file
                    .readLines()
                    .withIndex()
                    .filter { (_, line) -> DEPRECATED_SYMBOLS.any { line.contains(it) } }
                    .forEach { (index, line) ->
                        offenders.add("${file.invariantSeparatorsPath}:${index + 1}  ${line.trim()}")
                    }
            }
        return offenders
    }

    private fun tempRoot(): File = Files.createTempDirectory("encrypted-storage-guard").toFile()

    private fun write(
        root: File,
        relativePath: String,
        content: String,
    ) {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private companion object {
        val DEPRECATED_SYMBOLS = listOf("EncryptedSharedPreferences", "MasterKey")
        const val ALLOWED_PATH = "data/local/SecurePreferences.kt"
    }
}
