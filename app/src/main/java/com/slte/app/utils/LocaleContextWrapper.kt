// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

class LocaleContextWrapper(
    base: Context,
    private val storedLocaleProvider: () -> Locale?,
) : ContextWrapper(base) {
    private var effectiveLocale: Locale? = null
    private var cachedResources: Resources? = null

    override fun getResources(): Resources {
        val stored = storedLocaleProvider()
        val systemLocale = baseContext.resources.configuration.locales[0] ?: Locale.getDefault()
        val effective = resolveEffectiveLocale(systemLocale, stored)
        if (effective != effectiveLocale || cachedResources == null) {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(effective.toLanguageTag()))
            cachedResources = createConfigurationContext(config).resources
            effectiveLocale = effective
        }
        return cachedResources ?: super.getResources()
    }
}

internal fun resolveEffectiveLocale(
    systemLocale: Locale,
    storedLocale: Locale?,
): Locale = when {
    storedLocale != null -> storedLocale
    systemLocale.language == "zh" && isTraditionalChinese(systemLocale) -> Locale.TRADITIONAL_CHINESE
    systemLocale.language == "zh" -> Locale.SIMPLIFIED_CHINESE
    systemLocale.language == "en" -> Locale.ENGLISH
    else -> Locale.ENGLISH
}

internal fun isTraditionalChinese(locale: Locale): Boolean = locale.language == "zh" &&
    (
        locale.script == "Hant" ||
            locale.country == "TW" ||
            locale.country == "HK" ||
            locale.country == "MO"
        )

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
