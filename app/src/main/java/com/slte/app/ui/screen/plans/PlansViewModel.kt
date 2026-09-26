// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.PlanInfo
import com.slte.app.ui.ContentPhase
import com.slte.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansData(
    val plans: List<PlanInfo> = emptyList(),
    val phase: ContentPhase = ContentPhase.Loading,
    val isEntering: Boolean = false,
    val errorMessageRes: Int? = null,
)

@HiltViewModel
class PlansViewModel
@Inject
constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _data = MutableStateFlow(PlansData())
    val data: StateFlow<PlansData> = _data.asStateFlow()

    fun enterAndRefresh() {
        _data.update { it.copy(isEntering = true) }
        loadPlans()
    }

    fun retry() {
        loadPlans()
    }

    fun refresh() {
        if (_data.value.phase == ContentPhase.Refreshing) return
        _data.update { it.copy(phase = ContentPhase.Refreshing) }
        viewModelScope.launch {
            orderRepository.fetchPlans().fold(
                onSuccess = { plans ->
                    _data.update {
                        it.copy(
                            plans = plans.filter { p -> p.show },
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            errorMessageRes = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _data.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            // 套餐页用自己的错误文案映射：原先复用 forOrder，导致订阅页
                            // 加载失败时显示「订单操作失败」（文案与页面语义不符）。
                            errorMessageRes = ErrorMessages.forPlans(throwable),
                        )
                    }
                },
            )
        }
    }

    private fun loadPlans() {
        _data.update { it.copy(phase = ContentPhase.Loading, errorMessageRes = null) }
        viewModelScope.launch {
            orderRepository.fetchPlans().fold(
                onSuccess = { plans ->
                    _data.update {
                        it.copy(
                            plans = plans.filter { p -> p.show },
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            errorMessageRes = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _data.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            // 套餐页用自己的错误文案映射：原先复用 forOrder，导致订阅页
                            // 加载失败时显示「订单操作失败」（文案与页面语义不符）。
                            errorMessageRes = ErrorMessages.forPlans(throwable),
                        )
                    }
                },
            )
        }
    }
}
