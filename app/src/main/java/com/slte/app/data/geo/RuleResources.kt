// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.geo

import android.content.Context
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一份要落盘的资源：本地文件名 + 仓库内相对路径 + 期望校验值。 */
data class RuleResource(
    val name: String,
    val path: String,
    val sha256: String,
    val bytes: Long,
    val geo: Boolean,
)

/**
 * 分流规则清单 + 地理库的统一刷新（P1）。
 *
 * 取代原先只管三份 geodata、且从**第三方** `appshubcc/bett-rules` 拉的 `GeoResources`：
 * 现在规则清单与 geodata 都来自我们自己的仓库 `ipevel/polaris-rules`
 * （CI 每 15 分钟同步上游 ACL4SSR + MetaCubeX 并算 SHA-256）——
 * 一个来源、一套校验、一个节拍。
 *
 * ## 算法（与 Windows 侧 `electron/core/rulesync.js` 同一套，只是语言不同）
 * ```
 * isFresh()：只看本地、不发任何请求 —— marker 未过期（12h）且 marker 记的每份文件都在且非空
 * refresh()：
 *   单飞（上一轮还在跑就直接返回，不叠加）
 *   sha = 最新 commit SHA        ← 与 marker 相同则只推进 marker 时间戳，零下载
 *   manifest = <base(sha)>/latest.json
 *   plan = 本 App 要的那几份（规则键来自随包种子目录，geodata 固定三份）
 *   逐份：下载 → 校验（长度 + sha256，基准就是同一个 commit 的 latest.json）
 *         通过才 .part → 原子 rename 覆盖；不通过删 .part、保留旧文件、记 WARN
 *   全部成功才写 marker（含 sha/version）；差一份就不写，下次重来
 * ```
 * ## 为什么必须钉 commit SHA
 * jsDelivr 的 `@main` 会命中陈旧的边缘缓存，而「校验和与字节分属两次不同的 CDN 抓取」
 * 正是安卓端旧实现踩过的坑（新 sum 配旧字节）。把清单与文件都钉在**同一个 SHA** 上，
 * 基准天生一致，不可能错配。
 *
 * ## 三条硬约束
 * 1. **不碰内核正在读的文件**：写 `.part` 再 `rename` 原子替换，绝不半写。
 * 2. **校验不过绝不覆盖**：保留旧文件 + 记 WARN，绝不写 marker（下次再来）。
 * 3. **失败不抛**：离线、上游抖动、限流都只记日志，继续用随包那份。
 */
object RuleResources {
    /** 规则与地理库的唯一来源。 */
    const val SOURCE = "ipevel/polaris-rules"

    /** `latest.json` 的清单版本；与 Windows 侧 rulesync.js 的 SCHEMA 一致。 */
    const val SCHEMA = 1

    /** 刷新节拍：12 小时（取代 GeoResources 的 24 小时）。 */
    const val REFRESH_INTERVAL_MS = 12L * 60 * 60 * 1000

    /** 上次成功同步的标记文件名（与 Windows 侧同名）。 */
    const val MARKER = "rules_last_sync"

    /** 上次**尝试**（含失败）的标记文件名；失败后退避，避免每次启动都重拉一遍。 */
    const val TRY_MARKER = "rules_last_try"

    /** 失败后的重试退避窗口：30 分钟。 */
    const val RETRY_BACKOFF_MS = 30L * 60 * 1000

    /** 规则清单在仓库里的产出前缀。 */
    const val RULES_PREFIX = "dist/rules/"

    /** 单文件与清单的体积上限。 */
    const val MAX_BYTES = 64L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024

    /** 钉住同一个 commit 的两个取件源；`@main` 会命中陈旧边缘缓存，所以必须带 SHA。 */
    const val CDN_BASE = "https://cdn.jsdelivr.net/gh/ipevel/polaris-rules"
    const val RAW_BASE = "https://raw.githubusercontent.com/ipevel/polaris-rules"

