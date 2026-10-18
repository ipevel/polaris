// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
//
// 移植自上游 shgnx/slte c73c4af6 的「配置目录原子替换」工具（GPL-3.0）。

package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 目录原子替换；两次 rename 之间若进程崩溃，由 [recover] 恢复。 */
object AtomicFs {
    private const val BACKUP_SUFFIX = ".old"

    fun replaceDirectory(
        staging: File,
        target: File,
    ) {
        val backup = File(target.parentFile, target.name + BACKUP_SUFFIX)

        staging.parentFile?.mkdirs()
        target.parentFile?.mkdirs()
        fsyncRecursively(staging)

        backup.deleteRecursively()
        if (target.exists() && !target.renameTo(backup)) {
            throw IOException("Cannot move ${target.name} aside")
        }
        if (!staging.renameTo(target)) {
            runCatching { backup.renameTo(target) }
            throw IOException("Cannot replace ${target.name}")
        }
        backup.deleteRecursively()
    }

    /** 恢复被中断的目录替换：残留的 `<name>.old` 要么回滚、要么清理。 */
    fun recover(directory: File) {
        if (!directory.isDirectory) return

        directory.listFiles()?.forEach { file ->
            if (!file.name.endsWith(BACKUP_SUFFIX)) return@forEach

            val target = File(directory, file.name.removeSuffix(BACKUP_SUFFIX))
            if (target.exists()) {
                file.deleteRecursively()
            } else if (file.renameTo(target)) {
                Log.w("Recovered ${target.name} from interrupted profile swap")
            }
        }
    }

    private fun fsyncRecursively(directory: File) {
        directory
            .walkBottomUp()
            .filter { it.isFile }
            .forEach { file ->
                FileOutputStream(file, true).use { it.fd.sync() }
            }
    }
}
