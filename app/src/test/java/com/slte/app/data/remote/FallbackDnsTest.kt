// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import com.slte.app.data.local.DnsFailureStore
import com.slte.app.data.local.InMemoryPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FallbackDns] 的纯逻辑回归测试。
 *
 * 回归背景（2026-09-26 真机实证）：面板域名的**全部 IPv4 被网络黑洞、只有 IPv6 通**，
 * 而 OkHttp 4.x 没有 happy-eyeballs，只能按 DNS 返回顺序串行尝试。两处能力缺口共同造成
 * "不连 VPN 时登录/流量/个人页全部超时"：
 * 1. 备用 DNS 路径**只解析 A 记录**（IPv4），一旦走到降级路径就注定拿不到可用的 IPv6；
 * 2. 没有"这个地址连不上"的记忆，坏地址每次都会被排在前面重试。
 */
class FallbackDnsTest {

    /** 共享同一份偏好的存储：用于验证"冷启动后仍记得哪一族连不上"。 */
    private val prefs = InMemoryPreferences()

    private fun newDns() = FallbackDns(DnsFailureStore(prefs))

    // region 备用 DNS 响应解析

    @Test
    fun `备用 DNS 路径必须能解析 AAAA 记录`() {
        val hostname = "panel.example.com"
        val id: Short = 0x1234
        val ipv6 = byteArrayOf(
            0x26, 0x06, 0x47, 0x00, 0x30, 0x33, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x68, 0x15, 0x09.toByte(), 0xEE.toByte(),
        )
        val packet = dnsResponse(hostname, id, listOf(TYPE_AAAA to ipv6))

        val result = newDns().parseResponse(packet, packet.size, id, hostname)

        assertEquals("AAAA 应解析出 1 个地址", 1, result.size)
        assertTrue("必须是 IPv6 地址，否则降级路径仍然只有 IPv4", result.single() is Inet6Address)
        assertArrayEquals(ipv6, result.single().address)
    }

    @Test
    fun `A 记录解析保持原行为`() {
        val hostname = "panel.example.com"
        val id: Short = 0x4321
        val ipv4 = byteArrayOf(172.toByte(), 67, 161.toByte(), 180.toByte())
        val packet = dnsResponse(hostname, id, listOf(TYPE_A to ipv4))

        val result = newDns().parseResponse(packet, packet.size, id, hostname)

        assertEquals(1, result.size)
        assertTrue(result.single() is Inet4Address)
        assertArrayEquals(ipv4, result.single().address)
    }

    @Test
    fun `同一应答里的 A 与 AAAA 都要收下`() {
        val hostname = "panel.example.com"
        val id: Short = 0x0F0F
        val ipv4 = byteArrayOf(104, 21, 9, 238.toByte())
        val ipv6 = ByteArray(16) { if (it == 15) 1 else 0 }
        val packet = dnsResponse(hostname, id, listOf(TYPE_A to ipv4, TYPE_AAAA to ipv6))

        val result = newDns().parseResponse(packet, packet.size, id, hostname)

        assertEquals(2, result.size)
        assertTrue(result.any { it is Inet4Address })
        assertTrue(result.any { it is Inet6Address })
    }

    // endregion

    // region 坏地址降级

    @Test
    fun `连接失败的地址会被排到队尾而不是被丢弃`() {
        val dns = newDns()
        val bad = InetAddress.getByName("172.67.161.180")
        val good = InetAddress.getByName("2606:4700:3033::6815:9ee")
        // 先过一次解析：生产路径下 order() 会记住"这个地址来自哪个主机"
        dns.orderForTest(HOST, listOf(bad, good))

        dns.markConnectFailed(bad)

        assertEquals("坏地址应让位给可用地址", listOf(good, bad), dns.orderForTest(HOST, listOf(bad, good)))
    }

    @Test
    fun `未标记时保持 DNS 返回的原始顺序`() {
        val dns = newDns()
        val first = InetAddress.getByName("172.67.161.180")
        val second = InetAddress.getByName("2606:4700:3033::6815:9ee")

        assertEquals(listOf(first, second), dns.orderForTest(HOST, listOf(first, second)))
    }

