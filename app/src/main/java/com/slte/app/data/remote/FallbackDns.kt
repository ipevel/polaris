// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.utils.AppLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
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
constructor(
    private val failureStore: com.slte.app.data.local.DnsFailureStore,
) : Dns {

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    /**
     * 近期连接失败的地址 → 降级失效时间戳（毫秒）。
     *
     * 只影响**同一进程内后续请求的排序**，不落盘、不影响 DNS 结果本身。
     */
    private val failedUntil = ConcurrentHashMap<String, Long>()

    /**
     * 主机+地址族 → 降级失效时间戳（key 形如 `panel.example.com|v4`）。
     *
     * 为什么需要"整族"记忆：CDN 的 A/AAAA 记录会变，重新解析可能拿到**从没记录过的新 IP**，
     * 逐地址记忆挡不住它们。2026-09-26 真机实证：退出登录会清 DNS 缓存（`clearCache`），
     * 重新解析回 5 个新 IPv4（全被黑洞），5 个地址 × 5 秒连接超时把登录拖到 25 秒。
     * "这个网络黑洞 IPv4"是整族特征，记在族上才能让新 IP 一上来就排在 IPv6 后面。
     */
    private val failedFamilies = ConcurrentHashMap<String, Long>()
        .apply {
            // 跨进程复用上次学到的"哪一族连不上"：否则冷启动第一个请求要重新交一遍学费
            // （2 个坏 IPv4 × 5 秒连接超时 ≈ 11 秒），用户感受就是"刚打开 App 特别慢"。
            putAll(failureStore.loadFamilies())
        }

    /**
     * 地址 → 解析出它的主机集合。
     *
     * 用集合而不是单值：CDN 的同一个 IP 会被多个面板主机共享（真机上就观测到
     * API 主机与站点主机解析到**同一批 Cloudflare IP**），
     * 单值映射会被后一次解析覆盖，导致"整族失败"记到不相干的主机上、降级逻辑失效。
     * 上限 [MAX_HOSTS_PER_ADDRESS] 只防异常膨胀。
     *
     * 注：本文件刻意不写具体面板主机名——那是用户的私有部署信息，不应固化进仓库。
     */
    private val hostsOfAddress = ConcurrentHashMap<String, MutableSet<String>>()

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
            if (now - entry.timestamp < CACHE_TTL_MS) return order(hostname, entry.ips)
            cache.remove(hostname)
        }

        try {
            val result = Dns.SYSTEM.lookup(hostname)
            cache[hostname] = CachedEntry(result, now)
            return order(hostname, result)
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
                    return order(hostname, result)
                }
            } catch (e: Exception) {
                AppLog.w("Polaris-Dns", "FallbackDns: 备用 DNS $dnsStr 失败")
            }
        }

        throw UnknownHostException("FallbackDns: 所有 DNS 均无法解析")
    }

    /**
     * 记录一个"连不上"的地址，后续 [lookup] 会把它排到队尾（由 OkHttp 的 `connectFailed` 事件驱动）。
     *
     * 为什么需要它：一个域名解析出多个地址时，OkHttp 只能按顺序串行尝试，而**网络会黑洞掉某一族地址**
     * （2026-09-26 真机实证：面板域名的全部 IPv4 被黑洞、IPv6 正常，而 DNS 先返回 IPv4）。
     * 没有记忆的话，每次请求都要先在坏地址上白等一个 connectTimeout，用户侧表现为
     * "不连接 VPN 时每个面板操作都转很久甚至失败"。记下来之后，同一进程内后续请求直接走可用地址；
     * [FAILED_TTL_MS] 到期自动失效，网络或地址恢复后不会被永久降级。
     */
    fun markConnectFailed(address: InetAddress) {
        val key = address.hostAddress ?: return
        val now = System.currentTimeMillis()
        val familySuffix = familySuffixOf(address)
        failedUntil[key] = now + FAILED_TTL_MS
        hostsOfAddress[key]?.forEach { host ->
            failedFamilies[failureStore.familyKey(host, familySuffix)] = now + FAILED_TTL_MS
            // 落盘：让下一次冷启动不必重新学一遍（否则第一个请求又要白等 11 秒）
            failureStore.markFailed(host, familySuffix, FAILED_TTL_MS, now)
        }
        AppLog.w(
            "Polaris-Dns",
            "FallbackDns: 地址 $key 连接失败，${FAILED_TTL_MS / 1000}s 内降级到队尾（含所属地址族）",
        )
    }

    /**
     * 连接成功 ⇒ 立刻作废该地址族在**本次运行**内的"连不上"记忆。
     *
     * 落盘那份刻意**不**跟着清：真机上用户连着 VPN 时的成功连接走的是隧道，
     * 直连路径的 IPv4 依旧是黑洞——一次隧道成功就把跨进程记忆清掉，会让下次断开后又
     * 重新交一遍学费（2026-09-26 真机实测：那次 45 秒登录超时就是这么来的）。
     * 落盘记忆只由 [com.slte.app.data.local.DnsFailureStore.TTL_MS] 兜底，10 分钟后自动失效。
     */
    fun markConnectSucceeded(address: InetAddress) {
        val key = address.hostAddress ?: return
        val familySuffix = familySuffixOf(address)
        // 该地址自己的降级也要撤掉，否则"成功连上过"的地址仍被压在队尾
        val clearedAddress = failedUntil.remove(key) != null
        var clearedFamily = false
        hostsOfAddress[key]?.forEach { host ->
            if (failedFamilies.remove(failureStore.familyKey(host, familySuffix)) != null) clearedFamily = true
        }
        if (clearedAddress || clearedFamily) {
            AppLog.d("Polaris-Dns", "FallbackDns: 该地址（族）已恢复，解除本次运行的排序降级")
        }
    }

    private fun familySuffixOf(address: InetAddress): String = if (address is Inet6Address) "v6" else "v4"

    /** 把近期连接失败的地址排到队尾（**不丢弃**），并让"整族失败"优先让位给另一族。 */
    private fun order(
        hostname: String,
        addresses: List<InetAddress>,
    ): List<InetAddress> {
        // 记住"这个地址由哪些主机解析而来"，markConnectFailed 才能把族失败归因到正确的主机
        addresses.forEach { addr ->
            val key = addr.hostAddress ?: return@forEach
            val hosts = hostsOfAddress.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
            if (hosts.size < MAX_HOSTS_PER_ADDRESS) hosts.add(hostname.lowercase())
        }
        if (failedUntil.isEmpty() && failedFamilies.isEmpty()) return addresses

        val now = System.currentTimeMillis()
        failedUntil.entries.removeAll { it.value <= now }
        failedFamilies.entries.removeAll { it.value <= now }

        val host = hostname.lowercase()
        val v4Failed = failedFamilies.containsKey(failureStore.familyKey(host, "v4"))
        val v6Failed = failedFamilies.containsKey(failureStore.familyKey(host, "v6"))
        // 只有一族出过问题、另一族干净时，直接把干净那族提到最前——新解析出的未知 IP 也因此不再白等
        if (v4Failed && !v6Failed) {
            val (v6, v4) = addresses.partition { it is Inet6Address }
            if (v6.isNotEmpty()) return v6 + v4
        }
        if (v6Failed && !v4Failed) {
            val (v4, v6) = addresses.partition { it is Inet4Address }
            if (v4.isNotEmpty()) return v4 + v6
        }

        // 两族都出过问题（或同族内只有部分地址失败）：按地址记忆把坏地址挪到队尾
        val (failed, healthy) = addresses.partition { failedUntil.containsKey(it.hostAddress) }
        if (healthy.isEmpty()) return addresses
        return healthy + failed
    }

    /** 仅供单测直接验证排序语义（生产路径只经 [lookup]）。 */
    internal fun orderForTest(
        hostname: String,
        addresses: List<InetAddress>,
    ): List<InetAddress> = order(hostname, addresses)

    fun clearCache() {
        cache.clear()
    }

    private data class CachedEntry(
        val ips: List<InetAddress>,
        val timestamp: Long,
    )

    private companion object {
        const val CACHE_TTL_MS = 5 * 60_000L

        /** 地址被判定"连不上"后的降级时长：到期自动解除，避免网络恢复后还被压队尾。 */
        const val FAILED_TTL_MS = com.slte.app.data.local.DnsFailureStore.TTL_MS

        /** 单个地址最多记住多少个来源主机（防异常膨胀；正常只有面板的几个主机）。 */
        const val MAX_HOSTS_PER_ADDRESS = 8

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

    internal fun parseResponse(
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
            } else if (type == 28 && rdlength == 16) {
                // AAAA 也必须解析：备用 DNS 路径若只认 A 记录，就保证"降级后只剩 IPv4"，
                // 而 2026-09-26 真机实证 IPv4 可能整族被网络黑洞、只有 IPv6 通——
                // 那样降级路径会把用户推进更糟的状态（详见 markConnectFailed 的注释）。
                val addr = data.copyOfRange(pos, pos + 16)
                result.add(InetAddress.getByAddress(hostname, addr))
            }
            pos += rdlength
        }

        if (result.isEmpty()) {
            throw UnknownHostException("No A/AAAA records found for $hostname")
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