    /** commit SHA 的解析源（GitHub API；两个端点同一台主机，互为兜底）。 */
    const val COMMIT_API = "https://api.github.com/repos/ipevel/polaris-rules/commits/main"
    const val BRANCH_API = "https://api.github.com/repos/ipevel/polaris-rules/branches/main"

    /**
     * 取件基准：能钉住 commit 就钉住，钉不住退回 `@main`。
     *
     * `@main` 会命中陈旧边缘缓存，所以**能钉就必须钉**（这正是"新校验和配旧字节"的根治办法）。
     * 但 `api.github.com` 在国内直连是不通的，若拿不到 SHA 就整轮放弃，规则会永远停在随包种子上
     * —— 与"全部随仓库更新"的诉求相反。所以拿不到 SHA 时退到 jsdelivr 的 `@main`
     * （jsdelivr 国内可直连）：参照物与被校验物仍然同源（同一份 `latest.json` + 逐份 sha256），
     * 只是放弃了 commit 级的强一致。缓存代际错配会被逐份校验挡下（失败 → 不写 marker → 下次再来），
     * 所以退化是"这次不更新"，绝不会变成"更新成坏文件"。
     */
    sealed interface Ref {
        val label: String

        /** 钉住了 commit：`marker.sha` 与之相同即代表上游没动。 */
        data class Pinned(val sha: String) : Ref {
            override val label: String get() = sha.take(8)
        }

        /** 没钉住：只能靠清单里的 `version` 判断上游有没有动。 */
        object Floating : Ref {
            override val label: String get() = "@main"
        }
    }

    /**
     * 规则清单缓存目录名（相对内核 home 目录）。
     *
     * 与 Windows 侧 `data/polaris-rules/` 对齐：内核 home 就是 `filesDir/clash`
     * （见 `Bridge.kt` 的 `constant.SetHomeDir`），所以缓存落在这里。
     */
    const val CACHE_DIR = "polaris-rules"

    /** 内核 profile 目录下 provider 的落地子目录，与 `RoutingStateStore.PROVIDERS_SUB_DIR` 一致。 */
    const val PROVIDERS_SUB_DIR = "polaris-rules"

    /** 随包种子目录，与 `RoutingStateStore.ASSETS_PROVIDERS_DIR` 一致。 */
    const val ASSETS_PROVIDERS_DIR = "routing/providers"

    /** 三份 geodata：仓库路径 → 内核 home 下的本地名。 */
    val GEO_RENAME: Map<String, String> =
        mapOf(
            "geo/geosite.dat" to "geosite.dat",
            "geo/geoip.metadb" to "geoip.metadb",
            "geo/ASN.mmdb" to "ASN.mmdb",
        )

    private const val TAG = "RuleResources"

    private val json = Json { ignoreUnknownKeys = true }

    private val ruleFileName = Regex("^[a-z0-9-]+\\.list$")
    private val safeFileName = Regex("^[A-Za-z0-9._-]+$")

    /** 落盘位置：geodata 进内核 home，规则清单进 home 下的缓存目录。 */
    class Dirs(
        val geoDir: File,
        val cacheDir: File,
        val importedDir: File,
    )

    /** 一次刷新的结果。失败也返回（不抛），[reason] 说明停在哪一步。 */
    data class Result(
        val sha: String? = null,
        val version: Int? = null,
        val updated: List<String> = emptyList(),
        val skipped: Boolean = false,
        val reason: String? = null,
    )

    /** 取件接口；生产实现是 OkHttp，测试注入本地假仓库。 */
    internal fun interface Http {
        /** 取一个 URL 的全部字节；失败抛异常。 */
        fun get(url: String, maxBytes: Long): ByteArray
    }

    /** 上游端点；生产用默认值，测试指向本地假仓库以跑通整条算法。 */
    internal class Endpoints(
        val commitApi: String = COMMIT_API,
        val branchApi: String = BRANCH_API,
        val cdnBase: String = CDN_BASE,
        val rawBase: String = RAW_BASE,
    )

