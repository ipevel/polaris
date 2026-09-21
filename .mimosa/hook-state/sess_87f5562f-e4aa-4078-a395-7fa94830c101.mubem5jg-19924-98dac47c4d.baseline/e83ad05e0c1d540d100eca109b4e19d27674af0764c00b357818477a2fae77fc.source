package com.slte.app.data.local

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import com.slte.app.utils.LocaleContextWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class LocaleStore
@Inject
constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private val _locale = MutableStateFlow(readTag(context)?.let { Locale.forLanguageTag(it) })

    val locale: StateFlow<Locale?> = _locale.asStateFlow()

    fun setLocale(locale: Locale?) {
        prefs.edit { putString(KEY_LOCALE, locale?.toLanguageTag()) }
        _locale.value = locale
    }

    companion object {
        private const val PREFS_NAME = "slte_locale"
        private const val KEY_LOCALE = "locale"

        internal fun readTag(context: Context): String? = context
            .getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LOCALE, null)
            ?.takeIf { it.isNotEmpty() }

        internal fun wrapBase(base: Context): Context = LocaleContextWrapper(base) {
            readTag(base)?.let { tag -> Locale.forLanguageTag(tag) }
        }
    }
}
