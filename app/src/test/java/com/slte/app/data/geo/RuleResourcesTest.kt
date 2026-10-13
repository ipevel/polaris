// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.geo

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `RuleResources` 的刷新契约（P1）。
 *
 * 用 MockWebServer 起一个假的 `ipevel/polaris-rules` 仓库，走**真实的** OkHttp 取件口，
 * 所以这里验的是生产路径，不是替身。契约要点：
 * 新鲜就零请求 · SHA 未变零下载 · 校验不过绝不覆盖也绝不写标记 ·
 * 清单取不到判失败（不许静默跳过）· 只下本 App 要的那几份。
 */
class RuleResourcesTest {
    private lateinit var server: MockWebServer
    private lateinit var root: File
    private lateinit var dirs: RuleResources.Dirs

    /** 服务端"最新 commit"；换掉它即模拟上游有新提交。 */
    private var commitSha = SHA_A
    private var manifestVersion = 4
    private var schema = 1
    private var shaApiDown = false
    private var manifestDown = false
    private var cdnDown = false
    private var rawDown = false

    private val content = LinkedHashMap<String, ByteArray>()
    private val declared = LinkedHashMap<String, Pair<Long, String>>()
    private val hits = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = RepoDispatcher()
        server.start()
        root = Files.createTempDirectory("rule-resources").toFile()
        dirs = newDirs()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // 新鲜度：只看本地，不发请求
    // ------------------------------------------------------------------