    /** 生产取件口。 */
    internal fun defaultHttp(): Http = InternalHttp()

    private val running = AtomicBoolean(false)

    fun dirs(context: Context): Dirs {
        val geoDir = File(context.filesDir, "clash")
        return Dirs(geoDir, File(geoDir, CACHE_DIR), File(context.filesDir, "imported"))
    }

    /**
     * 本 App 需要哪些规则清单：以随包种子目录为准。
     *
     * 种子文件名就是内核 `Providers` 的键（`<键>.yaml`），所以这份清单与 Go 表
     * 天然一一对应，仓库里多出来的产出（不属于本 App 的）不会被下下来。
     */
    fun providerKeys(context: Context): List<String> = context.assets
        .list(ASSETS_PROVIDERS_DIR)
        .orEmpty()
        .filter { it.endsWith(".yaml") }
        .map { it.removeSuffix(".yaml") }
        .sorted()

    /** 已被刷新过的某份规则清单；没有就返回 null（调用方退回随包种子）。 */
    fun cachedRule(context: Context, name: String): File? = File(dirs(context).cacheDir, name).takeIf { it.isFile && it.length() > 0 }

    /**
     * 用随包 geodata 补齐 / 替换内核 home 里的那三份，并在发生替换时清掉同步标记。
     *
     * **文件与标记同生共死**：随包副本换了，上一轮同步写下的标记就不再代表磁盘上的内容，
     * 必须一并清掉——否则下次 `refresh` 会以为「刚同步过」，内核会一直读这份随包副本，
     * 直到下一轮节拍才可能刷新（P4 修的就是这个语义）。
     *
     * @param updateDate 本次安装时间；比它旧的文件视为上一个版本的随包副本
     * @param open 按文件名打开随包资源；返回 null 表示这份没打进包
     * @return 是否发生过替换
     */
    internal fun seedGeoFiles(
        geoDir: File,
        updateDate: Long,
        open: (String) -> InputStream?,
    ): Boolean {
        geoDir.mkdirs()
        var replaced = false
        for (name in GEO_RENAME.values) {
            val target = File(geoDir, name)
            if (target.isFile && target.lastModified() < updateDate) {
                if (target.delete()) replaced = true
            }
            if (!target.isFile) {
                val source = open(name)
                if (source == null) {
                    AppLog.w(TAG, "seed geo: 随包里没有 $name")
                    continue
                }
                runCatching {
                    source.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                }.onFailure {
                    AppLog.w(TAG, "seed geo failed: $name: ${sanitizeLog(it.message ?: it.javaClass.simpleName)}")
                }
            }
        }
        if (replaced) {
            // 文件与标记同生共死：换了随包 geodata 就作废「上次成功」与「上次尝试」两个标记，
            // 否则会出现"文件换了、标记还新鲜"（长时间不刷新）或"退避窗口挡住新种子"的怪状态。
            File(geoDir, MARKER).delete()
            File(geoDir, TRY_MARKER).delete()
            AppLog.i(TAG, "seed geo: 替换了随包 geodata，同步标记一并清掉")
        }
        return replaced
    }

    /** 按 12 小时节拍刷新规则清单与地理库；失败只记日志，不影响启动。 */
    fun refresh(context: Context): Result {
        val keys = providerKeys(context)
        if (keys.isEmpty()) {
            AppLog.w(TAG, "rules sync: 随包种子目录为空，跳过")
            return Result(reason = "no-keys")
        }
        return refresh(dirs(context), keys, defaultHttp(), System.currentTimeMillis())
    }

    // -----------------------------------------------------------------------
    // 纯逻辑（可测试）：不看 Android 的 Context，只吃目录与取件口
    // -----------------------------------------------------------------------

