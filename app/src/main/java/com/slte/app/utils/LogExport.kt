package com.slte.app.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.slte.app.R

/** 日志导出结果，由调用方决定提示文案。 */
enum class LogExportResult { SUCCESS, EXPORT_FAILED, SHARE_FAILED }

/** 导出应用日志并唤起系统分享面板（关于页「日志导出」与登录页长按版本号共用）。 */
fun exportAndShareLog(context: Context): LogExportResult {
    val file = AppLog.export(context) ?: return LogExportResult.EXPORT_FAILED
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_log_export))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.about_log_export_share))
                // 调用方可能传入 Application Context
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        LogExportResult.SUCCESS
    } catch (e: Exception) {
        LogExportResult.SHARE_FAILED
    }
}
