// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.local.ThemeMode
import com.slte.app.data.local.ThemePreference
import com.slte.app.data.update.UpdateAvailability
import com.slte.app.ui.navigation.SlteApp
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.LocalHasAppUpdate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themePreference: ThemePreference

    @Inject
    lateinit var updateAvailability: UpdateAvailability

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleStore.wrapBase(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 底部导航为自绘：关闭系统给三键导航栏加的半透明底衬，避免与自绘底栏叠色（API 29+）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // 每次启动查一次新版本，结果供三处提示圆点（我的 Tab / 关于软件 / 检查更新）共用。
        // 单例内部保证每进程只发一次请求，失败静默；不阻塞启动，也不参与任何界面状态机。
        lifecycleScope.launch { updateAvailability.refreshOnLaunch() }
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader
                    .Builder(context)
                    .components { add(SvgDecoder.Factory()) }
                    .build()
            }
            val themeMode by themePreference.mode.collectAsStateWithLifecycle()
            val availableUpdate by updateAvailability.available.collectAsStateWithLifecycle()
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SlteTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalHasAppUpdate provides (availableUpdate != null)) {
                    SlteApp()
                }
            }
        }
    }
}