    internal fun refresh(
        dirs: Dirs,
        keys: List<String>,
        http: Http,
        now: Long,
        endpoints: Endpoints = Endpoints(),
    ): Result {
        if (isFresh(dirs, now)) {
            val marker = readMarker(dirs)
            AppLog.i(TAG, "rules sync: 本地未过期（$MARKER），跳过 —— 未发任何请求")
            return Result(sha = marker?.sha, version = marker?.version, skipped = true)
        }
        if (inBackoff(dirs, now)) {
            AppLog.i(TAG, "rules sync: 上一轮失败还在退避窗口内（$TRY_MARKER），跳过本轮")
            return Result(skipped = true, reason = "backoff")
        }
        if (!running.compareAndSet(false, true)) {
            AppLog.i(TAG, "rules sync: 上一轮还在跑，跳过本轮")
            return Result(skipped = true, reason = "inflight")
        }
        try {
            val result = doRefresh(dirs, keys, http, now, endpoints)
            if (result.reason == null) {
                // 成功：清掉退避标记，下一轮按 12 小时节拍走。
                tryMarker(dirs).delete()
            } else {
                // 失败：记下时间戳，退避窗口内不再重拉（用户日志里骂的就是"每次启动拉一遍、每次失败刷屏"）。
                writeTryMarker(dirs, now)
            }
            return result
        } finally {
            running.set(false)
        }
    }

    /**
     * 是否处在失败后的退避窗口里。
     *
     * 时钟回拨（`now < at`）时**不**退避：宁可多试一次，也不要因为时钟跳变把刷新永久卡死。
     */
    internal fun inBackoff(dirs: Dirs, now: Long): Boolean {
        val at = readTryMarker(dirs)
        if (at <= 0L || now < at) return false
        return now - at < RETRY_BACKOFF_MS
    }

    /**
     * 本地是否已经是最新且未过期 —— **只看本地状态，不发任何请求**。
     *
     * marker 是上一轮**全部文件都成功**之后才写的，所以「marker 新鲜 + 文件都在」
     * 就意味着上一轮确实完整落地了。
     */
    internal fun isFresh(dirs: Dirs, now: Long): Boolean {
        val marker = readMarker(dirs) ?: return false
        if (marker.schema != SCHEMA) return false
        if (marker.at <= 0L || now - marker.at >= REFRESH_INTERVAL_MS) return false
        if (marker.files.isEmpty()) return false
        for (entry in marker.files) {
            // marker 是可写的，别让它变成路径穿越
            if (!safeFileName.matches(entry.n)) return false
            val base = if (entry.d == 1) dirs.geoDir else dirs.cacheDir
            val file = File(base, entry.n)
            if (!file.isFile || file.length() <= 0L) return false
        }
        return true
    }

    internal fun planFiles(doc: ManifestDoc, keys: List<String>): List<RuleResource> {
        val known = keys.toSet()
        val wanted = mutableListOf<RuleResource>()
        for (file in doc.files) {
            val path = file.path
            if (path.isEmpty() || file.sha256.isEmpty()) continue
            if (path.startsWith(RULES_PREFIX)) {
                val name = path.removePrefix(RULES_PREFIX)
                if (!ruleFileName.matches(name)) continue
                val key = name.removeSuffix(".list")
                if (key !in known) continue
                // 仓库里是 `dist/rules/<键>.list`，本地必须叫 `<键>.yaml`：
                // 内核按声明的 format 解析内容，扩展名只用来对齐种子名（Go 侧 FileExt 一律 .yaml）。
                wanted += RuleResource("$key.yaml", path, file.sha256, file.bytes, geo = false)
                continue
            }
            val local = GEO_RENAME[path] ?: continue
            wanted += RuleResource(local, path, file.sha256, file.bytes, geo = true)
        }
        return wanted
    }

