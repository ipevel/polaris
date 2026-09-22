// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app

import com.slte.app.ui.screen.about.compareVersions
import com.slte.app.ui.screen.about.shouldShowUpdateDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateViewModelLogicTest {
    @Test
    fun manualCheckAlwaysResponds() {
        assertTrue(shouldShowUpdateDialog("1.1.0", "1.0.0", force = false, dismissedInSession = true, manual = true))
        assertFalse(shouldShowUpdateDialog("1.1.0", "1.0.0", force = false, dismissedInSession = true, manual = false))
        assertTrue(shouldShowUpdateDialog("1.1.0", "1.0.0", force = false, dismissedInSession = false, manual = false))
    }

    @Test
    fun forceAlwaysShows() {
        assertTrue(shouldShowUpdateDialog("1.1.0", "1.0.0", force = true, dismissedInSession = true, manual = false))
    }

    @Test
    fun noUpdateMeansNoDialog() {
        assertFalse(shouldShowUpdateDialog("1.0.0", "1.0.0", force = false, dismissedInSession = false, manual = true))
        assertFalse(shouldShowUpdateDialog("", "1.0.0", force = false, dismissedInSession = false, manual = true))
        assertTrue(compareVersions("1.0.0-debug", "1.0.0") == 0)
    }

    @Test
    fun compareVersions_大小与各段差异() {
        assertTrue(compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.0.1", "1.0.0") > 0)
        assertEquals(0, compareVersions("1.0.0", "1.0.0"))

        assertEquals(0, compareVersions("v1.2.3", "1.2.3"))
        assertEquals(0, compareVersions("1.2.3-rc1", "1.2.3"))

        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertTrue(compareVersions("1.0.1", "1.0") > 0)

        assertTrue(compareVersions("abc", "1.0.0") < 0)
        assertTrue(compareVersions("", "0.0.1") < 0)
    }
}
