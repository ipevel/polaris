// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.slte.app.R

/**
 * 诊断日志导出 + 系统分享。
 *
 * 从关于页抽出来共用：流量页失败时也要能一键导出，用户不必先跑到「我的 → 关于」，
 * 再回头复述自己遇到的是什么问题。导出内容、提示文案与文件来源与关于页完全一致
 * （同一个 [AppLog.export]），行为不做第二套。
 */
object LogExport {
    /**
     * 导出并拉起系统分享面板。
     *
     * @param extra 附加诊断字段（后端类型/面板/构建类型等），会写进导出文件头部。
     */
    fun exportAndShare(
        context: Context,
        extra: Map<String, String> = emptyMap(),
    ) {
        val file = AppLog.export(context, extra)
        if (file == null) {
            Toast.makeText(context, context.getString(R.string.about_log_export_failed), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, context.getString(R.string.about_log_exported), Toast.LENGTH_SHORT).show()
        share(context, file)
    }

    private fun share(
        context: Context,
        file: java.io.File,
    ) {
        try {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_log_export))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.about_log_export_share)),
            )
        } catch (e: Exception) {
            AppLog.w("Polaris-Log", "导出日志分享失败: ${sanitizeLog(e.message ?: "Unknown")}")
            Toast.makeText(context, context.getString(R.string.about_log_share_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