    private fun doRefresh(dirs: Dirs, keys: List<String>, http: Http, now: Long, endpoints: Endpoints): Result {
        val ref = resolveRef(http, endpoints)
        val marker = readMarker(dirs)
        val pinnedSha = (ref as? Ref.Pinned)?.sha

        // 钉住了 SHA 且与上次相同：上游没动，只把 marker 时间戳推进，零下载。
        if (pinnedSha != null && marker != null && marker.sha == pinnedSha) {
            writeMarker(dirs, marker.copy(at = now))
            AppLog.i(TAG, "rules sync: 上游仍是 v${marker.version} @ ${ref.label}，无变化（零下载）")
            return Result(sha = pinnedSha, version = marker.version, skipped = true)
        }

        val manifest = fetchManifest(http, ref, endpoints) ?: return Result(sha = pinnedSha, reason = "manifest")
        val (base, doc) = manifest

        // 没钉住 SHA 时只能看清单里的 version 判断上游动没动（latest.json 才几 KB，值得一取）。
        if (pinnedSha == null && marker != null && marker.version == doc.version) {
            writeMarker(dirs, marker.copy(at = now))
            AppLog.i(TAG, "rules sync: 上游仍是 v${doc.version}（${ref.label} 兜底，本轮未钉 SHA），无变化（零下载）")
            return Result(version = doc.version, skipped = true)
        }

        val plan = planFiles(doc, keys)
        if (plan.isEmpty()) {
            AppLog.w(TAG, "rules sync: 清单里没有本 App 需要的文件（仓库产出名与规则键对不上？）")
            return Result(sha = pinnedSha, version = doc.version, reason = "empty-plan")
        }

        dirs.geoDir.mkdirs()
        dirs.cacheDir.mkdirs()

        val updated = mutableListOf<String>()
        for (file in plan) {
            val target = if (file.geo) dirs.geoDir else dirs.cacheDir
            val tmp = File(target, "${file.name}.part")
            try {
                val body = http.get("$base/${file.path}", MAX_BYTES)
                if (body.isEmpty()) throw IllegalStateException("下载到 0 字节")
                if (file.bytes > 0L && body.size.toLong() != file.bytes) {
                    throw IllegalStateException("长度不符：期望 ${file.bytes}，实际 ${body.size}")
                }
                val actual = sha256Hex(body)
                if (!actual.equals(file.sha256, ignoreCase = true)) {
                    throw IllegalStateException("sha256 不匹配：期望 ${file.sha256}，实际 $actual")
                }
                FileOutputStream(tmp).use { it.write(body) }
                replaceAtomically(tmp, File(target, file.name))
                updated += file.name
                AppLog.i(TAG, "rules sync: updated ${file.name} (${body.size} bytes)")
            } catch (e: Exception) {
                AppLog.w(TAG, "rules sync failed: ${file.name}: ${sanitizeLog(e.message ?: e.javaClass.simpleName)}")
                tmp.delete()
            }
        }

        // 只有全部成功才写 marker —— 差一份就下次再来，避免「看着新鲜其实缺文件」。
        if (updated.size == plan.size) {
            writeMarker(
                dirs,
                MarkerDoc(
                    schema = SCHEMA,
                    // 未钉 SHA 时写空串：标记如实反映「这一轮没有 commit 级强一致」。
                    sha = pinnedSha.orEmpty(),
                    version = doc.version,
                    at = now,
                    files = plan.map { MarkerEntry(it.name, if (it.geo) 1 else 0) },
                ),
            )
            mirrorIntoProfiles(dirs)
            return Result(sha = pinnedSha, version = doc.version, updated = updated)
        }
        AppLog.w(TAG, "rules sync: 本次只成功 ${updated.size}/${plan.size} 份，不推进版本标记，下次继续")
        return Result(sha = pinnedSha, version = doc.version, updated = updated, reason = "partial")
    }

