package com.slte.app.domain.usecase

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 计算套餐到期剩余天数 UseCase。
 *
 * 根据到期时间戳和当前时间，计算距到期还有多少天。
 * 已到期（剩余 <= 0）或没有到期时间（0），返回 0。
 */
class DaysUntilExpiryUseCase @Inject constructor() {

    operator fun invoke(expiredAtEpochSeconds: Long): Int {
        if (expiredAtEpochSeconds <= 0L) return 0
        val now = Instant.now()
        val target = Instant.ofEpochSecond(expiredAtEpochSeconds)
        val days = ChronoUnit.DAYS.between(now, target).toInt()
        return days.coerceAtLeast(0)
    }
}
