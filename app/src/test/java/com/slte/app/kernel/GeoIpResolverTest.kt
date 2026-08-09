package com.slte.app.kernel

import com.maxmind.db.Reader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress

class GeoIpResolverTest {

    private val metadb = File("src/main/assets/geoip.metadb")

    @Test
    fun `metadb 标量与数组 record 均能解码国家码`() {
        assumeTrue("metadb 文件不存在，跳过", metadb.exists())
        Reader(metadb).use { reader ->
            // 标量 record：114.114.114.114 → cn
            val scalar = reader.get(InetAddress.getByName("114.114.114.114"), String::class.java)
            assertEquals("cn", scalar)
            // 数组 record：8.8.8.8 → [us, google]，首元素为国家码
            val arr = reader.get(InetAddress.getByName("8.8.8.8"), List::class.java)
            assertEquals("us", (arr as? List<*>)?.firstOrNull())
            // 私有网段：数据返回 "private" 标记（非两字母码，Resolver 的长度过滤会剔除）
            assertEquals("private", reader.get(InetAddress.getByName("10.0.0.1"), String::class.java))
        }
    }
}
