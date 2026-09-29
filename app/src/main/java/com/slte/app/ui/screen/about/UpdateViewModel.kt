// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelProxy
import com.slte.app.utils.Diagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 关于页的 ViewModel。
 *
 * 类名沿用 `UpdateViewModel` 是历史原因：它原本同时承担「应用内更新」与关于页自身的展示职责。
 * 应用内更新功能（版本检测、更新弹窗、跳转下载页）已整体移除后，本类只保留**与更新无关**的
 * 关于页职责，不再读取任何远端 `update_*` 字段，也不再发起任何更新相关的网络请求：
 * - [kernelVersion]：内核版本行（关于页展示 + 日志导出上下文）
 * - [siteInfo]：面板下发的应用名/描述，用于应用标识卡
 * - [diagnosticsExtra]：导出诊断日志时附带的内核版本
 */
@HiltViewModel
class UpdateViewModel
@Inject
constructor(
    private val kernelProxy: KernelProxy,
    private val siteInfoStore: SiteInfoStore,
    private val diagnostics: Diagnostics,
) : ViewModel() {
    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    /** 导出诊断时附带的上下文；内核版本由本页负责查询，所以在基础字段上补一条。 */
    fun diagnosticsExtra(): Map<String, String> = diagnostics.extra() + mapOf("内核版本" to (_kernelVersion.value ?: "-"))

    init {
        viewModelScope.launch {
            repeat(10) {
                _kernelVersion.value = kernelProxy.coreVersion()
                if (_kernelVersion.value != null) return@launch
                delay(1000)
            }
        }

        // 站点名/描述由订阅生命周期维护（订阅头 + comm/config 写入 SiteInfoStore），
        // 关于页只观察回放
        viewModelScope.launch {
            siteInfoStore.siteInfo.collect { _siteInfo.value = it }
        }
    }
}
