package com.slte.app.kernel

import com.github.kr328.clash.service.util.AtomicFs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `替换目录后只保留新内容`() {
        val root = temp.newFolder()
        val target =
            File(root, "uuid").apply {
                mkdirs()
                resolve("config.yaml").writeText("old")
            }
        val staging =
            File(root, "uuid.tmp").apply {
                mkdirs()
                resolve("config.yaml").writeText("new")
            }

        AtomicFs.replaceDirectory(staging, target)

        assertEquals("new", File(target, "config.yaml").readText())
        assertFalse(staging.exists())
        assertFalse(File(root, "uuid.old").exists())
    }

    @Test
    fun `目标缺失时从备份恢复`() {
        val root = temp.newFolder()
        val backup =
            File(root, "uuid.old").apply {
                mkdirs()
                resolve("config.yaml").writeText("old")
            }

        AtomicFs.recover(root)

        val target = File(root, "uuid")
        assertTrue(target.isDirectory)
        assertEquals("old", File(target, "config.yaml").readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `目标存在时丢弃过期备份`() {
        val root = temp.newFolder()
        File(root, "uuid").apply {
            mkdirs()
            resolve("config.yaml").writeText("current")
        }
        val backup =
            File(root, "uuid.old").apply {
                mkdirs()
                resolve("config.yaml").writeText("stale")
            }

        AtomicFs.recover(root)

        assertEquals("current", File(root, "uuid/config.yaml").readText())
        assertFalse(backup.exists())
    }
}
