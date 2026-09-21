package com.slte.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
