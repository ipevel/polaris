package com.slte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.slte.app.ui.navigation.SlteApp
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.data.local.ThemePreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreference: ThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components { add(SvgDecoder.Factory()) }
                    .build()
            }
            val dark by themePreference.dark.collectAsStateWithLifecycle()
            SlteTheme(darkTheme = dark) {
                SlteApp()
            }
        }
    }
}
