// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

/**
 * 重名节点自愈：内核 parseProxies 遇到同名的 proxy 会直接报
 * `proxy X is the duplicate name` 并拒绝加载整份配置，所以清洗阶段要把重名改成唯一名。
 */
class SanitizerNameDeduperTest {

    private fun dedupe(yaml: String): Pair<Int, String> {
        val lines = yaml.trimMargin().lines().toMutableList()
        val count = SanitizerNameDeduper.dedupeProxyNames(lines)
        return count to lines.joinToString("\n")
    }

    private fun proxyNames(yaml: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        val doc = Yaml().load<Any?>(yaml.trimMargin()) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val proxies = doc["proxies"] as List<Map<String, Any?>>
        return proxies.map { it["name"] as String }
    }

    private fun proxy(
        name: String,
        indent: String = "  ",
    ) = "$indent- name: $name\n$indent  type: ss\n$indent  server: 1.2.3.4\n$indent  port: 8388"

    // --- 基础行为 ---

    @Test
    fun `无重名时不做任何改动`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("B")}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(0, count)
        assertEquals(src.trimMargin(), out)
    }

    @Test
    fun `第二个同名追加序号且首个保持原名`() {
        val src =
            """
            |proxies:
            |${proxy("香港 01")}
            |${proxy("香港 01")}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(1, count)
        assertEquals(listOf("香港 01", "香港 01 (2)"), proxyNames(out))
    }

    @Test
    fun `三个同名按出现顺序依次追加序号`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("A")}
            |${proxy("A")}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(2, count)
        assertEquals(listOf("A", "A (2)", "A (3)"), proxyNames(out))
    }

    @Test
    fun `后缀已被其它节点占用时顺延`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("A (2)")}
            |${proxy("A")}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(1, count)
        assertEquals(listOf("A", "A (2)", "A (3)"), proxyNames(out))
    }

    @Test
    fun `重复执行结果一致（幂等）`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("A")}
            |
            """.trimMargin()
        val (_, once) = dedupe(src)
        val lines = once.lines().toMutableList()
        assertEquals(0, SanitizerNameDeduper.dedupeProxyNames(lines))
        assertEquals(once, lines.joinToString("\n"))
    }

    // --- 书写形式 ---

    @Test
    fun `保留原有引号风格`() {
        val src =
            """
            |proxies:
            |${proxy("\"A\"")}
            |${proxy("\"A\"")}
            |
            """.trimMargin()
        val (_, out) = dedupe(src)
        assertTrue("双引号应保留", out.contains("- name: \"A (2)\""))
        assertEquals(listOf("A", "A (2)"), proxyNames(out))
    }

    @Test
    fun `保留行尾注释`() {
        val src =
            """
            |proxies:
            |  - name: A # 主节点
            |    type: ss
            |  - name: A # 备用
            |    type: ss
            |
            """.trimMargin()
        val (_, out) = dedupe(src)
        assertTrue("注释应保留", out.contains("- name: A (2) # 备用"))
    }

    @Test
    fun `条目内部的嵌套 name 键不被改写`() {
        val src =
            """
            |proxies:
            |  - name: A
            |    type: ws
            |    ws-opts:
            |      headers:
            |        name: inner
            |  - name: A
            |    type: ws
            |    ws-opts:
            |      headers:
            |        name: inner
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(1, count)
        assertEquals(2, Regex("^\\s*name: inner$", RegexOption.MULTILINE).findAll(out).count())
        assertTrue(out.contains("- name: A (2)"))
    }

    @Test
    fun `flow 风格条目安全跳过而不抛异常`() {
        val src =
            """
            |proxies:
            |  - {name: A, type: ss, server: 1.2.3.4, port: 8388}
            |  - {name: A, type: ss, server: 5.6.7.8, port: 8388}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(0, count)
        assertEquals(src.trimMargin(), out)
    }

    // --- 名字空间冲突 ---

    @Test
    fun `避开内核预注册的保留名`() {
        val src =
            """
            |proxies:
            |${proxy("DIRECT")}
            |${proxy("DIRECT")}
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(1, count)
        assertEquals(listOf("DIRECT", "DIRECT (2)"), proxyNames(out))
    }

    @Test
    fun `避开策略组名（代理与组同名同样会让内核报错）`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("A")}
            |proxy-groups:
            |  - name: "A (2)"
            |    type: select
            |    proxies:
            |      - A
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(1, count)
        assertEquals(listOf("A", "A (3)"), proxyNames(out))
    }

    @Test
    fun `策略组引用与规则引用保持指向首个节点`() {
        val src =
            """
            |proxies:
            |${proxy("A")}
            |${proxy("A")}
            |proxy-groups:
            |  - name: 选择
            |    type: select
            |    proxies:
            |      - A
            |rules:
            |  - MATCH,A
            |
            """.trimMargin()
        val (_, out) = dedupe(src)
        assertTrue(out.contains("      - A\n"))
        assertTrue(out.contains("- MATCH,A"))
    }

    @Test
    fun `没有 proxies 块时安全返回`() {
        val src =
            """
            |rules:
            |  - MATCH,DIRECT
            |
            """.trimMargin()
        val (count, out) = dedupe(src)
        assertEquals(0, count)
        assertEquals(src.trimMargin(), out)
    }

    // --- 端到端：清洗后的配置可被内核加载 ---

    @Test
    fun `清洗后重名唯一且节点数量不变`() {
        val src =
            """
            |mixed-port: 7890
            |proxies:
            |${proxy("香港 01")}
            |${proxy("香港 01")}
            |${proxy("日本 01")}
            |proxy-groups:
            |  - name: 节点选择
            |    type: select
            |    proxies:
            |      - 香港 01
            |      - 日本 01
            |rules:
            |  - MATCH,节点选择
            |
            """.trimMargin()
        val out = SubscriptionSanitizer.sanitize(src, listOf("example.com"))

        @Suppress("UNCHECKED_CAST")
        val doc = Yaml().load<Any?>(out) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val names = (doc["proxies"] as List<Map<String, Any?>>).map { it["name"] as String }
        assertEquals("节点不能丢", 3, names.size)
        assertEquals("名字必须唯一", names.size, names.toSet().size)
        assertEquals(listOf("香港 01", "香港 01 (2)", "日本 01"), names)

        @Suppress("UNCHECKED_CAST")
        val group = (doc["proxy-groups"] as List<Map<String, Any?>>).first { it["name"] == "节点选择" }
        assertEquals("组引用仍指向首个节点", listOf("香港 01", "日本 01"), group["proxies"])
    }
}
