// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.OrderInfo
import com.slte.app.ui.ContentPhase
import com.slte.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersData(
    val orders: List<OrderInfo> = emptyList(),
    val phase: ContentPhase = ContentPhase.Loading,
    val isEntering: Boolean = false,
    val errorMessageRes: Int? = null,
    val toastRes: Int? = null,
)

@HiltViewModel
class OrdersViewModel
@Inject
constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _data = MutableStateFlow(OrdersData())
    val data: StateFlow<OrdersData> = _data.asStateFlow()

    fun enterAndRefresh() {
        _data.update { it.copy(isEntering = true) }
        loadOrders()
    }

    fun retry() {
        loadOrders()
    }

    fun refresh() {
        if (_data.value.phase == ContentPhase.Refreshing) return
        _data.update { it.copy(phase = ContentPhase.Refreshing) }
        viewModelScope.launch {
            orderRepository.fetchOrders().fold(
                onSuccess = { orders ->
                    _data.update { it.copy(orders = orders, phase = ContentPhase.Idle, isEntering = false, errorMessageRes = null) }
                },
                onFailure = { throwable ->
                    _data.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            errorMessageRes = ErrorMessages.forOrder(throwable),
                        )
                    }
                },
            )
        }
    }

    fun cancelOrder(tradeNo: String) {
        viewModelScope.launch {
            orderRepository.cancelOrder(tradeNo).fold(
                onSuccess = {
                    _data.update { it.copy(toastRes = R.string.order_cancel_success) }
                    loadOrders()
                },
                onFailure = {
                    _data.update { it.copy(toastRes = R.string.order_cancel_failed) }
                },
            )
        }
    }

    fun clearToast() {
        _data.update { it.copy(toastRes = null) }
    }

    private fun loadOrders() {
        _data.update { it.copy(phase = ContentPhase.Loading, errorMessageRes = null) }
        viewModelScope.launch {
            orderRepository.fetchOrders().fold(
                onSuccess = { orders ->
                    _data.update { it.copy(orders = orders, phase = ContentPhase.Idle, isEntering = false) }
                },
                onFailure = { throwable ->
                    _data.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            isEntering = false,
                            errorMessageRes = ErrorMessages.forOrder(throwable),
                        )
                    }
                },
            )
        }
    }
}