    /**
     * 决定这一轮的取件基准：能钉住 commit 就钉住，钉不住退回 `@main`。
     *
     * 拿不到 SHA **不再**整轮放弃 —— 那是"规则永远停在随包种子"的老毛病（`api.github.com` 国内直连不通）。
     */
    private fun resolveRef(http: Http, endpoints: Endpoints): Ref {
        val sha = resolveSha(http, endpoints)
        if (sha == null) {
            AppLog.w(TAG, "rules sync: 取不到 commit SHA（api.github.com 多半被墙），退回 @main 兜底，本轮未钉 SHA")
            return Ref.Floating
        }
        return Ref.Pinned(sha)
    }

    private fun resolveSha(http: Http, endpoints: Endpoints): String? {
        for (url in listOf(endpoints.commitApi, endpoints.branchApi)) {
            try {
                val root = json.parseToJsonElement(http.get(url, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)).jsonObject
                val node: JsonElement? = root["sha"] ?: root["commit"]?.jsonObject?.get("sha")
                val sha = node?.jsonPrimitive?.content
                if (sha != null && SHA_RE.matches(sha)) return sha
                AppLog.w(TAG, "rules sync: $url 里没有合法的 commit sha")
            } catch (e: Exception) {
                AppLog.w(TAG, "rules sync: 取 commit SHA 失败（$url）: ${sanitizeLog(e.message ?: e.javaClass.simpleName)}")
            }
        }
        return null
    }

