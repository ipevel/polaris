package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPresetTest {

    @Test
    fun `块式配置注入自动组并设为默认`() {
        val output = SmartPreset.apply(BLOCK_CONFIG)

        assertTrue(output.contains("- name: \"自动选择\""))
        assertTrue(output.contains("include-all: true"))
        assertTrue(output.contains("type: smart"))
        // 主选择器成员首位是自动组
        val selector = output.substringAfter("- name: 节点选择").substringBefore("- name:")
        assertTrue(selector.contains("- \"自动选择\""))
        assertTrue(selector.indexOf("- \"自动选择\"") < selector.indexOf("- 香港01"))
    }

    @Test
    fun `重复应用结果不变`() {
        val once = SmartPreset.apply(BLOCK_CONFIG)
        val twice = SmartPreset.apply(once)

        assertEquals(once, twice)
    }

    @Test
    fun `已有同名 smart 组时直接采用不新建`() {
        val config =
            """
            proxies:
              - name: "jp-01"
                type: ss
                server: 1.2.3.4
                port: 8388
            proxy-groups:
              - name: 自动选择
                type: smart
                proxies:
                  - jp-01
              - name: 节点选择
                type: select
                proxies:
                  - 自动选择
                  - jp-01
            """.trimIndent()

        val output = SmartPreset.apply(config)

        assertFalse(output.contains("自动选择 2"))
        assertEquals(1, Regex("- name: 自动选择").findAll(output).count())
    }

    @Test
    fun `目标名被非测速组占用时使用候选名`() {
        val config =
            """
            proxies:
              - name: "jp-01"
                type: ss
                server: 1.2.3.4
                port: 8388
            proxy-groups:
              - name: 自动选择
                type: select
                proxies:
                  - jp-01
            """.trimIndent()

        val output = SmartPreset.apply(config)

        assertTrue(output.contains("- name: \"自动选择 2\""))
        assertTrue(output.contains("include-all: true"))
    }

    @Test
    fun `流式分组也能设默认`() {
        val config =
            """
            proxies:
              - {name: jp-01, type: ss, server: 1.2.3.4, port: 8388}
            proxy-groups:
              - {name: 节点选择, type: select, proxies: [jp-01]}
            """.trimIndent()

        val output = SmartPreset.apply(config)

        assertTrue(output.contains("- name: \"自动选择\""))
        assertTrue(output.contains("proxies: [\"自动选择\", jp-01]"))
        assertEquals(SmartPreset.apply(output), output)
    }

    @Test
    fun `没有可选组时原样返回`() {
        val config =
            """
            proxies:
              - name: "jp-01"
                type: ss
                server: 1.2.3.4
                port: 8388
            """.trimIndent()

        assertEquals(config, SmartPreset.apply(config))
    }

    @Test
    fun `既无节点也无订阅源时原样返回`() {
        val config =
            """
            mixed-port: 7890
            mode: rule
            """.trimIndent()

        assertEquals(config, SmartPreset.apply(config))
    }

    @Test
    fun `仅有订阅源时也注入`() {
        val config =
            """
            proxy-providers:
              sub:
                type: http
                url: https://example.com/sub
            proxy-groups:
              - name: 节点选择
                type: select
                use:
                  - sub
            """.trimIndent()

        val output = SmartPreset.apply(config)

        assertTrue(output.contains("include-all: true"))
        // 选择器 proxies 里出现自动组
        assertTrue(output.contains("- \"自动选择\""))
    }

    @Test
    fun `自动组追加在组列表末尾不劫持首个选择器`() {
        val output = SmartPreset.apply(BLOCK_CONFIG)

        val groupsBlock = output.substringAfter("proxy-groups:").substringBefore("\nrules:")
        val firstGroup = groupsBlock.substringAfter("- name:").substringBefore("- name:")
        assertFalse(firstGroup.contains("url-test"))
    }

    private val BLOCK_CONFIG =
        """
        proxies:
          - name: "香港01"
            type: ss
            server: 1.1.1.1
            port: 8388
          - name: "jp-01"
            type: ss
            server: 2.2.2.2
            port: 8388
        proxy-groups:
          - name: 节点选择
            type: select
            proxies:
              - 香港01
              - jp-01
          - name: 自动线路
            type: select
            proxies:
              - jp-01
        rules:
          - MATCH,节点选择
        """.trimIndent()
}
