package com.slte.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscribeHostTest {
    @Test
    fun `主机解析忽略大小写与首尾空白`() {
        assertEquals("sub.example.com", subscribeHostOf("  HTTPS://Sub.Example.COM/api/v1/client/subscribe?token=x  "))
    }

    @Test
    fun `无法解析或空值返回空`() {
        assertNull(subscribeHostOf(null))
        assertNull(subscribeHostOf(""))
        assertNull(subscribeHostOf("   "))
        assertNull(subscribeHostOf("not-a-url"))
    }

    @Test
    fun `只取主机不泄漏路径与令牌`() {
        assertEquals("api.example.com", subscribeHostOf("https://api.example.com/sub?token=secret"))
    }
}