    @Test
    fun `本地新鲜时一个请求都不发`() {
        landed(sha = commitSha, at = NOW)
        assertTrue(RuleResources.isFresh(dirs, NOW + 1000))

        val result = refresh(listOf("ai"), NOW + 1000)

        assertTrue(result.skipped)
        assertEquals(0, server.requestCount)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `过了十二小时节拍就不算新鲜`() {
        landed(sha = commitSha, at = NOW)
        assertTrue(RuleResources.isFresh(dirs, NOW + RuleResources.REFRESH_INTERVAL_MS - 1))
        assertFalse(RuleResources.isFresh(dirs, NOW + RuleResources.REFRESH_INTERVAL_MS))
    }

    @Test
    fun `标记里记的文件少了一份就算不新鲜`() {
        landed(sha = commitSha, at = NOW)
        File(dirs.cacheDir, "ai.yaml").delete()
        assertFalse(RuleResources.isFresh(dirs, NOW + 1000))
    }

    @Test
    fun `标记里的文件名带路径穿越一律不算新鲜`() {
        landed(sha = commitSha, at = NOW)
        markerFile().writeText(
            """{"schema":1,"sha":"$SHA_A","version":4,"at":$NOW,"files":[{"n":"../../etc/passwd","d":0}]}""",
        )
        assertFalse(RuleResources.isFresh(dirs, NOW + 1000))
    }

    // ------------------------------------------------------------------
    // 零下载：上游没动就只推进时间戳
    // ------------------------------------------------------------------

    @Test
    fun `上游 SHA 没变时只推进标记 不下载任何文件`() {
        landed(sha = commitSha, at = NOW - RuleResources.REFRESH_INTERVAL_MS)
        val before = File(dirs.cacheDir, "ai.yaml").readBytes()
        assertFalse(RuleResources.isFresh(dirs, NOW))

        val result = refresh(listOf("ai"), NOW)

        assertTrue(result.skipped)
        assertEquals(commitSha, result.sha)
        assertTrue(result.updated.isEmpty())
        assertEquals(listOf("/repos/commits/main"), hits)
        assertTrue(File(dirs.cacheDir, "ai.yaml").readBytes().contentEquals(before))
        assertEquals(NOW, marker()?.get("at")?.jsonPrimitive?.content?.toLong())
    }

    // ------------------------------------------------------------------
    // 正常路径：下载 + 校验 + 原子覆盖 + 写标记
    // ------------------------------------------------------------------

    @Test
    fun `上游 SHA 变了就下载 校验通过才覆盖并写标记`() {
        putRule("ai", AI_RULE)
        putGeo()
        landed(sha = SHA_B, at = NOW - RuleResources.REFRESH_INTERVAL_MS)

        val result = refresh(listOf("ai"), NOW)

        assertNull(result.reason)
        assertEquals(commitSha, result.sha)
        assertEquals(listOf("ai.yaml", "geosite.dat", "geoip.metadb", "ASN.mmdb"), result.updated)
        assertTrue(File(dirs.cacheDir, "ai.yaml").readBytes().contentEquals(AI_RULE))
        assertTrue(File(dirs.geoDir, "geosite.dat").exists())
        assertEquals(commitSha, marker()?.get("sha")?.jsonPrimitive?.content)
        assertEquals("4", marker()?.get("version")?.jsonPrimitive?.content)
        assertNoParts()
    }

    @Test
    fun `规则清单进缓存目录 geodata 进内核主目录`() {
        putRule("ai", AI_RULE)
        putGeo()

        refresh(listOf("ai"), NOW)

        assertTrue(File(dirs.cacheDir, "ai.yaml").isFile)
        assertFalse(File(dirs.geoDir, "ai.yaml").exists())
        assertTrue(File(dirs.geoDir, "geosite.dat").isFile)
        assertFalse(File(dirs.cacheDir, "geosite.dat").exists())
    }

    @Test
    fun `落地的清单会镜像进已存在的内核 profile 目录`() {
        val dest = File(dirs.importedDir, "uuid-1/providers/${RuleResources.PROVIDERS_SUB_DIR}")
        dest.mkdirs()
        putRule("ai", AI_RULE)

        refresh(listOf("ai"), NOW)

        assertTrue(File(dest, "ai.yaml").readBytes().contentEquals(AI_RULE))
    }

    @Test
    fun `只下载本 App 要的那几个键`() {
        putRule("ai", AI_RULE)
        putRule("banad", "DOMAIN-SUFFIX,ads.example\n".toByteArray())

        val result = refresh(listOf("ai"), NOW)

        assertTrue(result.updated.contains("ai.yaml"))
        assertFalse(File(dirs.cacheDir, "banad.yaml").exists())
        assertFalse(hits.any { it.contains("banad") })
    }

    @Test
    fun `CDN 挂了就退回 raw 但钉的是同一个 commit`() {
        putRule("ai", AI_RULE)
        cdnDown = true

        val result = refresh(listOf("ai"), NOW)

        assertTrue(result.updated.contains("ai.yaml"))
        assertEquals(commitSha, result.sha)
        assertTrue(hits.any { it.startsWith("/cdn@$commitSha/") })
        assertTrue(hits.any { it.startsWith("/raw/$commitSha/") })
    }

    // ------------------------------------------------------------------
    // 硬约束：校验不过绝不覆盖、绝不写标记；取件失败判失败
    // ------------------------------------------------------------------

    @Test
    fun `sha256 不匹配就保留旧文件且不写标记`() {
        val old = "DOMAIN-SUFFIX,old.example\n".toByteArray()
        File(dirs.cacheDir, "ai.yaml").writeBytes(old)
        put("dist/rules/ai.list", AI_RULE, declaredSha = "0".repeat(64))

        val result = refresh(listOf("ai"), NOW)

        assertEquals("partial", result.reason)
        assertTrue(result.updated.isEmpty())
        assertTrue(File(dirs.cacheDir, "ai.yaml").readBytes().contentEquals(old))
        assertNull(marker())
        assertNoParts()
    }

    @Test
    fun `长度不匹配就保留旧文件且不写标记`() {
        val old = "DOMAIN-SUFFIX,old.example\n".toByteArray()
        File(dirs.cacheDir, "ai.yaml").writeBytes(old)
        put("dist/rules/ai.list", AI_RULE, declaredBytes = AI_RULE.size + 1L)

        val result = refresh(listOf("ai"), NOW)

        assertEquals("partial", result.reason)
        assertTrue(File(dirs.cacheDir, "ai.yaml").readBytes().contentEquals(old))
        assertNull(marker())
    }

    @Test
    fun `差一份没成功就不推进版本标记`() {
        putRule("ai", AI_RULE)
        // 第二份故意声明一个对不上的摘要，模拟"只成功了一部分"。
        put("dist/rules/banad.list", "x\n".toByteArray(), declaredSha = "0".repeat(64))

        val result = refresh(listOf("ai", "banad"), NOW)

        assertEquals("partial", result.reason)
        assertEquals(listOf("ai.yaml"), result.updated)
        assertNull(marker())
    }

    @Test
    fun `清单拉不到就判失败 不静默跳过`() {
        putRule("ai", AI_RULE)
        manifestDown = true

        val result = refresh(listOf("ai"), NOW)

        assertEquals("manifest", result.reason)
        assertNull(marker())
        assertFalse(File(dirs.cacheDir, "ai.yaml").exists())
    }

    @Test
    fun `清单的 schema 不是 1 就判失败`() {
        putRule("ai", AI_RULE)
        schema = 2

        val result = refresh(listOf("ai"), NOW)

        assertEquals("manifest", result.reason)
        assertNull(marker())
    }

    @Test
    fun `取不到 commit SHA 时退回 main 兜底 照常更新`() {
        putRule("ai", AI_RULE)
        shaApiDown = true

        val result = refresh(listOf("ai"), NOW)

        // api.github.com 国内直连不通，不能因此整轮放弃 —— 否则规则永远停在随包种子上。
        assertNull(result.reason)
        assertEquals(listOf("ai.yaml"), result.updated)
        assertNull(result.sha)
        assertTrue("兜底应走 jsdelivr 的 @main，实际：$hits", hits.any { it.startsWith("/cdn@main/") })
        // 标记如实记「本轮未钉 SHA」（sha 为空串时 kotlinx 会按默认值省略该字段）。
        assertEquals("", marker()?.get("sha")?.jsonPrimitive?.content.orEmpty())
        assertEquals(AI_RULE.toList(), File(dirs.cacheDir, "ai.yaml").readBytes().toList())
    }

    @Test
    fun `main 兜底时上游 version 没变就零下载`() {
        putRule("ai", AI_RULE)
        shaApiDown = true
        refresh(listOf("ai"), NOW)
        val before = hits.count { it.contains("/dist/rules/") }

        // 过了 12 小时节拍再来一轮：兜底模式只能靠清单里的 version 判断上游动没动。
        val second = refresh(listOf("ai"), NOW + RuleResources.REFRESH_INTERVAL_MS)

        assertTrue(second.skipped)
        assertEquals(before, hits.count { it.contains("/dist/rules/") })
        assertTrue(second.updated.isEmpty())
    }

    @Test
    fun `失败后退避三十分钟内不再重试`() {
        putRule("ai", AI_RULE)
        manifestDown = true

        val first = refresh(listOf("ai"), NOW)

        assertEquals("manifest", first.reason)
        assertTrue(File(dirs.geoDir, RuleResources.TRY_MARKER).isFile)
        val afterFirst = hits.size

        // 退避窗口内：直接跳过，一个请求都不发（用户日志里骂的就是"每次启动拉一遍、每次失败刷屏"）。
        val second = refresh(listOf("ai"), NOW + 60_000)
        assertEquals("backoff", second.reason)
        assertEquals(afterFirst, hits.size)

        // 出了窗口就再试一次。
        val third = refresh(listOf("ai"), NOW + RuleResources.RETRY_BACKOFF_MS + 1)
        assertEquals("manifest", third.reason)
        assertTrue(hits.size > afterFirst)
    }

    @Test
    fun `成功会清掉退避标记`() {
        putRule("ai", AI_RULE)
        manifestDown = true
        refresh(listOf("ai"), NOW)
        assertTrue(File(dirs.geoDir, RuleResources.TRY_MARKER).isFile)

        manifestDown = false
        val result = refresh(listOf("ai"), NOW + RuleResources.RETRY_BACKOFF_MS + 1)

        assertNull(result.reason)
        assertFalse(File(dirs.geoDir, RuleResources.TRY_MARKER).exists())
    }

    @Test
    fun `时钟回拨不把刷新永久卡死`() {
        putRule("ai", AI_RULE)
        manifestDown = true
        refresh(listOf("ai"), NOW)

        // now 比记录的时间戳还早：宁可多试一次，也不要因为时钟跳变永远卡在退避里。
        val result = refresh(listOf("ai"), NOW - 60_000)

        assertEquals("manifest", result.reason)
    }

    @Test
    fun `两个源都挂了就判失败`() {
        putRule("ai", AI_RULE)
        cdnDown = true
        rawDown = true

        val result = refresh(listOf("ai"), NOW)

        assertEquals("manifest", result.reason)
        assertNull(marker())
    }

    // ------------------------------------------------------------------
    // 单飞：不叠加
    // ------------------------------------------------------------------

    @Test
    fun `上一轮还在跑时本轮直接返回`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking =
            RuleResources.Http { url, _ ->
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                if (url.endsWith("/commits/main")) """{"sha":"$commitSha"}""".toByteArray() else throw IllegalStateException("unexpected $url")
            }
        val dirs2 = newDirs()
        val worker = Thread { RuleResources.refresh(dirs2, listOf("ai"), blocking, NOW, endpoints()) }
        worker.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val second = RuleResources.refresh(dirs2, listOf("ai"), blocking, NOW, endpoints())

        assertEquals("inflight", second.reason)
        release.countDown()
        worker.join(5000)
    }

