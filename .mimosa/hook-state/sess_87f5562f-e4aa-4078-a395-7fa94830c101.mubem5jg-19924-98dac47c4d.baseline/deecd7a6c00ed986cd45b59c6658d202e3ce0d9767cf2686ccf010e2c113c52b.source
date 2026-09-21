package com.slte.app.domain.usecase

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class DaysUntilExpiryUseCase
@Inject
constructor() {
    operator fun invoke(expiredAtEpochSeconds: Long): Int {
        if (expiredAtEpochSeconds <= 0L) return 0
        val now = Instant.now()
        val ceiling = now.plus(MAX_HORIZON_DAYS, ChronoUnit.DAYS)
        val target =
            if (expiredAtEpochSeconds >= ceiling.epochSecond) {
                ceiling
            } else {
                Instant.ofEpochSecond(expiredAtEpochSeconds)
            }
        return ChronoUnit.DAYS.between(now, target).coerceAtLeast(0L).toInt()
    }

    private companion object {
        private const val MAX_HORIZON_DAYS = 36_500L
    }
}
