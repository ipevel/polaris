package com.slte.app.data.remote

import com.slte.app.utils.AppLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dns

@Singleton
class FallbackDns
@Inject
constructor() : Dns {

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    private val fallbackServers =
        listOf(
            "114.114.114.114",
            "223.5.5.5",
            "8.8.8.8",
            "1.1.1.1",
        )

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL_MS) return entry.ips
            cache.remove(hostname)
        }

        try {
            val result = Dns.SYSTEM.lookup(hostname)
            cache[hostname] = CachedEntry(result, now)
            return result
        } catch (_: UnknownHostException) {
            AppLog.w("Polaris-Dns", "FallbackDns: 系统 DNS 解析失败，尝试备用 DNS")
        }

        val deadline = now + FALLBACK_TIMEOUT_MS
        for (dnsStr in fallbackServers) {
            if (System.currentTimeMillis() > deadline) break
            try {
                val dnsServer = InetAddress.getByName(dnsStr)
                val result = queryDns(hostname, dnsServer, deadline - System.currentTimeMillis())
                if (result.isNotEmpty()) {
                    cache[hostname] = CachedEntry(result, System.currentTimeMillis())
                    return result
                }
            } catch (e: Exception) {
                AppLog.w("Polaris-Dns", "FallbackDns: 备用 DNS $dnsStr 失败")
            }
        }

        throw UnknownHostException("FallbackDns: 所有 DNS 均无法解析")
    }

    fun clearCache() {
        cache.clear()
    }

    private data class CachedEntry(
        val ips: List<InetAddress>,
        val timestamp: Long,
    )

    private companion object {
        const val CACHE_TTL_MS = 5 * 60_000L

        const val FALLBACK_TIMEOUT_MS = 8_000L

        const val QUERY_TIMEOUT_MS = 5_000L

        const val MAX_POINTER_JUMPS = 16

        const val MAX_NAME_LENGTH = 253
    }

    private fun queryDns(
        hostname: String,
        dnsServer: InetAddress,
        remainingMs: Long,
    ): List<InetAddress> {
        val socket = DatagramSocket()
        socket.soTimeout = minOf(QUERY_TIMEOUT_MS, remainingMs.coerceAtLeast(1)).toInt()

        try {
            val id = (SecureRandom().nextInt(65536) and 0xFFFF).toShort()
            val req = buildQueryPacket(id, hostname)
            socket.send(DatagramPacket(req, req.size, dnsServer, 53))

            val resp = ByteArray(512)
            val pkt = DatagramPacket(resp, resp.size)
            socket.receive(pkt)

            if (pkt.address != dnsServer) {
                throw UnknownHostException("DNS response source mismatch")
            }

            return parseResponse(resp, pkt.length, id, hostname)
        } finally {
            socket.close()
        }
    }

    private fun buildQueryPacket(
        id: Short,
        hostname: String,
    ): ByteArray {
        val buf = ByteArray(512)
        var pos = 0

        buf[pos++] = (id.toInt() shr 8).toByte()
        buf[pos++] = id.toByte()
        buf[pos++] = 1
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 1
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 0

        for (label in hostname.split(".")) {
            buf[pos++] = label.length.toByte()
            for (c in label.encodeToByteArray()) {
                buf[pos++] = c
            }
        }
        buf[pos++] = 0
        buf[pos++] = 0
        buf[pos++] = 1
        buf[pos++] = 0
        buf[pos++] = 1

        return buf.copyOf(pos)
    }

    private fun parseResponse(
        resp: ByteArray,
        len: Int,
        expectedId: Short,
        hostname: String,
    ): List<InetAddress> {
        val data = resp.copyOfRange(0, len)
        val respId = ((resp[0].toInt() and 0xFF) shl 8) or (resp[1].toInt() and 0xFF)
        if (respId != (expectedId.toInt() and 0xFFFF)) {
            throw UnknownHostException("DNS response ID mismatch")
        }
        if (resp[2].toInt() and 0x80 == 0) {
            throw UnknownHostException("DNS response is not a reply")
        }

        val rcode = resp[3].toInt() and 0x0F
        if (rcode != 0) {
            throw UnknownHostException("DNS response code: $rcode")
        }

        val qdcount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val ancount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)

        var pos = 12

        if (qdcount > 0) {
            val (qname, qend) = decodeName(data, pos)
            pos = qend
            pos += 4

            if (!qname.equals(hostname, ignoreCase = true)) {
                throw UnknownHostException("DNS question mismatch")
            }
        }

        val result = mutableListOf<InetAddress>()
        for (i in 0 until ancount) {
            pos = skipName(data, pos)
            if (pos + 10 > data.size) throw UnknownHostException("DNS answer truncated")
            val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 8
            val rdlength = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            if (pos + rdlength > data.size) throw UnknownHostException("DNS answer truncated")

            if (type == 1 && rdlength == 4) {
                val addr = data.copyOfRange(pos, pos + 4)
                result.add(InetAddress.getByAddress(hostname, addr))
            }
            pos += rdlength
        }

        if (result.isEmpty()) {
            throw UnknownHostException("No A records found for $hostname")
        }
        return result
    }

    private fun decodeName(
        buf: ByteArray,
        start: Int,
    ): Pair<String, Int> {
        var pos = start
        var jumped = false
        var end = start
        val labels = mutableListOf<String>()
        var jumps = 0
        var totalLen = 0
        while (true) {
            if (pos >= buf.size) throw UnknownHostException("DNS name overflow")
            val len = buf[pos].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) end = pos + 1
                break
            }
            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= buf.size) throw UnknownHostException("DNS pointer overflow")
                val ptr = ((len and 0x3F) shl 8) or (buf[pos + 1].toInt() and 0xFF)
                if (!jumped) end = pos + 2
                jumped = true

                if (++jumps > MAX_POINTER_JUMPS) throw UnknownHostException("DNS pointer loop")
                pos = ptr
            } else {
                if (pos + 1 + len > buf.size) throw UnknownHostException("DNS name overflow")
                totalLen += len
                if (totalLen > MAX_NAME_LENGTH) throw UnknownHostException("DNS name too long")
                labels.add(String(buf, pos + 1, len, Charsets.US_ASCII))
                pos += len + 1
            }
        }
        return labels.joinToString(".") to end
    }

    private fun skipName(
        buf: ByteArray,
        start: Int,
    ): Int {
        var pos = start
        while (pos < buf.size) {
            val len = buf[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if ((len and 0xC0) == 0xC0) return pos + 2
            pos += len + 1
        }
        return pos
    }
}
