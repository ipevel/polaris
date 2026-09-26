// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

/**
 * 信息伪节点剔除。
 *
 * 面板把账号信息伪装成**真代理**塞进 `proxies:`（名字就是说明文本，且与某个真节点共用
 * server/port/uuid），再 include-all 并入所有策略组。后果是真机实测到的：
 * 「已连接 · 剩余流量：3983.45 GB · 249 ms」但速率 0B/s——内核 url-test 真的选中了它。
 *
 * 判定双重条件：名字像信息条目 **且** 与同文档另一条代理端点完全重复。
 */
class SanitizerInfoProxyDropperTest {

    private fun apply(yaml: String): Pair<Int, String> {
        val lines = yaml.trimMargin().lines().toMutableList()
        val count = SanitizerInfoProxyDropper.dropInfoProxies(lines)
        return count to lines.joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun doc(yaml: String) = Yaml().load<Any?>(yaml.trimMargin()) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun proxyNames(yaml: String): List<String> =
        (doc(yaml)["proxies"] as List<Map<String, Any?>>).map { it["name"] as String }

    @Suppress("UNCHECKED_CAST")
    private fun groupProxies(
        yaml: String,
        group: String,
    ): List<String> =
        (doc(yaml)["proxy-groups"] as List<Map<String, Any?>>)
            .first { it["name"] == group }["proxies"] as List<String>

    private fun flowProxy(
        name: String,
        server: String = "a.example.com",
        port: Int = 443,
        uuid: String = "u-1",
    ) = "  - { name: $name, type: vless, server: $server, port: $port, uuid: $uuid }"

    /** 复刻真实订阅形态：3 条信息伪节点与「🇺🇸 美国」共用同一端点。 */
    private val realWorld =
        """
        |mixed-port: 7890
        |proxies:
        |${flowProxy("'剩余流量：3983.45 GB'")}
        |${flowProxy("'距离下次重置剩余：5 天'")}
        |${flowProxy("套餐到期：2029-04-20")}
        |${flowProxy("'🇺🇸 美国'")}
        |${flowProxy("'🇭🇰 香港'", server = "b.example.com", port = 8443, uuid = "u-2")}
        |proxy-groups:
        |  - { name: '🚀 节点选择', type: select, proxies: ['剩余流量：3983.45 GB', '距离下次重置剩余：5 天', 套餐到期：2029-04-20, '🇺🇸 美国', '🇭🇰 香港'] }
        |  - { name: '📲 电报消息', type: select, proxies: ['🚀 节点选择', '剩余流量：3983.45 GB', '🇺🇸 美国'] }
        |rules:
        |  - MATCH,🚀 节点选择
        |
        """.trimMargin()

    // --- 主场景 ---

    @Test
    fun `flow 写法：端点重复的信息伪节点被剔除且真节点保留`() {
        val (count, out) = apply(realWorld)
        assertEquals(3, count)
        assertEquals(listOf("🇺🇸 美国", "🇭🇰 香港"), proxyNames(out))
    }

    @Test
    fun `剔除后全部分组都不再引用信息伪节点（否则内核会拒绝加载）`() {
        val (_, out) = apply(realWorld)
        assertEquals(listOf("🇺🇸 美国", "🇭🇰 香港"), groupProxies(out, "🚀 节点选择"))
        assertEquals(listOf("🚀 节点选择", "🇺🇸 美国"), groupProxies(out, "📲 电报消息"))
    }

    @Test
    fun `块状写法同样生效`() {
        val src =
            """
            |proxies:
            |  - name: 剩余流量：3983.45 GB
            |    type: vless
            |    server: a.example.com
            |    port: 443
            |    uuid: u-1
            |  - name: 🇺🇸 美国
            |    type: vless
            |    server: a.example.com
            |    port: 443
            |    uuid: u-1
            |  - name: 🇭🇰 香港
            |    type: ss
            |    server: b.example.com
            |    port: 8443
            |proxy-groups:
            |  - name: 节点选择
            |    type: select
            |    proxies:
            |      - 剩余流量：3983.45 GB
            |      - 🇺🇸 美国
            |      - 🇭🇰 香港
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(1, count)
        assertEquals(listOf("🇺🇸 美国", "🇭🇰 香港"), proxyNames(out))
        assertEquals(listOf("🇺🇸 美国", "🇭🇰 香港"), groupProxies(out, "节点选择"))
    }

    // --- 零误杀保护 ---

    @Test
    fun `独享端点的真节点即使名字含全角冒号也不动`() {
        val src =
            """
            |proxies:
            |${flowProxy("'🇭🇰 香港：01'", server = "h1.example.com", uuid = "x-1")}
            |${flowProxy("'🇯🇵 日本'", server = "j1.example.com", uuid = "x-2")}
            |proxy-groups:
            |  - { name: '选择', type: select, proxies: ['🇭🇰 香港：01', '🇯🇵 日本'] }
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(0, count)
        assertEquals(src.trimmed(), out)
    }

    @Test
    fun `端点重复但两条都是正常节点名时不删`() {
        val src =
            """
            |proxies:
            |${flowProxy("'🇺🇸 美国 A'", server = "n.example.com", uuid = "y-1")}
            |${flowProxy("'🇺🇸 美国 B'", server = "n.example.com", uuid = "y-1")}
            |proxy-groups:
            |  - { name: '选择', type: select, proxies: ['🇺🇸 美国 A', '🇺🇸 美国 B'] }
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(0, count)
        assertEquals(src.trimmed(), out)
    }

    @Test
    fun `同一端点全是信息条目时不动（无从判断该保留哪条）`() {
        val src =
            """
            |proxies:
            |${flowProxy("'剩余流量：1 GB'", server = "z.example.com", uuid = "z-1")}
            |${flowProxy("'套餐到期：2029-01-01'", server = "z.example.com", uuid = "z-1")}
            |proxy-groups:
            |  - { name: '选择', type: select, proxies: ['剩余流量：1 GB', '套餐到期：2029-01-01'] }
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(0, count)
        assertEquals(src.trimmed(), out)
    }

    @Test
    fun `剔除会让某个分组变空时整步放弃`() {
        val src =
            """
            |proxies:
            |${flowProxy("'剩余流量：1 GB'", server = "q.example.com", uuid = "q-1")}
            |${flowProxy("'🇺🇸 美国'", server = "q.example.com", uuid = "q-1")}
            |proxy-groups:
            |  - { name: '只剩信息节点', type: select, proxies: ['剩余流量：1 GB'] }
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals("宁可留下伪节点，也不产出内核会拒绝加载的空组", 0, count)
        assertEquals(src.trimmed(), out)
    }

    // --- 稳定性 ---

    @Test
    fun `幂等`() {
        val (_, once) = apply(realWorld)
        val lines = once.lines().toMutableList()
        assertEquals(0, SanitizerInfoProxyDropper.dropInfoProxies(lines))
        assertEquals(once, lines.joinToString("\n"))
    }

    @Test
    fun `没有 proxies 块时安全返回`() {
        val src =
            """
            |rules:
            |  - MATCH,DIRECT
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(0, count)
        assertEquals(src.trimmed(), out)
    }

    @Test
    fun `只有一条代理时短路返回`() {
        val src =
            """
            |proxies:
            |${flowProxy("'剩余流量：1 GB'")}
            |
            """.trimMargin()
        val (count, out) = apply(src)
        assertEquals(0, count)
        assertEquals(src.trimmed(), out)
    }

    // --- 名字判定（纯函数） ---

    @Test
    fun `名字判定表`() {
        assertTrue(SanitizerInfoProxyDropper.isInfoLikeName("剩余流量：3983.45 GB"))
        assertTrue(SanitizerInfoProxyDropper.isInfoLikeName("距离下次重置剩余：5 天"))
        assertTrue(SanitizerInfoProxyDropper.isInfoLikeName("套餐到期：2029-04-20"))
        assertTrue(SanitizerInfoProxyDropper.isInfoLikeName("剩余流量 3983.45 GB"))
        assertTrue(SanitizerInfoProxyDropper.isInfoLikeName("官网：example.com"))
        assertFalse(SanitizerInfoProxyDropper.isInfoLikeName("🇺🇸 ＵＳ · LA〔4837 0.5x〕"))
        assertFalse(SanitizerInfoProxyDropper.isInfoLikeName("🇭🇰 ＨＫ · C〔4837/CMI/163 2x〕"))
        assertFalse(SanitizerInfoProxyDropper.isInfoLikeName("香港 01"))
    }

    // --- 端到端：清洗后的配置仍可被内核加载 ---

    @Test
    fun `端到端清洗后配置结构合法且引用无悬空`() {
        val out = SubscriptionSanitizer.sanitize(realWorld, listOf("example.com"))
        assertTrue("清洗不应整体失败", out.isNotBlank())

        val names = proxyNames(out)
        assertEquals(listOf("🇺🇸 美国", "🇭🇰 香港"), names)

        // 全部分组引用的成员必须都能在 proxies 或组名里找到（悬空引用会让内核拒绝加载）
        @Suppress("UNCHECKED_CAST")
        val groups = doc(out)["proxy-groups"] as List<Map<String, Any?>>
        val groupNames = groups.map { it["name"] as String }
        val resolvable = names.toSet() + groupNames.toSet() + setOf("DIRECT", "REJECT", "GLOBAL")
        groups.forEach { group ->
            @Suppress("UNCHECKED_CAST")
            val refs = group["proxies"] as List<String>
            assertTrue("分组 ${group["name"]} 引用了不存在的代理: ${refs - resolvable}", refs.all { it in resolvable })
        }
    }

    private fun String.trimmed(): String = trimMargin()
}
