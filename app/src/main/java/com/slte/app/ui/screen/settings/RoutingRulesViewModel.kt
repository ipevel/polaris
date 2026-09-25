// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.remote.config.ConfigValidation
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.RoutingCustomGroup
import com.slte.app.kernel.RoutingGroups
import com.slte.app.kernel.RoutingInputValidator
import com.slte.app.kernel.RoutingStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RoutingSync { Idle, Saving }

data class RoutingRuleItem(
    val name: String,
    val defaultOn: Boolean,
    val defaultOut: String,
    val enabled: Boolean,
)

data class RoutingRulesData(
    val items: List<RoutingRuleItem> = emptyList(),
    val sync: RoutingSync = RoutingSync.Idle,
    val errorMessageRes: Int? = null,
    val savedCount: Int = 0,
    val custom: List<RoutingCustomGroup> = emptyList(),
)

data class CustomGroupForm(
    val name: String = "",
    val url: String = "",
    val behavior: String = "classical",
)

sealed interface CustomGroupState {
    object Closed : CustomGroupState

    data class Editing(
        val form: CustomGroupForm = CustomGroupForm(),
        val submitting: Boolean = false,
        val errorMessageRes: Int? = null,
    ) : CustomGroupState
}

@HiltViewModel
class RoutingRulesViewModel
@Inject
constructor(
    private val routingStateStore: RoutingStateStore,
    private val kernelConfig: KernelConfig,
) : ViewModel() {

    private val _data = MutableStateFlow(RoutingRulesData())
    val data: StateFlow<RoutingRulesData> = _data.asStateFlow()

    private val _customGroupState = MutableStateFlow<CustomGroupState>(CustomGroupState.Closed)
    val customGroupState: StateFlow<CustomGroupState> = _customGroupState.asStateFlow()

    init {
        refresh()
    }

    /** 组的生效开关：routing.json 有覆盖用覆盖值，否则用内置默认。 */
    private fun refresh() {
        // loadSanitized：旧版本或其它写入点可能在 routing.json 留下非法名称/
        // 域名（会让整份内核配置加载失败），读时清掉并回写，同时提示用户。
        val sanitized = routingStateStore.loadSanitized()
        val state = sanitized.state
        val items =
            RoutingGroups.map { group ->
                RoutingRuleItem(
                    name = group.name,
                    defaultOn = group.defaultOn,
                    defaultOut = group.defaultOut,
                    enabled = state.groups[group.name] ?: group.defaultOn,
                )
            }
        _data.update {
            it.copy(
                items = items,
                custom = state.custom,
                errorMessageRes =
                if (sanitized.hasDropped) {
                    R.string.routing_custom_cleaned_invalid
                } else {
                    it.errorMessageRes
                },
            )
        }
    }

    fun setEnabled(
        name: String,
        enabled: Boolean,
    ) {
        if (_data.value.sync == RoutingSync.Saving) return
        // 记录切换前的生效值：写盘失败时回滚到它（而非内置默认）
        val previous = _data.value.items.firstOrNull { it.name == name }?.enabled ?: return
        _data.update { state ->
            state.copy(
                sync = RoutingSync.Saving,
                errorMessageRes = null,
                items =
                state.items.map {
                    if (it.name == name) it.copy(enabled = enabled) else it
                },
            )
        }
        viewModelScope.launch {
            val written = kernelConfig.applyRoutingGroup(name, enabled)
            if (written) {
                _data.update { it.copy(sync = RoutingSync.Idle, savedCount = it.savedCount + 1) }
            } else {
                _data.update { state ->
                    state.copy(
                        sync = RoutingSync.Idle,
                        errorMessageRes = R.string.settings_local_routing_failed,
                        items =
                        state.items.map {
                            if (it.name == name) it.copy(enabled = previous) else it
                        },
                    )
                }
            }
        }
    }

    fun resetDefaults() {
        if (_data.value.sync == RoutingSync.Saving) return
        _data.update { it.copy(sync = RoutingSync.Saving, errorMessageRes = null) }
        viewModelScope.launch {
            val written = kernelConfig.resetRoutingGroups()
            if (written) {
                refresh()
                _data.update { it.copy(sync = RoutingSync.Idle, savedCount = it.savedCount + 1) }
            } else {
                _data.update {
                    it.copy(
                        sync = RoutingSync.Idle,
                        errorMessageRes = R.string.settings_local_routing_failed,
                    )
                }
            }
        }
    }

    fun consumeError() {
        _data.update { it.copy(errorMessageRes = null) }
    }

    fun consumeSaved() {
        _data.update { it.copy(savedCount = 0) }
    }

    // ---- 自定义规则组（M3）----

    fun showAddCustomGroup() {
        _customGroupState.value = CustomGroupState.Editing()
    }

    fun dismissCustomGroup() {
        _customGroupState.value = CustomGroupState.Closed
    }

    fun onCustomNameChange(value: String) {
        updateEditing { it.copy(name = value) }
    }

    fun onCustomUrlChange(value: String) {
        updateEditing { it.copy(url = value) }
    }

    fun onCustomBehaviorChange(value: String) {
        updateEditing { it.copy(behavior = value) }
    }

    /**
     * 校验并提交自定义规则组。名称 ≤32 字符且不与内置/已有组冲突；
     * URL 仅接受 https 且 host 不得为私有/保留地址（规则由内核直接抓取，
     * 这里在入口侧阻断本地网络探测向量）。失败回路由内核静态回退语义。
     */
    fun submitCustomGroup() {
        val state = _customGroupState.value as? CustomGroupState.Editing ?: return
        if (state.submitting) return
        val form = state.form
        val name = form.name.trim()
        val url = form.url.trim()
        val error =
            when {
                // 字符集校验（逗号/控制字符）与保留名判定统一收敛到 validator，
                // 与内核侧 reserved.go 的规则保持一致。
                !RoutingInputValidator.isValidGroupName(name) -> R.string.routing_custom_invalid_name
                !isValidPublicHttpsUrl(url) -> R.string.routing_custom_invalid_url
                else -> null
            }
        if (error != null) {
            _customGroupState.value = state.copy(errorMessageRes = error)
            return
        }
        _customGroupState.value =
            state.copy(submitting = true, errorMessageRes = null)
        viewModelScope.launch {
            val written = kernelConfig.addRoutingCustomGroup(RoutingCustomGroup(name = name, url = url, behavior = form.behavior))
            if (written) {
                _customGroupState.value = CustomGroupState.Closed
                refresh()
                _data.update { it.copy(savedCount = it.savedCount + 1) }
            } else {
                // 同名冲突（存储层去重）或写盘失败
                _customGroupState.value =
                    state.copy(
                        submitting = false,
                        errorMessageRes = R.string.routing_custom_duplicate_name,
                    )
            }
        }
    }

    fun removeCustomGroup(name: String) {
        viewModelScope.launch {
            if (kernelConfig.removeRoutingCustomGroup(name)) {
                refresh()
            }
        }
    }

    private fun updateEditing(transform: (CustomGroupForm) -> CustomGroupForm) {
        val current = _customGroupState.value as? CustomGroupState.Editing ?: return
        _customGroupState.value = current.copy(form = transform(current.form), errorMessageRes = null)
    }

    private fun isValidPublicHttpsUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        if (host.isBlank()) return false
        return !ConfigValidation.isPrivateOrReservedHost(host)
    }
}
