package com.slte.app.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Geo 资源安装：逐文件临时写 + fsync + 原子替换，全部成功后再写版本标记。
 *
 * 原子替换保证内核在任何时刻读到的都是完整文件；版本标记 + 文件大小用于
 * 下次启动跳过重复解包，也能发现被截断的文件。
 */
object GeoInstaller {
    private const val MARKER = ".geo-version"

    fun install(
        directory: File,
        version: Long,
        entries: Map<String, () -> InputStream>,
    ) {
        directory.mkdirs()
        deleteTempFiles(directory, entries.keys)

        if (isUpToDate(directory, version, entries.keys)) return

        val sizes = LinkedHashMap<String, Long>()
        entries.forEach { (name, opener) ->
            val target = File(directory, name)
            val temp = File(directory, "$name.tmp")
            temp.delete()
            try {
                opener().use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                moveAtomic(temp, target)
            } finally {
                temp.delete()
            }
            sizes[name] = target.length()
        }

        writeMarker(directory, version, sizes)
    }

    fun isUpToDate(
        directory: File,
        version: Long,
        names: Collection<String>,
    ): Boolean {
        val (installedVersion, sizes) = readMarker(File(directory, MARKER)) ?: return false
        if (installedVersion != version) return false
        return names.all { name ->
            val file = File(directory, name)
            val size = sizes[name] ?: return@all false
            file.isFile && file.length() == size
        }
    }

    private fun writeMarker(
        directory: File,
        version: Long,
        sizes: Map<String, Long>,
    ) {
        val marker = File(directory, MARKER)
        val temp = File(directory, "$MARKER.tmp")
        temp.delete()
        try {
            FileOutputStream(temp).use { output ->
                output.write("version=$version\n".toByteArray())
                sizes.forEach { (name, size) ->
                    output.write("$name=$size\n".toByteArray())
                }
                output.fd.sync()
            }
            moveAtomic(temp, marker)
        } finally {
            temp.delete()
        }
    }

    private fun readMarker(marker: File): Pair<Long, Map<String, Long>>? {
        if (!marker.isFile) return null

        val lines = runCatching { marker.readLines() }.getOrNull() ?: return null
        val versionLine = lines.firstOrNull() ?: return null
        if (!versionLine.startsWith("version=")) return null
        val version = versionLine.removePrefix("version=").toLongOrNull() ?: return null

        val sizes = LinkedHashMap<String, Long>()
        lines.drop(1).forEach { line ->
            val index = line.lastIndexOf('=')
            if (index <= 0) return null
            val size = line.substring(index + 1).toLongOrNull() ?: return null
            sizes[line.substring(0, index)] = size
        }

        return version to sizes
    }

    private fun deleteTempFiles(
        directory: File,
        names: Collection<String>,
    ) {
        (names.map { "$it.tmp" } + "$MARKER.tmp").forEach { name ->
            File(directory, name).delete()
        }
    }

    private fun moveAtomic(
        source: File,
        target: File,
    ) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (!target.isFile) throw IOException("rename ${source.name} -> ${target.name} failed")
    }
}