    @Test
    fun `解析结果全部被标记过时不改变顺序，保证永远还有一次机会`() {
        val dns = newDns()
        val first = InetAddress.getByName("172.67.161.180")
        val second = InetAddress.getByName("104.21.9.238")
        dns.orderForTest(HOST, listOf(first, second))
        dns.markConnectFailed(first)
        dns.markConnectFailed(second)

        assertEquals(
            "全部坏过时不能把地址滤空或乱序，否则请求会直接失败在 DNS 层",
            listOf(first, second),
            dns.orderForTest(HOST, listOf(first, second)),
        )
    }

    /**
     * 关键回归：**没记录过的新 IPv4** 也必须让位给 IPv6。
     *
     * CDN 的 A 记录会轮换：退出登录会清 DNS 缓存，重新解析往往拿到全新的 IPv4 地址，
     * 逐地址记忆对它们一无所知。真机实证（2026-09-26）：5 个新 IPv4 × 5 秒连接超时，
     * 把一次重新登录拖到 25 秒。所以记忆必须落在"主机+地址族"上。
     */
    @Test
    fun `整族失败后新解析出的未记录 IPv4 也要让位给 IPv6`() {
        val dns = newDns()
        val knownBadV4 = InetAddress.getByName("172.67.161.180")
        val goodV6 = InetAddress.getByName("2606:4700:3033::6815:9ee")
        dns.orderForTest(HOST, listOf(knownBadV4, goodV6))
        dns.markConnectFailed(knownBadV4)

        val brandNewV4 = InetAddress.getByName("162.159.140.229")
        assertEquals(
            "新 IPv4 从未失败过，但同族失败过——必须排在可用的 IPv6 后面",
            listOf(goodV6, brandNewV4),
            dns.orderForTest(HOST, listOf(brandNewV4, goodV6)),
        )
    }

    @Test
    fun `另一族失败过时同样让位（方向对称）`() {
        val dns = newDns()
        val badV6 = InetAddress.getByName("2606:4700:3033::6815:9ee")
        val goodV4 = InetAddress.getByName("104.21.9.238")
        dns.orderForTest(HOST, listOf(badV6, goodV4))
        dns.markConnectFailed(badV6)

        assertEquals(
            "IPv6 整族失败时应让位给 IPv4",
            listOf(goodV4, badV6),
            dns.orderForTest(HOST, listOf(badV6, goodV4)),
        )
    }

    /**
     * 共享同一个 CDN IP 的多个面板主机都要吃到这次失败。
     *
     * 真机上观测到 API 主机与站点主机解析到**同一批 Cloudflare IP**；
     * 若用"地址 → 单个主机"的映射，后一次解析会把前一次覆盖掉，
     * 结果"整族失败"记到了不相干的主机上，降级完全不生效。
     *
     * 测试里用中立域名，不写真实面板主机（仓库不固化用户私有部署信息）。
     */
    @Test
    fun `共享同一 CDN IP 的多个主机都会被记为整族失败`() {
        val dns = newDns()
        val sharedV4 = InetAddress.getByName("172.67.161.180")
        val v6 = InetAddress.getByName("2606:4700:3033::6815:9ee")
        dns.orderForTest("api.example.com", listOf(sharedV4, v6))
        dns.orderForTest("site.example.com", listOf(sharedV4, v6))

        dns.markConnectFailed(sharedV4)

        assertEquals(
            "API 主机必须吃到这次失败",
            listOf(v6, sharedV4),
            dns.orderForTest("api.example.com", listOf(sharedV4, v6)),
        )
        assertEquals(
            "站点主机同理",
            listOf(v6, sharedV4),
            dns.orderForTest("site.example.com", listOf(sharedV4, v6)),
        )
    }

    // endregion

