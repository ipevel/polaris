// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.slte.app.R
import java.io.File

/**
 * 本地分流配置（routing.json）导出 + 系统分享。
 *
 * 背景：routing.json 是私有 filesDir 里的**唯一副本**，系统备份关闭（allowBackup=false），
 * 被覆盖或清理后用户没有任何自救手段。这里把原文件复制到 App 专属外部下载目录
 * （file_paths.xml 已声明 external-files-path Download/）再走 FileProvider 分享；
 * 导出的是**磁盘原样**内容——包含 App 列表里暂时隐藏的条目。
 */
object RoutingBackupExport {
    fun exportAndShare(
        context: Context,
        source: File,
    ) {
        val target = copyToDownloads(context, source)
        if (target == null) {
            Toast.makeText(context, context.getString(R.string.routing_export_backup_failed), Toast.LENGTH_SHORT).show()
            return
        }
        share(context, target)
    }

    /** 复制到 App 专属下载目录；失败（含源文件不存在）返回 null。 */
    internal fun copyToDownloads(
        context: Context,
        source: File,
    ): File? {
        if (!source.isFile) return null
        return runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("no external files dir")
            dir.mkdirs()
            val target = File(dir, source.name)
            source.copyTo(target, overwrite = true)
            target
        }.getOrElse {
            AppLog.w("Polaris-Routing", "导出本地分流配置失败: ${sanitizeLog(it.message ?: "Unknown")}")
            null
        }
    }

    private fun share(
        context: Context,
        file: File,
    ) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.routing_export_backup))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.routing_export_backup_share)))
        }.onFailure {
            AppLog.w("Polaris-Routing", "分享本地分流配置失败: ${sanitizeLog(it.message ?: "Unknown")}")
            Toast.makeText(context, context.getString(R.string.routing_export_backup_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
