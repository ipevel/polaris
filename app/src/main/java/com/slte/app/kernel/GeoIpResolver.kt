package com.slte.app.kernel

import android.content.Context
import com.maxmind.db.Reader
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoIpResolver
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var reader: Reader? = null

    fun countryCode(ip: String): String? {
        val r =
            reader ?: synchronized(this) {
                reader ?: load().also { reader = it }
            } ?: return null
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return null

        val scalar = runCatching { r.get(address, String::class.java) }.getOrNull()
        val code =
            if (!scalar.isNullOrBlank()) {
                scalar
            } else {
                val list = runCatching { r.get(address, List::class.java) }.getOrNull()
                (list as? List<*>)?.firstOrNull() as? String
            }

        return code?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' } }?.lowercase()
    }

    private fun load(): Reader? = runCatching {
        Reader(File(context.filesDir, "clash/geoip.metadb"))
    }.getOrElse {
        AppLog.w("SLTE-Kernel", "GeoIpResolver: metadb 加载失败: ${sanitizeLog(it.message ?: "Unknown")}")
        null
    }
}
