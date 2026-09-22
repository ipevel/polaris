// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.notice

import com.slte.app.R
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.Notice
import com.slte.app.support.MainDispatcherRule
import com.slte.app.ui.ContentPhase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoticeViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = mockk<SubscribeRepository>(relaxed = true)

    private fun notice(id: Int) = Notice(
        id = id,
        title = "标题 $id",
        body = "正文",
        tags = emptyList(),
        createdAt = 1_700_000_000L,
    )

    @Test
    fun `初始为加载中，加载成功后进入空闲并填充列表`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.success(listOf(notice(1), notice(2)))
        val vm = NoticeViewModel(repository)

        assertEquals(ContentPhase.Loading, vm.uiState.value.phase)

        vm.loadNotices()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.uiState.value.phase)
        assertEquals(2, vm.uiState.value.notices.size)
        assertNull(vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `加载失败进入空闲并带全屏错误`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.failure(IllegalStateException("boom"))
        val vm = NoticeViewModel(repository)

        vm.loadNotices()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.uiState.value.phase)
        assertEquals(R.string.notice_error, vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `加载失败时透出脱敏后的具体原因`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.failure(IllegalStateException("未登录或登陆已过期"))
        val vm = NoticeViewModel(repository)

        vm.loadNotices()
        advanceUntilIdle()

        assertEquals("未登录或登陆已过期", vm.uiState.value.errorMessage)
    }

    @Test
    fun `刷新期间阶段为 Refreshing`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.success(listOf(notice(1)))
        val vm = NoticeViewModel(repository)
        vm.loadNotices()
        advanceUntilIdle()

        vm.refresh()
        assertEquals(ContentPhase.Refreshing, vm.uiState.value.phase)

        advanceUntilIdle()
        assertEquals(ContentPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `刷新失败但已有列表时只弹轻提示、保留内容`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.success(listOf(notice(1)))
        val vm = NoticeViewModel(repository)
        vm.loadNotices()
        advanceUntilIdle()

        coEvery { repository.fetchNotices() } returns Result.failure(IllegalStateException("boom"))
        vm.refresh()
        advanceUntilIdle()

        assertEquals(R.string.notice_refresh_failed, vm.uiState.value.toastRes)
        assertEquals("已有内容不应被清空", 1, vm.uiState.value.notices.size)
        assertNull("有数据时不走全屏错误", vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `刷新失败且无数据时进入全屏错误态`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.failure(IllegalStateException("boom"))
        val vm = NoticeViewModel(repository)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(R.string.notice_error, vm.uiState.value.errorMessageRes)
        assertNull(vm.uiState.value.toastRes)
        assertTrue(vm.uiState.value.notices.isEmpty())
    }

    @Test
    fun `预加载完成前 isEntering 为真`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchNotices() } returns Result.success(emptyList())
        val vm = NoticeViewModel(repository)

        vm.enterAndRefresh()
        assertTrue("预加载期间应保持进入中", vm.uiState.value.isEntering)

        advanceUntilIdle()
        assertTrue("预加载结束后应可进入页面", !vm.uiState.value.isEntering)
    }
}
