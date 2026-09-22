// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialStoreTest {
    private val prefs = InMemoryPreferences()
    private val store = CredentialStore(prefs)

    @Test
    fun `保存后读取往返`() {
        store.save(email = "user@example.com", password = "s3cret")

        assertEquals("user@example.com", store.getSavedEmail())
        assertEquals("s3cret", store.getSavedPassword())
    }

    @Test
    fun `未保存时读取为 null`() {
        assertNull(store.getSavedEmail())
        assertNull(store.getSavedPassword())
    }

    @Test
    fun `单槽位：后保存的账号覆盖前一个`() {
        store.save(email = "first@example.com", password = "p1")
        store.save(email = "second@example.com", password = "p2")

        assertEquals("second@example.com", store.getSavedEmail())
        assertEquals("p2", store.getSavedPassword())
    }

    @Test
    fun `clearPassword 保留邮箱`() {
        store.save(email = "user@example.com", password = "old")

        store.clearPassword()

        assertEquals("改密后仍需预填邮箱", "user@example.com", store.getSavedEmail())
        assertNull(store.getSavedPassword())
    }

    @Test
    fun `clear 清空邮箱与密码`() {
        store.save(email = "user@example.com", password = "p")

        store.clear()

        assertNull(store.getSavedEmail())
        assertNull(store.getSavedPassword())
    }
}
