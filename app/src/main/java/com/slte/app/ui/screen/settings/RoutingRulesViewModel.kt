// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.RoutingGroups
import com.slte.app.kernel.RoutingStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class RoutingRulesViewModel
@Inject
constructor(
    private val routingStateStore: RoutingStateStore,
    private val kernelConfig: KernelConfig,
) : ViewModel() {

    private val _data = MutableStateFlow(RoutingRulesData())
    val data: StateFlow<RoutingRulesData> = _data.asStateFlow()

    init {
        refresh()
    }

    /** 组的生效开关：routing.json 有覆盖用覆盖值，否则用内置默认。 */
    private fun refresh() {
        val state = routingStateStore.load()
        val items =
            RoutingGroups.map { group ->
                RoutingRuleItem(
                    name = group.name,
                    defaultOn = group.defaultOn,
                    defaultOut = group.defaultOut,
                    enabled = state.groups[group.name] ?: group.defaultOn,
                )
            }
        _data.update { it.copy(items = items) }
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
}
