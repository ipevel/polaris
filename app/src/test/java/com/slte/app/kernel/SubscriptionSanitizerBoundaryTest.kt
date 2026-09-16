package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class SubscriptionSanitizerBoundaryTest {
    private val healthCheckUrl = "https://www.gstatic.com/generate_204"

    private val baseProxies =
        """
        |proxies:
        |  - name: "x"
        |    type: ss
        |    server: 1.2.3.4
        |    port: 8388
        """.trimMargin()

    private val baseWithRules = "$baseProxies\nrules:\n  - MATCH,DIRECT"

    private fun sanitize(
        text: String,
        domains: List<String> = listOf("example.com"),
    ): String = SubscriptionSanitizer.sanitize(text, domains)

    private fun parse(text: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return Yaml().load<Any?>(text) as Map<String, Any?>
    }

    private fun parseFails(text: String): Boolean = try {
        Yaml().load<Any?>(text)
        false
    } catch (e: Throwable) {
        true
    }

    @Test
    fun `空字符串输入 - 原样返回且内容校验拒绝`() {
        assertEquals("", sanitize(""))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml(""))
    }

    @Test
    fun `纯空白输入 - 原样返回不注入不崩溃`() {
        listOf("   ", "\n", "\n\n", " \t ", "\r\n", "\t").forEach { blank ->
            assertEquals(blank, sanitize(blank))
            assertFalse(SubscriptionSanitizer.isValidSubscribeYaml(blank))
        }
    }

    @Test
    fun `超大输入 - 1MB级YAML完整注入且幂等`() {
        val big =
            buildString {
                append("mixed-port: 7890\nallow-lan: true\ndns:\n  enable: true\nproxies:\n")
                repeat(12000) { i ->
                    append("  - name: \"n$i\"\n    type: ss\n    server: 1.2.3.4\n    port: 8388\n")
                }
                append("rules:\n")
                repeat(12000) { i ->
                    append("  - DOMAIN-SUFFIX,s$i.example,DIRECT\n")
                }
            }
        assertTrue(big.toByteArray().size > 1_000_000)
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml(big))

        val out = sanitize(big)
        assertEquals(big.lines().size + 3, out.lines().size)
        assertTrue(
            out.startsWith(
                "mixed-port: 0\nallow-lan: false\ndns:\n  fake-ip-filter:\n      - '+.example.com'\n  enable: true\n",
            ),
        )
        assertTrue(out.trimEnd().endsWith("  - DOMAIN-SUFFIX,s11999.example,DIRECT"))
        assertTrue(out.contains("\nrules:\n  - 'DOMAIN-SUFFIX,example.com,DIRECT'\n"))
        assertEquals(out, sanitize(out))
    }

    @Test
    fun `超大输入 - 1MB级缩进加引号文档同样完成清洗且幂等`() {
        val big =
            buildString {
                append("  'mixed-port': 7890\n  'allow-lan': true\n  'secret': hunter2\n  'script':\n    code: evil\n  proxies:\n")
                repeat(12000) { i ->
                    append("    - name: \"n$i\"\n      type: ss\n      server: 1.2.3.4\n      port: 8388\n")
                }
                append("  rules:\n")
                repeat(12000) { i ->
                    append("    - DOMAIN-SUFFIX,s$i.example,DIRECT\n")
                }
            }
        assertTrue(big.toByteArray().size > 1_000_000)

        val out = sanitize(big)
        assertEquals(out, sanitize(out))
        assertEquals(0, out.lines().count { it.contains("'mixed-port'") })
        assertEquals(0, out.lines().count { it.contains("script") })
        assertTrue(out.startsWith("mixed-port: 0\nallow-lan: false\nsecret: \"\"\n"))
        assertTrue(out.contains("\nrules:\n  - 'DOMAIN-SUFFIX,example.com,DIRECT'\n"))

        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["secret"])
        assertFalse(doc.containsKey("script"))
        val rules = doc["rules"] as List<*>
        assertEquals(12001, rules.size)
        assertEquals("DOMAIN-SUFFIX,example.com,DIRECT", rules.first())
    }

    @Test
    fun `超长单行 - 不做灾难性回溯且行为确定`() {
        val subtitleLine = "ui-subtitle-pattern: " + " ".repeat(100_000) + "x"
        val cleared = sanitize(subtitleLine)
        assertTrue(cleared.startsWith("ui-subtitle-pattern:"))
        assertTrue(cleared.endsWith("\"\""))
        assertNotEquals(subtitleLine, cleared)

        val singleLine = "z".repeat(200_000)
        assertEquals(singleLine, sanitize(singleLine))
    }

    @Test
    fun `内容校验 - 响应错误与二进制被拒绝`() {
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("<html><body>Not Found</body></html>"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("\n  <html>err</html>"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("<?xml version=\"1.0\"?><a/>"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("{\"data\":null}"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("   {\"data\":null}"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("mixed-port: 7890\nmode: rule"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("\u0000\u0001\u0002\u0003\u0004\u0005"))
    }

    @Test
    fun `内容校验 - 前置空白与CRLF的合法订阅被接受`() {
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("  proxies: []"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("\n\tproxies:\n  - name: a"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("mixed-port: 7890\r\nproxies:\r\n  - name: a\r\n"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("\uFEFFproxies:\n  - name: a"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("proxies:\n- name: a"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("proxies  :\n  - name: a"))
    }

    @Test
    fun `内容校验 - 仅含proxies字样的非配置文本被拒绝`() {
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("# proxies: 只是注释"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("\uFEFF{\"message\":\"proxies: 已过期\"}"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("\uFEFF<html><body>proxies: error</body></html>"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("\u0000\u0001proxies:\u0002\u0003"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("proxies: 已过期"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("  - proxies: x"))
    }

    @Test
    fun `内容校验 - 仅含proxy-providers的配置被接受`() {
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("proxy-providers:\n  a:\n    type: http"))
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml("  'proxy-providers':\n    a:\n      type: http"))
        assertFalse(SubscriptionSanitizer.isValidSubscribeYaml("proxies-list:\n  - a"))
    }

    @Test
    fun `畸形YAML - 制表符缩进不抛异常且保留原行`() {
        val src = "mixed-port:\t7890\nproxies:\n\t- name: \"x\""
        val out = sanitize(src)
        assertTrue(out.contains("mixed-port: 0"))
        assertTrue(out.contains("\t- name: \"x\""))
        assertTrue(parseFails(out))
    }

    @Test
    fun `畸形YAML - 重复键被逐行改写且解析取末值`() {
        val out = sanitize("mixed-port: 7890\nmixed-port: 7891\n$baseProxies")
        assertEquals(2, out.lines().count { it == "mixed-port: 0" })
        assertEquals(0, parse(out)["mixed-port"])
    }

    @Test
    fun `畸形YAML - CRLF被归一化为LF并完成清洗`() {
        val src = "mixed-port: 7890\r\nallow-lan: true\r\nproxies:\r\n  - name: \"x\"\r\n"
        assertTrue(SubscriptionSanitizer.isValidSubscribeYaml(src))
        val out = sanitize(src)
        assertFalse(out.contains("\r"))
        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals(1, (doc["proxies"] as List<*>).size)
    }

    @Test
    fun `畸形YAML - 孤立CR被当作换行分隔`() {
        val out = sanitize("mixed-port: 7890\r$baseProxies")
        assertFalse(out.contains("\r"))
        assertEquals(0, parse(out)["mixed-port"])
    }

    @Test
    fun `畸形YAML - 无proxies段仍安全清洗`() {
        val out = sanitize("mixed-port: 7890\nhosts:\n  'a.example': 1.1.1.1\n")
        assertFalse(out.contains("hosts"))
        assertFalse(out.contains("DOMAIN-SUFFIX"))
        assertEquals(0, parse(out)["mixed-port"])
    }

    @Test
    fun `安全 - 整篇缩进的根映射被归一化后完成全部清洗`() {
        val src =
            """
            |  mixed-port: 7890
            |  allow-lan: true
            |  secret: hunter2
            |  external-controller: 0.0.0.0:9090
            |  hosts:
            |    'bank.example': 10.0.0.1
            |  script:
            |    code: evil
            |  tun:
            |    enable: true
            |  proxies:
            |    - name: "x"
            |      type: ss
            |      server: 1.2.3.4
            |      port: 8388
            |  rules:
            |    - MATCH,DIRECT
            """.trimMargin()
        val out = sanitize(src)
        assertEquals(out, sanitize(out))
        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["secret"])
        assertEquals("", doc["external-controller"])
        assertFalse(doc.containsKey("hosts"))
        assertFalse(doc.containsKey("script"))
        assertEquals(false, (doc["tun"] as Map<*, *>)["enable"])
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            doc["rules"],
        )
        assertEquals(8388, (doc["proxies"] as List<Map<String, Any?>>)[0]["port"])

        val marked = sanitize("---\n  mixed-port: 7890\n  proxies:\n    - name: \"x\"\n  rules:\n    - MATCH,DIRECT")
        assertEquals(
            "---\nmixed-port: 0\nproxies:\n  - name: \"x\"\nrules:\n  - 'DOMAIN-SUFFIX,example.com,DIRECT'\n  - MATCH,DIRECT",
            marked,
        )
        assertEquals(0, parse(marked)["mixed-port"])
    }

    @Test
    fun `安全 - 单引号键在任意层级都被解引号并完成清洗`() {
        val src =
            """
            |'mixed-port': 7890
            |'allow-lan': true
            |'bind-address': "*"
            |'secret': hunter2
            |'external-controller': 0.0.0.0:9090
            |'hosts':
            |  'bank.example': 10.0.0.1
            |'script':
            |  code: evil
            |'tun':
            |  enable: true
            |'dns':
            |  'fake-ip-filter':
            |    - "*.lan"
            |'clash-for-android':
            |  'ui-subtitle-pattern': "^(a+)+\$"
            |$baseProxies
            |'rules':
            |  - MATCH,DIRECT
            """.trimMargin()
        val out = sanitize(src)
        assertEquals(out, sanitize(out))
        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["bind-address"])
        assertEquals("", doc["secret"])
        assertEquals("", doc["external-controller"])
        assertFalse(doc.containsKey("hosts"))
        assertFalse(doc.containsKey("script"))
        assertEquals(false, (doc["tun"] as Map<*, *>)["enable"])
        assertEquals("", (doc["clash-for-android"] as Map<*, *>)["ui-subtitle-pattern"])
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            doc["rules"],
        )
        val filter = (doc["dns"] as Map<*, *>)["fake-ip-filter"] as List<*>
        assertEquals(listOf("+.example.com", "*.lan"), filter)
        assertFalse(out.contains("'mixed-port'"))
        assertFalse(out.contains("^(a+)+$"))
    }

    @Test
    fun `安全 - 首行BOM被剥离且该行清洗生效`() {
        val ports = sanitize("\uFEFFmixed-port: 7890\nallow-lan: true\nbind-address: \"*\"\n$baseProxies")
        assertFalse(ports.contains("\uFEFF"))
        assertTrue(ports.startsWith("mixed-port: 0\nallow-lan: false\nbind-address: \"\"\n"))
        val portsDoc = parse(ports)
        assertEquals(0, portsDoc["mixed-port"])
        assertEquals(false, portsDoc["allow-lan"])
        assertEquals("", portsDoc["bind-address"])

        assertFalse(parse(sanitize("\uFEFFscript:\n  code: evil\n$baseProxies")).containsKey("script"))

        assertFalse(parse(sanitize("\uFEFFhosts:\n  'bank.example': 10.0.0.1\n$baseProxies")).containsKey("hosts"))

        assertEquals("", parse(sanitize("\uFEFFsecret: hunter2\n$baseProxies"))["secret"])

        assertEquals(mapOf("enable" to false), parse(sanitize("\uFEFFtun:\n  enable: true\n$baseProxies"))["tun"])

        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            parse(sanitize("\uFEFF$baseWithRules"))["rules"],
        )
    }

    @Test
    fun `安全 - 整篇缩进加BOM加引号键的组合绕过被完全清洗`() {
        val src =
            "\uFEFF" +
                """
                |  'mixed-port': 7890
                |  'allow-lan': true
                |  'secret': hunter2
                |  'external-controller': 0.0.0.0:9090
                |  'hosts':
                |    'bank.example': 10.0.0.1
                |  'script':
                |    code: evil
                |  'tun':
                |    enable: true
                |  proxies:
                |    - name: "x"
                |      type: ss
                |      server: 1.2.3.4
                |      port: 8388
                |  rules:
                |    - MATCH,DIRECT
                """.trimMargin()
        val out = sanitize(src)
        assertEquals(out, sanitize(out))
        assertFalse(out.contains("\uFEFF"))
        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["secret"])
        assertEquals("", doc["external-controller"])
        assertFalse(doc.containsKey("hosts"))
        assertFalse(doc.containsKey("script"))
        assertEquals(false, (doc["tun"] as Map<*, *>)["enable"])
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            doc["rules"],
        )
    }

    @Test
    fun `安全 - dns列表中的同名字符串不再抑制直连规则与豁免注入`() {
        val filter =
            """
            |dns:
            |  enable: true
            |  fake-ip-filter:
            |    - 'DOMAIN-SUFFIX,example.com,DIRECT'
            |$baseProxies
            |rules:
            |  - MATCH,DIRECT
            """.trimMargin()
        val filterOut = sanitize(filter)
        val filterDoc = parse(filterOut)
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            filterDoc["rules"],
        )
        assertEquals(
            listOf("+.example.com", "DOMAIN-SUFFIX,example.com,DIRECT"),
            (filterDoc["dns"] as Map<*, *>)["fake-ip-filter"],
        )
        assertEquals(filterOut, sanitize(filterOut))

        val nameserver =
            """
            |dns:
            |  enable: true
            |  nameserver:
            |    - 'DOMAIN-SUFFIX,example.com,DIRECT'
            |$baseProxies
            |rules:
            |  - MATCH,DIRECT
            """.trimMargin()
        val nameserverOut = sanitize(nameserver)
        val nameserverDoc = parse(nameserverOut)
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            nameserverDoc["rules"],
        )
        assertEquals(
            listOf("+.example.com"),
            (nameserverDoc["dns"] as Map<*, *>)["fake-ip-filter"],
        )
        assertEquals(nameserverOut, sanitize(nameserverOut))
    }

    @Test
    fun `安全 - 重复rules键下直连规则逐个段注入且解析生效`() {
        val out = sanitize("$baseProxies\nrules:\n  - MATCH,DIRECT\nrules:\n  - MATCH,PROXY")
        assertEquals(2, out.lines().count { it.contains("DOMAIN-SUFFIX,example.com,DIRECT") })
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,PROXY"),
            parse(out)["rules"],
        )
        assertEquals(out, sanitize(out))
    }

    @Test
    fun `安全 - 域名含换行或引号时被拒绝且不注入不破坏文档`() {
        val domains = listOf("evil.example\ninjected: 1", "x'\nscript:\n  code: evil\n#")
        domains.forEach { domain ->
            val out = sanitize(baseWithRules, listOf(domain))
            assertEquals(baseWithRules, out)
            assertEquals(listOf("MATCH,DIRECT"), parse(out)["rules"])
            assertFalse(out.contains("script"))
            assertEquals(out, sanitize(out, listOf(domain)))
        }
    }

    @Test
    fun `安全 - 非法直连域名被忽略且不注入任何内容`() {
        val invalid =
            listOf(
                "evil.example\ninjected: 1",
                "evil.example: 1",
                "a: 1",
                "evil.example rules",
                "*.example.com",
                " evil.example",
                "evil.example ",
                "-evil.example",
                "evil.example-",
                "evil..example",
                ".example.com",
                "example.com/DIRECT,MATCH",
                "例.example",
                "example.com,MATCH",
                "a." + "b".repeat(64) + ".com",
            )
        invalid.forEach { domain ->
            val out = sanitize(baseWithRules, listOf(domain))
            assertEquals("domain=$domain", baseWithRules, out)
            assertFalse("domain=$domain", out.contains("DOMAIN-SUFFIX"))
            assertFalse("domain=$domain", out.contains("fake-ip-filter"))
        }
        assertEquals(baseWithRules, sanitize(baseWithRules, listOf("a".repeat(254))))
    }

    @Test
    fun `安全 - 合法直连域名正常注入`() {
        val out = sanitize(baseWithRules, listOf("Example.COM", "cdn-a.example.co.uk", "intranet_host.local", " ", "example.com"))
        val doc = parse(out)
        assertEquals(
            listOf(
                "DOMAIN-SUFFIX,intranet_host.local,DIRECT",
                "DOMAIN-SUFFIX,cdn-a.example.co.uk,DIRECT",
                "DOMAIN-SUFFIX,Example.COM,DIRECT",
                "MATCH,DIRECT",
            ),
            doc["rules"],
        )
        assertTrue(out.contains("- 'DOMAIN-SUFFIX,Example.COM,DIRECT'"))
        assertEquals(out, sanitize(out, listOf("example.com")))
    }

    @Test
    fun `安全 - 顶层合并键被剔除`() {
        val inline = sanitize("$baseWithRules\n<<: {secret: hunter2, mixed-port: 7890, script: {code: evil}}\n")
        assertFalse(inline.contains("<<:"))
        val inlineDoc = parse(inline)
        assertFalse(inlineDoc.containsKey("secret"))
        assertFalse(inlineDoc.containsKey("mixed-port"))
        assertFalse(inlineDoc.containsKey("script"))
        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"), inlineDoc["rules"])

        val block = sanitize("$baseWithRules\n<<:\n  secret: hunter2\n  mixed-port: 7890\n")
        assertFalse(block.contains("<<"))
        val blockDoc = parse(block)
        assertFalse(blockDoc.containsKey("secret"))
        assertFalse(blockDoc.containsKey("mixed-port"))
        assertEquals(block, sanitize(block))
    }

    @Test
    fun `安全 - 锚点别名指向的tun与secret被中和`() {
        val alias = sanitize("shared: &shared\n  enable: true\n  stack: gvisor\ntun: *shared\nsecret: *shared\n$baseWithRules")
        val aliasDoc = parse(alias)
        assertEquals(false, (aliasDoc["tun"] as Map<*, *>)["enable"])
        assertEquals("", aliasDoc["secret"])

        val anchored = sanitize("tun: &tun\n  enable: true\n  stack: system\n$baseWithRules")
        assertEquals(false, (parse(anchored)["tun"] as Map<*, *>)["enable"])
        assertTrue(anchored.contains("tun: &tun"))

        val inlineAlias = sanitize("tun: {enable: true, stack: system}\n$baseWithRules")
        assertEquals(mapOf("enable" to false), parse(inlineAlias)["tun"])
    }

    @Test
    fun `安全 - tun块内重复enable键全部被关闭`() {
        val out = sanitize("tun:\n  enable: false\n  enable: true\n  stack: gvisor\n$baseWithRules")
        assertEquals(0, out.lines().count { it.trim() == "enable: true" })
        assertEquals(false, (parse(out)["tun"] as Map<*, *>)["enable"])
        assertEquals(out, sanitize(out))
    }

    @Test
    fun `安全 - 显式键语法无法绕过清洗`() {
        val explicit = "? hosts\n: {bank.example: 10.0.0.1}\n$baseWithRules"
        assertEquals("", sanitize(explicit))
        assertTrue(sanitize("$baseWithRules\n? script\n: {code: evil}\n").isEmpty())
    }

    @Test
    fun `安全 - 混合缩进的畸形文档被归一化后完成清洗`() {
        val out = sanitize("  tun:\n    enable: true\n  'secret': hunter2\n  'mixed-port': 7890\n$baseWithRules")
        val doc = parse(out)
        assertEquals(0, doc["mixed-port"])
        assertEquals("", doc["secret"])
        assertEquals(false, (doc["tun"] as Map<*, *>)["enable"])
        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"), doc["rules"])
        assertEquals(out, sanitize(out))
    }

    @Test
    fun `安全 - 绕过样本集合清洗后要么拒绝要么无危险键`() {
        val samples =
            listOf(
                "\uFEFF  'mixed-port': 7890\n  'allow-lan': true\n  'secret': hunter2\n  'script':\n    code: evil\n",
                "? hosts\n: {bank.example: 10.0.0.1}\n",
                "<<: {secret: hunter2, tun: {enable: true}}\n",
                "<<:\n  mixed-port: 7890\n",
                "shared: &shared\n  enable: true\ntun: *shared\n",
                "'hosts':\n  a.example: 1.1.1.1\n'mixed-port': 7890\n",
                "  tun:\n    enable: true\n  'secret': hunter2\n  'mixed-port': 7890\n",
                "tun:\n  enable: false\n  enable: true\n",
                "\t'mixed-port': 7890\n",
            )
        samples.forEach { sample ->
            val out = sanitize("$sample$baseWithRules", listOf("example.com"))
            if (out.isEmpty() || parseFails(out)) return@forEach
            val doc = parse(out)
            assertFalse(sample, doc.containsKey("hosts"))
            assertFalse(sample, doc.containsKey("script"))
            assertFalse(sample, doc.containsKey("scripting"))
            assertFalse(sample, doc.containsKey("web"))
            assertFalse(sample, doc.containsKey("<<"))
            assertEquals(sample, false, doc["allow-lan"] ?: false)
            assertEquals(sample, 0, doc["mixed-port"] ?: 0)
            assertEquals(sample, "", doc["secret"] ?: "")
            val tun = doc["tun"] as? Map<*, *>
            if (tun != null) assertEquals(sample, false, tun["enable"])
            val rules = doc["rules"] as? List<*>
            assertTrue(sample, rules?.contains("DOMAIN-SUFFIX,example.com,DIRECT") == true)
        }
    }

    @Test
    fun `安全 - inbound中的listeners块被剔除`() {
        val src =
            """
            |listeners:
            |  - name: inbound
            |    type: socks
            |    port: 7890
            |    listen: 0.0.0.0
            |$baseWithRules
            """.trimMargin()
        val out = sanitize(src)
        val doc = parse(out)
        assertFalse(doc.containsKey("listeners"))
        assertFalse(out.contains("0.0.0.0"))
        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"), doc["rules"])
        assertEquals(out, sanitize(out))
    }

    @Test
    fun `安全 - 转义与锚点修饰的键名无法绕过清洗`() {
        val escaped = "\"\\x68osts\":\n  'bank.example': 10.0.0.1\n$baseWithRules"
        assertTrue(parse(escaped).containsKey("hosts"))
        assertEquals("", sanitize(escaped))

        val anchored = "&a hosts:\n  'bank.example': 10.0.0.1\n$baseWithRules"
        assertTrue(parse(anchored).containsKey("hosts"))
        assertEquals("", sanitize(anchored))
    }

    @Test
    fun `安全 - 键名以外的转义与锚点符号不被误判`() {
        val passwords =
            """
            |mode: rule
            |proxies:
            |  - name: "x"
            |    type: ss
            |    server: 1.2.3.4
            |    port: 8388
            |    password: "p&ss\\word *"
            |rules:
            |  - DOMAIN-KEYWORD,&,DIRECT
            |  - MATCH,DIRECT
            |
            """.trimMargin()
        val out = sanitize(passwords)
        assertTrue(out.isNotEmpty())
        val doc = parse(out)
        assertEquals("p&ss\\word *", (doc["proxies"] as List<Map<String, Any?>>)[0]["password"])
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "DOMAIN-KEYWORD,&,DIRECT", "MATCH,DIRECT"),
            doc["rules"],
        )
    }

    @Test
    fun `清洗 - scripting与web块被剔除且subtitle被清空`() {
        val src =
            "scripting:\n  engine: expr\nweb:\n  external-ui: /srv/ui\n" +
                "clash-for-android:\n    ui-subtitle-pattern: \"^(a+)+\$\"\n    ui-subtitle-pattern2: keep\n" +
                "$baseProxies\nrules:\n  - MATCH,DIRECT"
        val out = sanitize(src)
        val doc = parse(out)
        assertFalse(doc.containsKey("scripting"))
        assertFalse(doc.containsKey("web"))
        val cfa = doc["clash-for-android"] as Map<*, *>
        assertEquals("", cfa["ui-subtitle-pattern"])
        assertEquals("keep", cfa["ui-subtitle-pattern2"])
        assertFalse(out.contains("^(a+)+$"))
    }

    @Test
    fun `清洗 - 双引号键仍被中和`() {
        val src =
            """
            |"external-controller": 0.0.0.0:9090
            |"external-ui": /srv/ui
            |"secret": hunter2
            |"allow-lan": true
            |"mixed-port": 7890
            |$baseProxies
            """.trimMargin()
        val doc = parse(sanitize(src))
        assertEquals("", doc["external-controller"])
        assertEquals("", doc["external-ui"])
        assertEquals("", doc["secret"])
        assertEquals(false, doc["allow-lan"])
        assertEquals(0, doc["mixed-port"])
    }

    @Test
    fun `清洗 - 端口键的注释与冒号前空白也被清零`() {
        val out = sanitize("port: 7890 # 开放\nmixed-port : 7891\nallow-lan: \"true\"\nbind-address: '*'\n$baseProxies")
        assertTrue(out.startsWith("port: 0\nmixed-port: 0\nallow-lan: false\nbind-address: \"\"\n"))
        val doc = parse(out)
        assertEquals(0, doc["port"])
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["bind-address"])
        assertEquals(8388, (doc["proxies"] as List<Map<String, Any?>>)[0]["port"])
    }

    @Test
    fun `清洗 - authentication标量形式被清空`() {
        val out = sanitize("authentication: admin:hunter2\n$baseProxies")
        assertTrue(out.startsWith("authentication: []\n"))
        assertEquals(emptyList<Any?>(), parse(out)["authentication"])
    }

    @Test
    fun `清洗 - tun块内enable非首行时仍被关闭`() {
        val src = "tun:\n    stack: gvisor\n    enable: true\n    dns-hijack:\n      - any:53\n$baseProxies"
        val tun = parse(sanitize(src))["tun"] as Map<String, Any?>
        assertEquals(false, tun["enable"])
        assertEquals("gvisor", tun["stack"])
        assertEquals(listOf("any:53"), tun["dns-hijack"])
    }

    @Test
    fun `清洗 - 合法的tun关闭写法不被误伤`() {
        listOf("tun: {enable: false}", "tun:\n  enable: false\n  stack: system").forEach { tun ->
            val out = sanitize("$tun\n$baseProxies")
            assertEquals(false, (parse(out)["tun"] as Map<*, *>)["enable"])
        }
    }

    @Test
    fun `清洗 - 嵌套同名危险键不被误伤`() {
        val src =
            """
            |proxy-providers:
            |  p:
            |    type: http
            |    url: https://example.com/s
            |    hosts:
            |      "bank.example": 10.0.0.1
            |    script:
            |      code: keep-me
            |$baseProxies
            """.trimMargin()
        val out = sanitize(src)
        val doc = parse(out)
        val provider = (doc["proxy-providers"] as Map<*, *>)["p"] as Map<*, *>
        assertEquals(mapOf("bank.example" to "10.0.0.1"), provider["hosts"])
        assertEquals(mapOf("code" to "keep-me"), provider["script"])
        assertFalse(doc.containsKey("hosts"))
        assertFalse(doc.containsKey("script"))
    }

    @Test
    fun `清洗 - 缩进文档内嵌套的provider危险同名键不被误伤`() {
        val src =
            """
            |  mixed-port: 7890
            |  proxy-providers:
            |    p:
            |      hosts:
            |        'bank.example': 10.0.0.1
            |      script:
            |        code: keep-me
            |  proxies:
            |    - name: "x"
            |      type: ss
            |      server: 1.2.3.4
            |      port: 8388
            """.trimMargin()
        val doc = parse(sanitize(src))
        assertEquals(0, doc["mixed-port"])
        assertFalse(doc.containsKey("hosts"))
        assertFalse(doc.containsKey("script"))
        val provider = (doc["proxy-providers"] as Map<*, *>)["p"] as Map<*, *>
        assertEquals(mapOf("bank.example" to "10.0.0.1"), provider["hosts"])
        assertEquals(mapOf("code" to "keep-me"), provider["script"])
        assertEquals(8388, (doc["proxies"] as List<Map<String, Any?>>)[0]["port"])
    }

    @Test
    fun `清洗 - 正常订阅不被拒绝且不重复注入`() {
        val src =
            """
            |mixed-port: 7890
            |allow-lan: true
            |external-controller: ""
            |secret: ""
            |tun:
            |  enable: false
            |$baseProxies
            |rules:
            |  - DOMAIN-SUFFIX,example.com,DIRECT
            |  - MATCH,DIRECT
            """.trimMargin()
        val out = sanitize(src)
        assertTrue(out.isNotEmpty())
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            parse(out)["rules"],
        )
        assertEquals(1, out.lines().count { it.contains("DOMAIN-SUFFIX,example.com,DIRECT") })
    }

    @Test
    fun `注入 - 多域名逆序插入且空白与重复域名被忽略`() {
        val src = "dns:\n  enable: true\n$baseProxies\nrules:\n  - MATCH,DIRECT"
        val out = sanitize(src, listOf("a.example", " ", "b.example", "a.example"))
        val doc = parse(out)
        assertEquals(
            listOf("DOMAIN-SUFFIX,b.example,DIRECT", "DOMAIN-SUFFIX,a.example,DIRECT", "MATCH,DIRECT"),
            doc["rules"],
        )
        assertEquals(listOf("+.b.example", "+.a.example"), (doc["dns"] as Map<*, *>)["fake-ip-filter"])
        assertTrue(out.contains("  - 'DOMAIN-SUFFIX,b.example,DIRECT'"))
        assertTrue(out.contains("      - '+.b.example'"))
    }

    @Test
    fun `注入 - 四空格缩进段注入位置与缩进正确`() {
        val src =
            """
            |dns:
            |    enable: true
            |    nameserver:
            |        - 223.5.5.5
            |$baseProxies
            |rules:
            |    - MATCH,DIRECT
            """.trimMargin()
        val out = sanitize(src)
        val doc = parse(out)
        val dns = doc["dns"] as Map<*, *>
        assertEquals(listOf("+.example.com"), dns["fake-ip-filter"])
        assertEquals(true, dns["enable"])
        assertEquals(listOf("223.5.5.5"), dns["nameserver"])
        assertEquals("DOMAIN-SUFFIX,example.com,DIRECT", (doc["rules"] as List<*>).first())
        assertTrue(out.contains("    fake-ip-filter:\n        - '+.example.com'"))
        assertTrue(out.contains("    - 'DOMAIN-SUFFIX,example.com,DIRECT'"))
    }

    @Test
    fun `注入 - 已存在等价直连规则时不重复`() {
        val out = sanitize("$baseProxies\nrules:\n  - DOMAIN-SUFFIX,example.com,DIRECT\n  - MATCH,DIRECT")
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,DIRECT"),
            parse(out)["rules"],
        )
        assertEquals(1, out.lines().count { it.contains("DOMAIN-SUFFIX,example.com,DIRECT") })

        val quoted = sanitize("$baseProxies\nrules:\n  - 'DOMAIN-SUFFIX,example.com,DIRECT'\n  - MATCH,DIRECT")
        assertEquals(1, quoted.lines().count { it.contains("DOMAIN-SUFFIX,example.com,DIRECT") })
    }

    @Test
    fun `注入 - dns为flow风格或空块时的实际行为`() {
        val flow = sanitize("dns: {enable: true}\n$baseProxies\nrules:\n  - MATCH,DIRECT")
        assertFalse(flow.contains("fake-ip-filter"))

        val empty = sanitize("dns:\n$baseProxies\nrules:\n  - MATCH,DIRECT")
        assertTrue(empty.contains("fake-ip-filter:\n    - '+.example.com'"))
        val doc = parse(empty)
        assertEquals(null, doc["dns"])
        assertEquals(listOf("+.example.com"), doc["fake-ip-filter"])
    }

    @Test
    fun `注入 - 空域名列表不注入任何规则`() {
        val src = "$baseProxies\nrules:\n  - MATCH,DIRECT"
        assertFalse(sanitize(src, emptyList()).contains("DOMAIN-SUFFIX"))
        val blank = sanitize(src, listOf("", "   "))
        assertFalse(blank.contains("DOMAIN-SUFFIX"))
        assertFalse(blank.contains("fake-ip-filter"))
    }

    @Test
    fun `幂等 - 复杂文档与绕过场景连续清洗两次结果一致`() {
        val complex =
            """
            |hosts:
            |  'bank.example': 10.0.0.1
            |external-controller: 0.0.0.0:9090
            |secret: hunter2
            |tun:
            |    enable: true
            |mixed-port: 7890
            |allow-lan: true
            |dns:
            |  enable: true
            |  fake-ip-filter:
            |    - "*.lan"
            |$baseProxies
            |proxy-providers:
            |  airport:
            |    type: http
            |    url: "https://sub.example.com"
            |proxy-groups:
            |  - name: "自动选择"
            |    type: url-test
            |    proxies:
            |      - x
            |rules:
            |  - MATCH,自动选择
            """.trimMargin()
        val domains = listOf("a.example", "b.example")
        val once = sanitize(complex, domains)
        assertEquals(once, sanitize(once, domains))
        val doc = parse(once)
        assertEquals(0, doc["mixed-port"])
        assertEquals(false, doc["allow-lan"])
        assertEquals("", doc["external-controller"])
        assertEquals("", doc["secret"])
        assertEquals(false, (doc["tun"] as Map<*, *>)["enable"])
        assertFalse(doc.containsKey("hosts"))
        assertEquals(healthCheckUrl, ((doc["proxy-groups"] as List<Map<String, Any?>>)[0])["url"])
        val provider = (doc["proxy-providers"] as Map<*, *>)["airport"] as Map<*, *>
        assertEquals(healthCheckUrl, (provider["health-check"] as Map<*, *>)["url"])
        val filter = (doc["dns"] as Map<*, *>)["fake-ip-filter"] as List<*>
        assertTrue(filter.containsAll(listOf("+.a.example", "+.b.example", "*.lan")))
        assertEquals(3, (doc["rules"] as List<*>).size)
    }

    @Test
    fun `幂等 - 绕过场景文档二次清洗保持不变`() {
        val quoted =
            """
            |'mixed-port': 7890
            |'secret': hunter2
            |'script':
            |  code: evil
            |$baseProxies
            """.trimMargin()
        val quotedOnce = sanitize(quoted)
        assertEquals(quotedOnce, sanitize(quotedOnce))

        val bom = sanitize("\uFEFFmixed-port: 7890\n$baseProxies")
        assertEquals(bom, sanitize(bom))

        val indented = sanitize("  mixed-port: 7890\n  allow-lan: true\n$baseProxies")
        assertEquals(indented, sanitize(indented))

        val decoy = sanitize("dns:\n  fake-ip-filter:\n    - '+.example.com'\n$baseWithRules")
        assertEquals(decoy, sanitize(decoy))
    }
}
