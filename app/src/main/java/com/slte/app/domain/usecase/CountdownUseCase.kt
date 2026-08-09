package com.slte.app.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.slte.app.utils.VerificationCodeConfig
import javax.inject.Inject

/**
 * 验证码倒计时 UseCase。
 *
 * 从 VerificationCodeConfig.countdownSeconds 开始倒数到 0，
 * 每秒发射一次剩余秒数。到达 0 后自动结束 Flow。
 */
class CountdownUseCase @Inject constructor() {

    operator fun invoke(): Flow<Int> = flow {
        var remaining = VerificationCodeConfig.countdownSeconds
        while (remaining > 0) {
            emit(remaining)
            delay(VerificationCodeConfig.countdownIntervalMs)
            remaining--
        }
        emit(0)
    }
}
