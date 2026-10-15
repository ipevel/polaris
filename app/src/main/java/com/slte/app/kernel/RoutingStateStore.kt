// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地分流状态（routing.json）的读写。
 *
 * 文件位于 Go 内核 home 目录（filesDir/clash/routing.json，与 override.json 同级），
 * 由 [com.github.kr328.clash.core.bridge.Bridge] 初始化时确定的 home 推导而来。
 * 内核侧 native/config/routing 读取同一文件；结构必须与 Go 的 State 保持一致
 * （未知字段两侧都会忽略，新增字段向后兼容）。
 *
 * 文件缺失/损坏时内核侧视为「本地分流关闭」（回退面板下发配置），因此 App 侧
 * 必须在订阅导入流程中主动 [ensureDefault] 写入默认启用状态。
 */
@Serializable
data class RoutingCustomGroup(
    val name: String,
    val url: String,
    val behavior: String = "classical",
    val interval: Int = 0,
)

@Serializable
data class RoutingState(
    val version: Int = 1,
    val enabled: Boolean = false,
    val groups: Map<String, Boolean> = emptyMap(),
    val custom: List<RoutingCustomGroup> = emptyList(),
    // 内置分流组的用户自定义顺序（组名列表）；空 = 沿用内置默认顺序。
    // 只影响「组与规则的排列」（即规则匹配优先级与 App 侧展示顺序），不影响开关。
    val order: List<String> = emptyList(),
    // 字段名必须与 Go State 的 json tag 逐字一致（snake_case）
    @SerialName("direct_domains") val directDomains: List<String> = emptyList(),
)

/** 内核写入的降级标记（`routing-degraded.json`，字段与 Go 的 Degraded 一致）。 */
@Serializable
data class DegradedRouting(
    val reason: String = "",
    val detail: String = "",
)

