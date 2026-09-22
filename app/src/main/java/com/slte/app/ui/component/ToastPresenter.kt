// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class ToastHandle internal constructor(private val context: Context) {
    fun show(
        message: String,
        centered: Boolean = false,
    ) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        if (centered) {
            toast.setGravity(Gravity.CENTER, 0, 0)
        }
        toast.show()
    }

    fun show(
        @StringRes messageRes: Int,
        centered: Boolean = false,
    ) = show(context.getString(messageRes), centered)
}

@Composable
fun rememberToast(): ToastHandle {
    val context = LocalContext.current
    return remember(context) { ToastHandle(context) }
}