    // ------------------------------------------------------------------
    // P4：随包 geodata 与同步标记同生共死
    // ------------------------------------------------------------------

    @Test
    fun `替换随包 geodata 时同步标记一并清掉`() {
        val geoDir = File(root, "p4")
        geoDir.mkdirs()
        for (name in RuleResources.GEO_RENAME.values) {
            File(geoDir, name).writeBytes("old".toByteArray())
            File(geoDir, name).setLastModified(1_000L)
        }
        markerFile(geoDir).writeText("""{"schema":1,"sha":"$SHA_A","version":3,"at":$NOW,"files":[]}""")

        val replaced = RuleResources.seedGeoFiles(geoDir, 2_000L) { ByteArrayInputStream("new".toByteArray()) }

        assertTrue(replaced)
        assertFalse("文件与标记必须同生共死", markerFile(geoDir).exists())
        assertTrue(File(geoDir, "geosite.dat").readBytes().contentEquals("new".toByteArray()))
    }

    @Test
    fun `随包 geodata 没过期就不动文件也不动标记`() {
        val geoDir = File(root, "p4-fresh")
        geoDir.mkdirs()
        for (name in RuleResources.GEO_RENAME.values) {
            File(geoDir, name).writeBytes("old".toByteArray())
            File(geoDir, name).setLastModified(3_000L)
        }
        markerFile(geoDir).writeText("""{"schema":1,"sha":"$SHA_A","version":3,"at":$NOW,"files":[]}""")

        val replaced = RuleResources.seedGeoFiles(geoDir, 2_000L) { ByteArrayInputStream("new".toByteArray()) }

        assertFalse(replaced)
        assertTrue(markerFile(geoDir).exists())
        assertTrue(File(geoDir, "geosite.dat").readBytes().contentEquals("old".toByteArray()))
    }