    private fun fetchManifest(http: Http, ref: Ref, endpoints: Endpoints): Pair<String, ManifestDoc>? {
        val errors = mutableListOf<String>()
        for (base in bases(ref, endpoints)) {
            try {
                val text = http.get("$base/latest.json", MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                val doc = runCatching { json.decodeFromString(ManifestDoc.serializer(), text) }.getOrNull()
                    ?: throw IllegalStateException("latest.json 不是合法 JSON")
                if (doc.schema != SCHEMA) throw IllegalStateException("schema 不是 $SCHEMA（收到 ${doc.schema}）")
                if (doc.files.isEmpty()) throw IllegalStateException("latest.json 里没有 files")
                return base to doc
            } catch (e: Exception) {
                errors += "$base: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        AppLog.w(TAG, "rules sync: 没有可用的规则源 —— ${sanitizeLog(errors.joinToString("；"))}")
        return null
    }

    /** 取件源：钉住 SHA 时两个源都带 SHA；兜底时两个源都走 `@main`。 */
    private fun bases(ref: Ref, endpoints: Endpoints): List<String> = when (ref) {
        is Ref.Pinned -> listOf("${endpoints.cdnBase}@${ref.sha}", "${endpoints.rawBase}/${ref.sha}")
        Ref.Floating -> listOf("${endpoints.cdnBase}@main", "${endpoints.rawBase}/main")
    }

    /**
     * 把刚落地的规则清单镜像进每个已存在的内核 profile 目录
     * （`imported/<uuid>/providers/polaris-kernel` 的兄弟目录 `providers/polaris-rules/`）。
     *
     * 正在运行的内核随后由调用方广播重载即可读到新清单，不必断线重连。
     * 新建的 profile 由 `KernelConfig.seedRoutingProviders` 优先从这里取件。
     */
    private fun mirrorIntoProfiles(dirs: Dirs) {
        val sources = dirs.cacheDir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }
        if (sources.isEmpty()) return
        for (profile in dirs.importedDir.listFiles().orEmpty()) {
            val dest = File(File(profile, "providers"), PROVIDERS_SUB_DIR)
            if (!dest.isDirectory) continue
            for (src in sources) {
                runCatching { copyAtomic(src, File(dest, src.name)) }
                    .onFailure {
                        AppLog.w(TAG, "rules sync: 镜像到 ${profile.name} 失败: ${sanitizeLog(it.message ?: it.javaClass.simpleName)}")
                    }
            }
        }
    }

    private fun copyAtomic(
        src: File,
        dest: File,
    ) {
        val tmp = File(dest.parentFile, "${dest.name}.part")
        src.inputStream().use { input -> FileOutputStream(tmp).use { output -> input.copyTo(output) } }
        replaceAtomically(tmp, dest)
    }

    /**
     * 原子替换目标文件。
     *
     * 特意不用 `File.renameTo`：它在 Windows 上目标已存在时会失败（安卓是 Linux，能覆盖），
     * 而"覆盖上一轮那份"恰恰是我们的常规路径。`Files.move` 两边都是 rename(2) 语义。
     */
    private fun replaceAtomically(
        tmp: File,
        dest: File,
    ) {
        try {
            Files.move(
                tmp.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            // 文件系统不支持原子替换（如跨卷）：退化成普通替换，至少不留半写文件。
            AppLog.w(TAG, "atomic move unsupported: ${sanitizeLog(e.message ?: "unknown")}")
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun markerFile(dirs: Dirs): File = File(dirs.geoDir, MARKER)

    private fun readMarker(dirs: Dirs): MarkerDoc? {
        val file = markerFile(dirs)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString(MarkerDoc.serializer(), file.readText()) }.getOrNull()
    }

    private fun writeMarker(dirs: Dirs, doc: MarkerDoc) {
        runCatching {
            dirs.geoDir.mkdirs()
            val tmp = File(dirs.geoDir, "$MARKER.part")
            FileOutputStream(tmp).use { it.write(json.encodeToString(MarkerDoc.serializer(), doc).toByteArray(Charsets.UTF_8)) }
            replaceAtomically(tmp, markerFile(dirs))
        }.onFailure { AppLog.w(TAG, "rules sync: 写标记失败 ${sanitizeLog(it.message ?: it.javaClass.simpleName)}") }
    }

    private fun tryMarker(dirs: Dirs): File = File(dirs.geoDir, TRY_MARKER)

    /** 上次尝试的时间戳（epoch 毫秒）；没有或读不出都返回 0，等价于"不处于退避"。 */
    private fun readTryMarker(dirs: Dirs): Long = runCatching { tryMarker(dirs).takeIf { it.isFile }?.readText()?.trim()?.toLong() ?: 0L }.getOrDefault(0L)

    private fun writeTryMarker(dirs: Dirs, now: Long) {
        runCatching {
            dirs.geoDir.mkdirs()
            val tmp = File(dirs.geoDir, "$TRY_MARKER.part")
            FileOutputStream(tmp).use { it.write(now.toString().toByteArray(Charsets.UTF_8)) }
            replaceAtomically(tmp, tryMarker(dirs))
        }.onFailure { AppLog.w(TAG, "rules sync: 写退避标记失败 ${sanitizeLog(it.message ?: it.javaClass.simpleName)}") }
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val SHA_RE = Regex("^[0-9a-f]{40}$")

    /** `latest.json`：只取本 App 要用的字段。 */
    @Serializable
    internal data class ManifestDoc(
        val schema: Int = 0,
        val version: Int = 0,
        val files: List<ManifestFile> = emptyList(),
    )

    @Serializable
    internal data class ManifestFile(
        val path: String = "",
        val bytes: Long = 0L,
        val sha256: String = "",
    )

    /** 同步标记：上游版本 + 已经落地的那几份文件。 */
    @Serializable
    private data class MarkerDoc(
        val schema: Int = SCHEMA,
        val sha: String = "",
        val version: Int = 0,
        val at: Long = 0L,
        val files: List<MarkerEntry> = emptyList(),
    )

    @Serializable
    private data class MarkerEntry(
        val n: String,
        val d: Int = 0,
    )

    private class InternalHttp : Http {
        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        override fun get(url: String, maxBytes: Long): ByteArray {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("空响应体")
                val declared = body.contentLength()
                if (declared > maxBytes) throw IllegalStateException("响应过大：$declared > $maxBytes")
                val bytes = body.bytes()
                if (bytes.size.toLong() > maxBytes) throw IllegalStateException("响应过大：${bytes.size} > $maxBytes")
                return bytes
            }
        }
    }
}
