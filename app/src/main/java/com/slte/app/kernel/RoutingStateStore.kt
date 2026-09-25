// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
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
    fun loadSanitized(): SanitizedRoutingState {
        val raw = load()
        val custom = raw.custom.filter { RoutingInputValidator.isValidGroupName(it.name) }
        val domains = raw.directDomains.filter { RoutingInputValidator.isValidRuleDomainShape(it) }
        val droppedCustom = raw.custom.size - custom.size
        val droppedDomains = raw.directDomains.size - domains.size
        val clean = raw.copy(custom = custom, directDomains = domains)
        if (droppedCustom > 0 || droppedDomains > 0) {
            AppLog.w(
                LOG_TAG,
                "load: 已清除非法本地分流项 custom=$droppedCustom domain=$droppedDomains",
            )
            write(clean)
        }
        return SanitizedRoutingState(clean, droppedCustom, droppedDomains)
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

    /** 读取状态（不含清洗回写；供写入路径内部使用）。 */
    fun load(): RoutingState = runCatching {
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

    /**
     * 原子写入（temp + rename）。参照 pitfall #13：Windows/部分文件系统上
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
    fun ensureDefault(directDomains: List<String>): Boolean {
        if (!stateFile().exists()) {
            return write(RoutingState(enabled = true, directDomains = directDomains))
        }
        return syncDirectDomains(directDomains)
    }

    /** 更新自家后端域名清单（App 侧构建期注入清单 → 内核生成直连规则）。 */
    fun syncDirectDomains(directDomains: List<String>): Boolean = write(load().copy(directDomains = directDomains))

    /** 置总开关并落盘；返回写入是否成功。 */
    fun setEnabled(enabled: Boolean): Boolean = write(load().copy(enabled = enabled))

    /** 置单组开关并落盘；返回写入是否成功。 */
    fun setGroupEnabled(
        name: String,
        enabled: Boolean,
    ): Boolean {
        val current = load()
        return write(current.copy(groups = current.groups + (name to enabled)))
    }

    /** 清空全部组开关（恢复内置默认）；返回写入是否成功。 */
    fun resetGroups(): Boolean = write(load().copy(groups = emptyMap()))

    /**
     * 追加自定义规则组（名称非法 / 同名 / 与内置保留组同名一律拒绝）；
     * 返回写入是否成功。
     */
    fun addCustomGroup(group: RoutingCustomGroup): Boolean {
        if (!RoutingInputValidator.isValidGroupName(group.name)) return false
        val current = load()
        if (current.custom.any { it.name == group.name }) return false
        return write(current.copy(custom = current.custom + group))
    }

    /** 删除自定义规则组；返回写入是否成功。 */
    fun removeCustomGroup(name: String): Boolean {
        val current = load()
        return write(current.copy(custom = current.custom.filter { it.name != name }))
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
