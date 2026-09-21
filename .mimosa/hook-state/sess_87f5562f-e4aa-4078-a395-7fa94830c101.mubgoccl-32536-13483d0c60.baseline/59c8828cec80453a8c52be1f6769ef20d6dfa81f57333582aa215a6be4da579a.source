package com.slte.app.data.local

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Singleton
class ThemePreference
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Backward-compatible dark flow: true when effective theme is dark. */
    val dark: StateFlow<Boolean> get() = _dark
    private val _dark = MutableStateFlow(computeDark(_mode.value))

    fun setMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
        _dark.value = computeDark(mode)
    }

    /** Legacy API used by existing UI — maps to setMode. */
    fun setDark(enabled: Boolean) = setMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)

    fun toggleDarkMode() {
        setMode(
            when (_mode.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            },
        )
    }

    private fun readMode(): ThemeMode {
        // Migrate legacy boolean key
        if (prefs.contains(KEY_DARK)) {
            val wasDark = prefs.getBoolean(KEY_DARK, false)
            prefs.edit {
                putString(KEY_MODE, if (wasDark) ThemeMode.DARK.name else ThemeMode.LIGHT.name)
                remove(KEY_DARK)
            }
            return if (wasDark) ThemeMode.DARK else ThemeMode.LIGHT
        }
        return try {
            ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    private fun computeDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private fun isSystemDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private companion object {
        const val PREFS_NAME = "slte_theme"
        const val KEY_MODE = "theme_mode"
        const val KEY_DARK = "dark_mode"
    }
}
