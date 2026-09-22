// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EndpointSnapshot(
    val url: String,
    val state: HealthState,
    val consecutiveFailures: Int,
    val lastLatencyMs: Long,
)

data class EndpointSelectionState(
    val primary: String?,
    val endpoints: List<EndpointSnapshot>,
)

@Singleton
class EndpointSelector
@Inject
constructor() {
    private val healthMap = ConcurrentHashMap<String, EndpointHealth>()

    private val _state = MutableStateFlow(EndpointSelectionState(null, emptyList()))

    val state: StateFlow<EndpointSelectionState> = _state.asStateFlow()

    fun recordSuccess(
        url: String,
        latencyMs: Long,
    ) {
        val now = System.currentTimeMillis()

        healthMap.compute(url) { _, previous ->
            EndpointHealthRules.onSuccess(previous ?: EndpointHealth(url), latencyMs, now)
        }
        refreshState()
    }

    fun recordFailure(url: String) {
        val now = System.currentTimeMillis()
        healthMap.compute(url) { _, previous ->
            EndpointHealthRules.onFailure(previous ?: EndpointHealth(url), now)
        }
        refreshState()
    }

    fun recordProbe(
        url: String,
        latencyMs: Long,
    ) {
        val now = System.currentTimeMillis()
        healthMap.compute(url) { _, previous ->
            EndpointHealthRules.onSuccess(previous ?: EndpointHealth(url), latencyMs, now)
        }
        refreshState()
    }

    fun isOpen(
        url: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean = healthMap[url]?.let { EndpointHealthRules.isOpen(it, now) } ?: false

    fun halfOpenCandidates(now: Long = System.currentTimeMillis()): List<String> = healthMap.values
        .filter { EndpointHealthRules.state(it, now) == HealthState.HALF_OPEN }
        .map { it.url }
        .sorted()

    fun candidateOrder(
        primary: String,
        candidates: List<String>,
    ): List<String> {
        val now = System.currentTimeMillis()
        val rest = candidates.filter { it != primary }
        val ordered =
            rest.sortedWith(
                compareBy { healthRank(healthMap[it], now) },
            )
        return listOf(primary) + ordered
    }

    fun pickPrimary(
        candidates: List<String>,
        probes: Map<String, Long>,
        currentPrimary: String?,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (candidates.isEmpty()) return null
        val healthy = probes.filterKeys { it in candidates }
        if (healthy.isEmpty()) return currentPrimary?.takeIf { it in candidates } ?: candidates.first()

        val currentHealth = currentPrimary?.let { healthMap[it] }
        val currentOpen = currentPrimary != null && EndpointHealthRules.isOpen(currentHealth ?: EndpointHealth(currentPrimary), now)
        val currentLatency = currentPrimary?.let { healthy[it] }

        if (!currentOpen && currentLatency != null && currentPrimary in candidates) {
            val faster =
                healthy.entries
                    .filter { it.key != currentPrimary }
                    .filter { EndpointHealthRules.shouldSwitchPrimary(currentLatency, it.value) }
                    .minByOrNull { it.value }
            return faster?.key ?: currentPrimary
        }

        return healthy.minByOrNull { it.value }?.key ?: candidates.first()
    }

    private fun healthRank(
        health: EndpointHealth?,
        now: Long,
    ): Int = when {
        health == null -> 0
        EndpointHealthRules.state(health, now) == HealthState.OPEN -> 3
        EndpointHealthRules.state(health, now) == HealthState.HALF_OPEN -> 2
        EndpointHealthRules.state(health, now) == HealthState.DEGRADED -> 1
        else -> 0
    }

    private fun refreshState() {
        val now = System.currentTimeMillis()
        _state.update { previous ->
            EndpointSelectionState(
                primary = previous.primary,
                endpoints =
                healthMap.entries.sortedBy { it.key }.map { (url, h) ->
                    EndpointSnapshot(
                        url = url,
                        state = EndpointHealthRules.state(h, now),
                        consecutiveFailures = h.consecutiveFailures,
                        lastLatencyMs = h.lastLatencyMs,
                    )
                },
            )
        }
    }

    internal fun updatePrimary(primary: String?) {
        _state.update { it.copy(primary = primary) }
    }
}
