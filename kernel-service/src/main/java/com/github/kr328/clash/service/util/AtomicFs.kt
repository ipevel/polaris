package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 目录原子替换：staging 写完后先备份旧目录，再改名新目录；两次 rename 之间的
 * 崩溃窗口由 [recover] 在服务启动时兜底恢复。
 */
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