    @Test
    fun `缺失的随包 geodata 会被补齐`() {
        val geoDir = File(root, "p4-missing")
        geoDir.mkdirs()

        val replaced = RuleResources.seedGeoFiles(geoDir, 2_000L) { ByteArrayInputStream("new".toByteArray()) }

        assertFalse(replaced)
        for (name in RuleResources.GEO_RENAME.values) {
            assertTrue(name, File(geoDir, name).readBytes().contentEquals("new".toByteArray()))
        }
    }

    // ------------------------------------------------------------------
    // 清单挑选（纯函数）
    // ------------------------------------------------------------------

    @Test
    fun `清单里 dist 前缀下的 geodata 不会被当成内核要的那三份`() {
        val files =
            listOf(
                RuleResources.ManifestFile("dist/geo/geosite.dat", 10L, SHA_A),
                RuleResources.ManifestFile("geo/geosite.dat", 11L, SHA_B),
            )
        val doc = RuleResources.ManifestDoc(schema = 1, version = 4, files = files)

        val plan = RuleResources.planFiles(doc, emptyList())

        assertEquals(listOf("geosite.dat"), plan.map { it.name })
        assertEquals(11L, plan.single().bytes)
    }

    // ------------------------------------------------------------------
    // 脚手架
    // ------------------------------------------------------------------

    private fun newDirs(): RuleResources.Dirs {
        val geoDir = File(root, "clash-${System.nanoTime()}")
        return RuleResources.Dirs(
            geoDir = geoDir,
            cacheDir = File(geoDir, RuleResources.CACHE_DIR),
            importedDir = File(root, "imported-${System.nanoTime()}"),
        ).also {
            it.geoDir.mkdirs()
            it.cacheDir.mkdirs()
        }
    }

    private fun endpoints() = RuleResources.Endpoints(
        commitApi = server.url("/repos/commits/main").toString(),
        branchApi = server.url("/repos/branches/main").toString(),
        cdnBase = server.url("/cdn").toString().trimEnd('/'),
        rawBase = server.url("/raw").toString().trimEnd('/'),
    )

