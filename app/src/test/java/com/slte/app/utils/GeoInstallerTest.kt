package com.slte.app.utils

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private typealias Opener = () -> InputStream

class GeoInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun entries(vararg pairs: Pair<String, String>): Map<String, Opener> = pairs.associate { (name, content) ->
        name to { ByteArrayInputStream(content.toByteArray()) }
    }

    @Test
    fun `首次安装写入全部文件与版本标记`() {
        val dir = temp.newFolder()

        GeoInstaller.install(dir, 1L, entries("geoip.metadb" to "geo", "geosite.dat" to "site"))

        assertEquals("geo", File(dir, "geoip.metadb").readText())
        assertEquals("site", File(dir, "geosite.dat").readText())
        assertTrue(GeoInstaller.isUpToDate(dir, 1L, listOf("geoip.metadb", "geosite.dat")))
        assertFalse(File(dir, ".geo-version.tmp").exists())
    }

    @Test
    fun `版本与文件大小一致时跳过高价解包`() {
        val dir = temp.newFolder()
        var opened = 0
        val source: Map<String, () -> InputStream> =
            mapOf(
                "geoip.metadb" to {
                    opened++
                    ByteArrayInputStream("geo".toByteArray())
                },
            )

        GeoInstaller.install(dir, 1L, source)
        opened = 0
        GeoInstaller.install(dir, 1L, source)

        assertEquals(0, opened)
    }

    @Test
    fun `版本变化时重新解包`() {
        val dir = temp.newFolder()
        GeoInstaller.install(dir, 1L, entries("geoip.metadb" to "v1"))

        GeoInstaller.install(dir, 2L, entries("geoip.metadb" to "v2"))

        assertEquals("v2", File(dir, "geoip.metadb").readText())
        assertTrue(GeoInstaller.isUpToDate(dir, 2L, listOf("geoip.metadb")))
    }

    @Test
    fun `只清理自己的临时文件`() {
        val dir = temp.newFolder()
        val unrelated = File(dir, "other.tmp").apply { writeText("keep") }

        GeoInstaller.install(dir, 1L, entries("geoip.metadb" to "geo"))

        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun `文件被截断后按大小失配重新解包`() {
        val dir = temp.newFolder()
        GeoInstaller.install(dir, 1L, entries("geoip.metadb" to "complete"))
        File(dir, "geoip.metadb").writeText("cut")

        assertFalse(GeoInstaller.isUpToDate(dir, 1L, listOf("geoip.metadb")))

        var opened = 0
        GeoInstaller.install(
            dir,
            1L,
            mapOf(
                "geoip.metadb" to {
                    opened++
                    ByteArrayInputStream("complete".toByteArray())
                },
            ),
        )

        assertEquals(1, opened)
        assertEquals("complete", File(dir, "geoip.metadb").readText())
    }

    @Test
    fun `写入中途失败不会破坏旧文件`() {
        val dir = temp.newFolder()
        GeoInstaller.install(dir, 1L, entries("geoip.metadb" to "old"))

        val failing =
            object : InputStream() {
                private var read = 0

                override fun read(): Int {
                    if (read++ > 2) throw IOException("disk full")
                    return 'x'.code
                }
            }

        val result = runCatching {
            GeoInstaller.install(dir, 2L, mapOf("geoip.metadb" to { failing }))
        }

        assertTrue(result.isFailure)
        assertEquals("old", File(dir, "geoip.metadb").readText())
        assertFalse(File(dir, "geoip.metadb.tmp").exists())
        assertFalse(GeoInstaller.isUpToDate(dir, 2L, listOf("geoip.metadb")))
    }
}
