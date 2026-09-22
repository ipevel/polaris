// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.di.IoDispatcher
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

data class KernelFault(
    val operation: String,
    val cause: Throwable,
)

@Singleton
class KernelFaultReporter
@Inject
constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _faults = MutableSharedFlow<KernelFault>(extraBufferCapacity = FAULT_BUFFER)

    val faults: SharedFlow<KernelFault> = _faults.asSharedFlow()

    fun report(fault: KernelFault) {
        _faults.tryEmit(fault)
    }

    internal suspend fun <T> guard(
        default: T,
        operation: String,
        tag: String = DEFAULT_FAULT_TAG,
        block: suspend () -> T,
    ): T = try {
        withContext(ioDispatcher) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.logAsFault(tag)
        report(KernelFault(operation = operation, cause = e))
        default
    }

    private companion object {
        const val FAULT_BUFFER = 8
    }
}

private val PROGRAMMATIC_FAULTS =
    setOf(
        NullPointerException::class,
        IllegalStateException::class,
        IndexOutOfBoundsException::class,
        ArrayIndexOutOfBoundsException::class,
        ClassCastException::class,
        NoSuchElementException::class,
        ArithmeticException::class,
        ConcurrentModificationException::class,
        kotlin.UninitializedPropertyAccessException::class,
    )

private fun Throwable.isProgrammaticFault(): Boolean = PROGRAMMATIC_FAULTS.any { it.isInstance(this) } ||
    javaClass.name.startsWith("kotlin.")

internal const val DEFAULT_FAULT_TAG = "Polaris-Kernel"

internal fun Exception.logAsFault(tag: String = "Polaris-Kernel") {
    val summary = "${javaClass.simpleName}: ${sanitizeLog(message ?: "Unknown")}"
    if (isProgrammaticFault()) {
        AppLog.e(tag, "$summary\n${stackTraceToString()}")
    } else {
        AppLog.w(tag, summary)
    }
}