    private fun refresh(
        keys: List<String>,
        now: Long,
    ) = RuleResources.refresh(dirs, keys, RuleResources.defaultHttp(), now, endpoints())

    /** 放一份规则清单进假仓库（仓库里叫 `dist/rules/<键>.list`）。 */
    private fun putRule(
        key: String,
        bytes: ByteArray,
    ) = put("${RuleResources.RULES_PREFIX}$key.list", bytes)

    private fun putGeo() {
        for ((repoPath, local) in RuleResources.GEO_RENAME) {
            put(repoPath, "$local-v4".toByteArray())
        }
    }

    private fun put(
        path: String,
        bytes: ByteArray,
        declaredBytes: Long = bytes.size.toLong(),
        declaredSha: String = sha256(bytes),
    ) {
        content[path] = bytes
        declared[path] = declaredBytes to declaredSha
    }

    /** 造一个"上一轮已经同步过"的现场：文件 + 标记。 */
    private fun landed(
        sha: String,
        at: Long,
        rules: List<String> = listOf("ai"),
    ) {
        dirs.geoDir.mkdirs()
        dirs.cacheDir.mkdirs()
        for (rule in rules) File(dirs.cacheDir, "$rule.yaml").writeBytes("landed\n".toByteArray())
        val entries = mutableListOf<String>()
        for (rule in rules) entries += """{"n":"$rule.yaml","d":0}"""
        for (name in RuleResources.GEO_RENAME.values) {
            File(dirs.geoDir, name).writeBytes("landed\n".toByteArray())
            entries += """{"n":"$name","d":1}"""
        }
        markerFile(dirs.geoDir).writeText(
            """{"schema":1,"sha":"$sha","version":4,"at":$at,"files":[${entries.joinToString(",")}]}""",
        )
    }

    private fun markerFile(geoDir: File = dirs.geoDir) = File(geoDir, RuleResources.MARKER)

    private fun marker(): JsonObject? {
        val file = markerFile()
        if (!file.isFile) return null
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun assertNoParts() {
        val leftovers =
            (dirs.geoDir.listFiles()?.toList().orEmpty() + dirs.cacheDir.listFiles()?.toList().orEmpty())
                .filter { it.name.endsWith(".part") }
                .map { it.name }
        assertEquals("不该留下 .part 残骸", emptyList<String>(), leftovers)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private inner class RepoDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            hits += path
            if (path.endsWith("/commits/main") || path.endsWith("/branches/main")) {
                if (shaApiDown) return MockResponse().setResponseCode(500).setBody("boom")
                val body =
                    if (path.endsWith("/commits/main")) {
                        """{"sha":"$commitSha"}"""
                    } else {
                        """{"commit":{"sha":"$commitSha"}}"""
                    }
                return json(body)
            }
            val isCdn = path.startsWith("/cdn")
            val isRaw = path.startsWith("/raw")
            if (!isCdn && !isRaw) return MockResponse().setResponseCode(404).setBody("nope")
            if (isCdn && cdnDown) return MockResponse().setResponseCode(503).setBody("cdn down")
            if (isRaw && rawDown) return MockResponse().setResponseCode(503).setBody("raw down")
            val prefix = if (isCdn) "/cdn@" else "/raw/"
            if (!path.startsWith(prefix)) return MockResponse().setResponseCode(404).setBody("wrong prefix")
            val rest = path.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash <= 0) return MockResponse().setResponseCode(404).setBody("no ref")
            val ref = rest.substring(0, slash)
            if (ref != commitSha && ref != "main") return MockResponse().setResponseCode(404).setBody("wrong sha")
            val name = rest.substring(slash + 1)
            if (name == "latest.json") {
                if (manifestDown) return MockResponse().setResponseCode(404).setBody("no manifest")
                return json(manifestJson())
            }
            val body = content[name] ?: return MockResponse().setResponseCode(404).setBody("no such file")
            return MockResponse().setBody(Buffer().write(body))
        }
    }

    private fun manifestJson(): String {
        val files =
            declared.entries.joinToString(",") { (path, spec) ->
                """{"path":"$path","bytes":${spec.first},"sha256":"${spec.second}"}"""
            }
        return """{"schema":$schema,"version":$manifestVersion,"files":[$files]}"""
    }

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val AI_RULE = "DOMAIN-SUFFIX,openai.com,🤖 AI\nGEOSITE,openai,🤖 AI\n".toByteArray()
    }
}
