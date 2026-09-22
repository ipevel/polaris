// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.maxmind.db.Reader
import java.io.File
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class GeoIpResolverTest {
    private val metadb = File("src/main/assets/geoip.metadb")

    @Test
    fun `metadb 标量与数组 record 均能解码国家码`() {
        assumeTrue("metadb 文件不存在，跳过", metadb.exists())
        Reader(metadb).use { reader ->

            val scalar = reader.get(InetAddress.getByName("114.114.114.114"), String::class.java)
            assertEquals("cn", scalar)

            val arr = reader.get(InetAddress.getByName("8.8.8.8"), List::class.java)
            assertEquals("us", (arr as? List<*>)?.firstOrNull())

            assertEquals("private", reader.get(InetAddress.getByName("10.0.0.1"), String::class.java))
        }
    }
}
