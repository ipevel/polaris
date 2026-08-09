package com.slte.app.data.repository

import kotlinx.coroutines.CancellationException

/**
 * Repository 层通用异常封装：suspend block → Result。
 * CancellationException 必须重抛；缓存回退只在 Repository 层做；异常必须留痕。
 */
internal suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