@Singleton
class RoutingStateStore
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * 串行化本类全部「读-改-写」序列（load()/readRaw() → copy → write()）。
     *
     * 单次 [write] 是「写 .tmp + rename」，本身原子；但两个调用方各自 load 到同一份
     * 旧状态、再先后 write 时，后写者会用陈旧快照覆盖先写者的结果（丢更新）。本锁把
     * 整段序列做成临界区；ReentrantLock 可重入，因此 [ensureDefault] →
     * [syncDirectDomains] 这类类内嵌套调用不会自锁，[write] 也无需自行加锁。
     */
    private val lock = ReentrantLock()

    fun stateFile(): File = File(context.filesDir, "clash").resolve(FILE_NAME)

    /**
     * 读取状态；文件缺失或损坏一律返回 enabled=false 的零状态——与内核侧
     * ReadState 的降级语义完全一致（缺省=关闭=面板配置原样生效），杜绝
     * 「UI 显示开启而内核按关闭处理」的期望值/真实值分裂（pitfall #6）。
     * 启用状态由 [ensureDefault] 在订阅导入流程写盘后再被读到。
     *
     * 读到的**非法自定义组/直连域名会被清掉**（`routing.json` 可能由旧版本或
     * 其它写入点留下非法值，非法值会让整份内核配置加载失败）。清洗结果会
     * 立刻回写一次，避免每次启动重复清洗。返回的 [SanitizedRoutingState] 带
     * 丢弃数量，供 UI 提示。enabled / groups 开关不受影响。
     *
     * 注意：本方法**不递归**——只有 [loadSanitized] 会回写，缓存层不会。
     */
    fun loadSanitized(): SanitizedRoutingState = lock.withLock {
        val rawFile = readRaw()
        val raw = migrateLegacyNames(rawFile)
        val custom = raw.custom.filter { RoutingInputValidator.isValidGroupName(it.name) }
        val domains = raw.directDomains.filter { RoutingInputValidator.isValidRuleDomainShape(it) }
        val droppedCustom = raw.custom.size - custom.size
        val droppedDomains = raw.directDomains.size - domains.size
        val migrated = raw != rawFile
        val clean = raw.copy(custom = custom, directDomains = domains)
        if (droppedCustom > 0 || droppedDomains > 0 || migrated) {
            if (droppedCustom > 0 || droppedDomains > 0) {
                AppLog.w(
                    LOG_TAG,
                    "load: 已清除非法本地分流项 custom=$droppedCustom domain=$droppedDomains",
                )
            }
            if (migrated) {
                AppLog.w(LOG_TAG, "load: 已迁移历史分流组名到「$AI_GROUP_NAME」")
            }
            write(clean)
        }
        SanitizedRoutingState(clean, droppedCustom, droppedDomains)
    }

    /** [loadSanitized] 的结果：清洗后的状态 + 被丢弃的条目数（供 UI 提示）。 */
    data class SanitizedRoutingState(
        val state: RoutingState,
        val droppedCustom: Int,
        val droppedDomains: Int,
    ) {
        val hasDropped: Boolean get() = droppedCustom > 0 || droppedDomains > 0
    }

    /**
     * 读取内核写的降级标记（本地分流因面板命名冲突等原因未生效）。内核在成功
     * 应用本地分流时会删除该文件，所以「文件存在」= 当前处于降级状态。
     * 返回 null 表示无降级。
     */
    fun readDegraded(): DegradedRouting? = runCatching {
        val file = File(context.filesDir, "clash").resolve(DEGRADED_FILE_NAME)
        if (!file.exists()) {
            null
        } else {
            json.decodeFromString<DegradedRouting>(file.readText())
        }
    }.getOrElse {
        AppLog.w(LOG_TAG, "readDegraded: 解析失败: ${sanitizeLog(it.message ?: "Unknown")}")
        null
    }

    fun clearDegraded(): Boolean = runCatching {
        val file = File(context.filesDir, "clash").resolve(DEGRADED_FILE_NAME)
        !file.exists() || file.delete()
    }.getOrDefault(false)

    /**
     * 读取状态（不含清洗回写；供写入路径内部使用）。
     *
     * 历史组名（[LegacyGroupAliases]）在读取时就归一——改名后用户已保存的开关与
     * 拖动顺序不能静默丢失（退回内置默认值 / 内置顺序）。归一结果会在下一次
     * 经 [write] 落盘时持久化；[loadSanitized] 会主动回写一次。
     */
    fun load(): RoutingState = migrateLegacyNames(readRaw())

    /**
     * 只读、**不清洗、不回写、可失败**的读：文件缺失 → 空 [RoutingState]（确实没有自建组）；
     * 文件存在但解析失败 → null（调用方据此走保守分支）。
     *
     * 与 [load] 的区别：[load] 把解析失败也吞成空状态，调用方无法区分「没有自建组」与
     * 「读不出来」——对显示映射来说前者照常映射、后者必须保守不映射。
     */
    fun readOrNull(): RoutingState? {
        val file = stateFile()
        if (!file.exists()) return RoutingState()
        return runCatching { json.decodeFromString<RoutingState>(file.readText()) }.getOrElse {
            AppLog.w(LOG_TAG, "readOrNull: 解析失败: ${sanitizeLog(it.message ?: "Unknown")}")
            null
        }
    }

    /** 原样读取磁盘内容（不做历史组名归一；供 [loadSanitized] 判断是否需要回写）。 */
    private fun readRaw(): RoutingState = runCatching {
        val file = stateFile()
        if (!file.exists()) return RoutingState()
        json.decodeFromString<RoutingState>(file.readText())
    }.getOrElse {
        AppLog.w(
            LOG_TAG,
            "load: 解析失败回退默认状态: ${sanitizeLog(it.message ?: "Unknown")}",
        )
        RoutingState()
    }

    /** 历史组名归一：`groups` 折叠旧组名，`order` 归一后去重保序；无变化时原样返回。 */
    private fun migrateLegacyNames(state: RoutingState): RoutingState {
        val groups = migrateLegacyGroups(state.groups)
        val order = migrateLegacyOrder(state.order)
        if (groups == state.groups && order == state.order) return state
        return state.copy(groups = groups, order = order)
    }

    /**
     * 原子写入（temp + rename）。参照 pitfall #13：Windows/部分文件系统上
     *
     * 注意：本方法自身不持锁；本类内所有写入口都在 [lock] 临界区内调用它。外部调用方
     * 需要自行串行化。
     * rename 不允许覆盖已存在目标，先删旧文件再重试一次；中途被杀最多残留
     * 一个 .tmp，routing.json 要么是旧状态要么是新状态。
     */
    fun write(state: RoutingState): Boolean = runCatching {
        val file = stateFile()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(RoutingState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) error("rename failed: $FILE_NAME")
        }
        true
    }.getOrElse {
        AppLog.w(LOG_TAG, "write: 写入失败: ${sanitizeLog(it.message ?: "Unknown")}")
        false
    }

    /**
     * 状态文件不存在时写入启用状态（本地分流默认开启，产品语义），并同步
     * 自家后端域名清单；已存在时仅补域名清单（保留用户的组开关/自定义组）。
     * 仅在订阅导入流程（IO 线程）调用。
     */
    fun ensureDefault(directDomains: List<String>): Boolean = lock.withLock {
        if (!stateFile().exists()) {
            write(RoutingState(enabled = true, directDomains = directDomains))
        } else {
            syncDirectDomains(directDomains)
        }
    }

    /** 更新自家后端域名清单（App 侧构建期注入清单 → 内核生成直连规则）。 */
    fun syncDirectDomains(directDomains: List<String>): Boolean = lock.withLock { write(load().copy(directDomains = directDomains)) }

    /** 置总开关并落盘；返回写入是否成功。 */
    fun setEnabled(enabled: Boolean): Boolean = lock.withLock { write(load().copy(enabled = enabled)) }

    /** 置单组开关并落盘；返回写入是否成功。 */
    fun setGroupEnabled(
        name: String,
        enabled: Boolean,
    ): Boolean = lock.withLock {
        val current = load()
        write(current.copy(groups = current.groups + (name to enabled)))
    }

    /**
     * 清空全部组的自定义开关**与**拖动顺序，一键回到内置默认值；返回写入是否成功。
     *
     * 顺序也必须一起清：拖动过的顺序写进 `routing.json` 后会长期覆盖内置顺序，
     * 只清开关的话，「恢复默认」按下去列表顺序仍旧是用户拖出来的样子。
     */
    fun resetGroups(): Boolean = lock.withLock { write(load().copy(groups = emptyMap(), order = emptyList())) }

    /**
     * 置内置分流组顺序并落盘；返回写入是否成功。
     *
     * 只记录**合法且存在**的组名（去重、保序），避免把未知名写进 `routing.json`；
     * 未列出的组由内核按内置默认顺序补在末尾，因此这里不做补全。
     */
    fun setGroupOrder(order: List<String>): Boolean = lock.withLock {
        val known = RoutingGroups.mapTo(HashSet()) { it.name }
        val clean = LinkedHashSet<String>()
        order.forEach { name -> if (name in known) clean += name }
        write(load().copy(order = clean.toList()))
    }

    /**
     * 追加自定义规则组（名称非法 / 同名 / 与内置保留组同名一律拒绝）；
     * 返回写入是否成功。
     */
    fun addCustomGroup(group: RoutingCustomGroup): Boolean = lock.withLock {
        if (!RoutingInputValidator.isValidGroupName(group.name)) return@withLock false
        val current = load()
        if (current.custom.any { it.name == group.name }) return@withLock false
        write(current.copy(custom = current.custom + group))
    }

    /** 删除自定义规则组；返回写入是否成功。 */
    fun removeCustomGroup(name: String): Boolean = lock.withLock {
        val current = load()
        write(current.copy(custom = current.custom.filter { it.name != name }))
    }

    companion object {
        private const val LOG_TAG = "Polaris-Routing"
        const val FILE_NAME = "routing.json"

        /** 内核写的降级标记文件名（与 Go routing.DegradedPath 一致）。 */
        const val DEGRADED_FILE_NAME = "routing-degraded.json"

        /** 内置分流种子在 APK assets 中的目录（与 Go providerSubPath 无关）。 */
        const val ASSETS_PROVIDERS_DIR = "routing/providers"

        /** 内核 profileDir 下 provider 缓存子目录，与 Go providerSubPath 一致。 */
        const val PROVIDERS_SUB_DIR = "polaris-rules"
    }
}
