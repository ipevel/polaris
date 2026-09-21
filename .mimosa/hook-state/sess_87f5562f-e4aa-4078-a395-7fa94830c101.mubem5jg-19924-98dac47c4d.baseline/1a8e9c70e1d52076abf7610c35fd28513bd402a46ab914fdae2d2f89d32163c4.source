package com.slte.app.data.remote.config

enum class HealthState {

    HEALTHY,

    DEGRADED,

    OPEN,

    HALF_OPEN,
}

data class EndpointHealth(
    val url: String,

    val consecutiveFailures: Int = 0,

    val lastSuccessAt: Long = 0L,

    val openedAt: Long = 0L,

    val backoffMs: Long = 0L,

    val lastLatencyMs: Long = 0L,
)

object EndpointHealthRules {

    const val FAILURE_THRESHOLD = 3

    const val BASE_BACKOFF_MS = 5_000L

    const val MAX_BACKOFF_MS = 5 * 60_000L

    const val JITTER_RATIO = 0.2

    const val STICKY_IMPROVE_RATIO = 0.3

    fun state(
        health: EndpointHealth,
        now: Long,
    ): HealthState = when {
        health.consecutiveFailures >= FAILURE_THRESHOLD &&
            now < health.openedAt + health.backoffMs -> HealthState.OPEN
        health.consecutiveFailures >= FAILURE_THRESHOLD -> HealthState.HALF_OPEN
        health.consecutiveFailures > 0 -> HealthState.DEGRADED
        else -> HealthState.HEALTHY
    }

    fun isOpen(
        health: EndpointHealth,
        now: Long,
    ): Boolean = state(health, now) == HealthState.OPEN

    fun onSuccess(
        health: EndpointHealth,
        latencyMs: Long,
        now: Long,
    ): EndpointHealth = EndpointHealth(
        url = health.url,
        lastSuccessAt = now,
        lastLatencyMs = latencyMs,
    )

    fun onFailure(
        health: EndpointHealth,
        now: Long,
    ): EndpointHealth {
        val failures = health.consecutiveFailures + 1
        if (failures < FAILURE_THRESHOLD) {
            return health.copy(consecutiveFailures = failures)
        }

        return health.copy(
            consecutiveFailures = failures,
            openedAt = now,
            backoffMs = computeBackoffMs(failures),
        )
    }

    private fun computeBackoffMs(failures: Int): Long {
        val exponent = (failures - FAILURE_THRESHOLD).coerceAtLeast(0)
        val base = (BASE_BACKOFF_MS shl exponent.coerceAtMost(8)).coerceAtMost(MAX_BACKOFF_MS)
        val jitter = (base * JITTER_RATIO).toLong()
        val jittered = (base - jitter) + (Math.random() * 2 * jitter).toLong()

        return jittered.coerceIn(0, MAX_BACKOFF_MS)
    }

    fun shouldSwitchPrimary(
        currentLatencyMs: Long,
        candidateLatencyMs: Long,
    ): Boolean = candidateLatencyMs < currentLatencyMs * (1 - STICKY_IMPROVE_RATIO)
}
