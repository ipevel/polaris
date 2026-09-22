// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.usecase

import com.slte.app.utils.VerificationCodeConfig
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CountdownUseCase
@Inject
constructor() {
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
