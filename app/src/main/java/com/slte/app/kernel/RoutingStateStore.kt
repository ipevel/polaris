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
    val enabled: Boolean = true,
    val groups: Map<String, Boolean> = emptyMap(),
    val custom: List<RoutingCustomGroup> = emptyList(),
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
     * 状态文件不存在时写入默认状态（本地分流启用）。幂等；已存在时不覆盖，
     * 保留用户的组开关与自定义组。仅在订阅导入流程（IO 线程）调用。
     */
    fun ensureDefault(): Boolean {
        if (stateFile().exists()) return true
        return write(RoutingState())
    }

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

    companion object {
        private const val LOG_TAG = "Polaris-Routing"
        const val FILE_NAME = "routing.json"

        /** 内置分流种子在 APK assets 中的目录（与 Go providerSubPath 无关）。 */
        const val ASSETS_PROVIDERS_DIR = "routing/providers"

        /** 内核 profileDir 下 provider 缓存子目录，与 Go providerSubPath 一致。 */
        const val PROVIDERS_SUB_DIR = "polaris-rules"
    }
}