    /**
     * 冷启动场景：新进程里的 [FallbackDns] 必须仍记得"这个主机的 IPv4 连不上"。
     *
     * 真机动机（2026-09-26）：进程内记忆只能让**本次运行**变快；冷启动记忆为空要重新学一遍
     * （2 个坏 IPv4 × 5 秒连接超时 ≈ 11 秒），用户感受就是"每次打开 App 第一个操作都特别慢"。
     */
    @Test
    fun `坏地址族记忆跨进程生效`() {
        val first = newDns()
        val badV4 = InetAddress.getByName("172.67.161.180")
        val goodV6 = InetAddress.getByName("2606:4700:3033::6815:9ee")
        first.orderForTest(HOST, listOf(badV4, goodV6))
        first.markConnectFailed(badV4)

        // 模拟进程重启：同一份偏好，全新的 FallbackDns 实例
        val restarted = newDns()

        assertEquals(
            "重启后第一个请求就该走 IPv6，而不是重新交一遍 5 秒学费",
            listOf(goodV6, badV4),
            restarted.orderForTest(HOST, listOf(badV4, goodV6)),
        )
    }

    /**
     * 跨进程记忆必须能自愈，但**只清本次运行**那一份：
     * 走隧道的成功连接不代表直连路径的 IPv4 可用，落盘那份清了会让下次断开又慢一遍。
     * 这正是真机上那次 45 秒登录超时的成因（连过一次 VPN 就清掉了记忆）。
     */
    @Test
    fun `连接成功只清除本次运行的记忆，落盘记忆保留`() {
        val first = newDns()
        val v4 = InetAddress.getByName("172.67.161.180")
        val v6 = InetAddress.getByName("2606:4700:3033::6815:9ee")
        first.orderForTest(HOST, listOf(v4, v6))
        first.markConnectFailed(v4)
        // IPv4 又通了（可能只是走隧道通了）
        first.orderForTest(HOST, listOf(v4, v6))
        first.markConnectSucceeded(v4)

        assertEquals(
            "本次运行内不该还把 IPv4 压在后面",
            listOf(v4, v6),
            first.orderForTest(HOST, listOf(v4, v6)),
        )
        assertEquals(
            "落盘那份要留给下次冷启动（断开直连时还得靠它避免重新学 11 秒）",
            listOf(v6, v4),
            newDns().orderForTest(HOST, listOf(v4, v6)),
        )
    }

    private companion object {
        const val HOST = "panel.example.com"

        const val TYPE_A = 1

        const val TYPE_AAAA = 28

        /**
         * 造一个最小的 DNS 应答包（header + 单条 question + 若干 answer），
         * 供 [FallbackDns.parseResponse] 直接解析，避免测试依赖真实网络。
         */
        fun dnsResponse(
            hostname: String,
            id: Short,
            answers: List<Pair<Int, ByteArray>>,
        ): ByteArray {
            val out = ArrayList<Byte>(64)
            fun u8(v: Int) = out.add(v.toByte())
            fun u16(v: Int) {
                out.add((v shr 8).toByte())
                out.add(v.toByte())
            }
            fun u32(v: Long) {
                out.add((v shr 24).toByte())
                out.add((v shr 16).toByte())
                out.add((v shr 8).toByte())
                out.add(v.toByte())
            }

            u16(id.toInt() and 0xFFFF)
            u16(0x8180) // QR=1（应答）、RD=1、RA=1、rcode=0
            u16(1) // qdcount
            u16(answers.size) // ancount
            u16(0) // nscount
            u16(0) // arcount

            hostname.split(".").forEach { label ->
                u8(label.length)
                label.forEach { u8(it.code) }
            }
            u8(0) // qname 结束
            u16(TYPE_A)
            u16(1) // IN

            answers.forEach { (type, rdata) ->
                u16(0xC00C) // 名字指针 → question 的 qname
                u16(type)
                u16(1) // IN
                u32(60) // TTL
                u16(rdata.size)
                rdata.forEach { u8(it.toInt() and 0xFF) }
            }
            return out.toByteArray()
        }
    }
}
