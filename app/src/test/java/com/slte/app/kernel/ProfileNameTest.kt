// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameTest {
    @Test
    fun `同一邮箱标记稳定`() {
        assertEquals(profileNameFor("user@example.com"), profileNameFor("user@example.com"))
    }

    @Test
    fun `不同邮箱标记可区分`() {
        assertNotEquals(profileNameFor("a@example.com"), profileNameFor("b@example.com"))
    }

    @Test
    fun `无邮箱回退通用名`() {
        assertEquals("Polaris", profileNameFor(null))
        assertEquals("Polaris", profileNameFor(""))
        assertEquals("Polaris", profileNameFor("   "))
    }

    @Test
    fun `标记前缀与长度固定`() {
        val name = profileNameFor("user@example.com")
        assertTrue(name.startsWith("Polaris-"))

        assertEquals("Polaris-".length + 64, name.length)
    }
}
