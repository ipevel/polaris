package com.slte.app.utils

import android.content.Context
import com.slte.app.support.RobolectricTestApplication
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class LocaleContextWrapperTest {
    private val base: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `存储语言优先于系统语言`() {
        val wrapper = LocaleContextWrapper(base) { Locale.ENGLISH }

        assertEquals("en", wrapper.resources.configuration.locales[0].language)
    }

    @Test
    fun `未存储语言时按系统简体中文`() {
        val wrapper = LocaleContextWrapper(base) { null }

        val locale = wrapper.resources.configuration.locales[0]
        assertEquals("zh", locale.language)
        assertEquals("CN", locale.country)
    }

    @Test
    fun `存储繁体时按繁体解析资源`() {
        val wrapper = LocaleContextWrapper(base) { Locale.TRADITIONAL_CHINESE }

        val locale = wrapper.resources.configuration.locales[0]
        assertEquals("zh", locale.language)
        assertTrue("繁体存储值应解析为繁体资源", isTraditionalChinese(locale))
    }

    @Test
    fun `不同包装实例按各自存储值解析资源`() {
        val english = LocaleContextWrapper(base) { Locale.ENGLISH }
        val chinese = LocaleContextWrapper(base) { Locale.SIMPLIFIED_CHINESE }

        assertEquals("en", english.resources.configuration.locales[0].language)
        assertEquals("zh", chinese.resources.configuration.locales[0].language)
    }

    @Test
    fun `语言未变化时复用缓存的资源`() {
        val wrapper = LocaleContextWrapper(base) { Locale.ENGLISH }

        assertSame(wrapper.resources, wrapper.resources)
    }

    @Test
    fun `存储语言变化后按新语言重新解析资源`() {
        var stored: Locale? = Locale.ENGLISH
        val wrapper = LocaleContextWrapper(base) { stored }

        assertEquals("en", wrapper.resources.configuration.locales[0].language)

        stored = Locale.SIMPLIFIED_CHINESE

        assertEquals("zh", wrapper.resources.configuration.locales[0].language)
    }

    @Test
    fun `非Activity上下文找不到Activity`() {
        assertNull(base.findActivity())
    }
}
