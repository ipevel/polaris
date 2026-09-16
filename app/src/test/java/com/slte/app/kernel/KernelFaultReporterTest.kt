package com.slte.app.kernel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelFaultReporterTest {
    private val reporter = KernelFaultReporter(UnconfinedTestDispatcher())

    @Test
    fun 成功时返回业务值且不上报故障() = runTest {
        val faults = mutableListOf<KernelFault>()
        backgroundScope.launch { reporter.faults.collect { faults += it } }
        runCurrent()

        val result = reporter.guard(0, "demoOperation") { 7 }
        runCurrent()

        assertEquals(7, result)
        assertTrue("成功不应上报故障", faults.isEmpty())
    }

    @Test
    fun 失败时返回默认值并上报带操作名的故障() = runTest {
        val faults = mutableListOf<KernelFault>()
        backgroundScope.launch { reporter.faults.collect { faults += it } }
        runCurrent()

        val result = reporter.guard("fallback", "selectNode") { throw IllegalStateException("内核不可用") }
        runCurrent()

        assertEquals("fallback", result)
        assertEquals(1, faults.size)
        assertEquals("selectNode", faults.first().operation)
        assertTrue(faults.first().cause is IllegalStateException)
    }

    @Test
    fun 取消异常必须继续抛出且不计为故障() = runTest {
        val faults = mutableListOf<KernelFault>()
        backgroundScope.launch { reporter.faults.collect { faults += it } }
        runCurrent()

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                reporter.guard(0, "cancelledOperation") { throw CancellationException("cancelled") }
            }
        }
        runCurrent()

        assertTrue("取消不是故障", faults.isEmpty())
    }

    @Test
    fun 无订阅者时上报不会阻塞() = runTest {
        repeat(3) {
            assertEquals("fallback", reporter.guard("fallback", "noSubscriber") { throw IllegalStateException("boom") })
        }
    }
}
