package com.slte.app.kernel

import java.io.File
import java.io.FileOutputStream

/** profile 目录三件套：内核加载的 config.yaml、pristine 清洗副本、内核类型标记。 */
internal object ProfileFiles {

    const val CONFIG_FILE = "config.yaml"
    const val PRISTINE_FILE = "config.pristine"
    const val MARKER_FILE = ".slte-kernel"

    /** 写入内核加载的 config.yaml、pristine 副本与内核类型标记。 */
    fun write(
        dir: File,
        facing: String,
        cleaned: String,
        marker: String,
    ) {
        dir.mkdirs()
        atomicWrite(dir.resolve(CONFIG_FILE), facing)
        atomicWrite(dir.resolve(PRISTINE_FILE), cleaned)
        atomicWrite(dir.resolve(MARKER_FILE), marker)
    }

    fun atomicWrite(
        file: File,
        text: String,
    ) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray())
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                throw java.io.IOException("rename failed: ${file.name}")
            }
        } finally {
            tmp.delete()
        }
    }
}
