// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5PageBody
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.sanitizeLog

/**
 * 关于页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SlteCard`/`SlteRow`/`SlteRowCard` 版本。入口与行为逐项对齐
 * （详见本轮交付报告的「关于页入口对账清单」）：返回、应用标识卡、应用版本、内核版本、
 * 检查更新（含检查中转圈）、导出日志（导出 + 系统分享 + 三种提示）、更新结果提示气泡。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(key = "update"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kernelVersion by viewModel.kernelVersion.collectAsStateWithLifecycle()
    val siteInfo by viewModel.siteInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val c = V5ThemeColors.current

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.about_title),
            onBack = onBack,
        )

        V5PageBody {
            AboutIdentityCard(
                appName = siteInfo?.appName?.ifBlank { null } ?: stringResource(R.string.app_name),
                description =
                siteInfo?.appDescription?.ifBlank { null }
                    ?: stringResource(R.string.about_app_desc),
            )

            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.about_app_version),
                    icon = SlteIcons.About,
                    value = BuildConfig.VERSION_NAME,
                    valueMono = true,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.about_kernel_version),
                    icon = SlteIcons.Settings,
                    value = kernelVersion ?: Constants.PLACEHOLDER_DASH,
                    valueMono = true,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                CheckUpdateRow(
                    checking = state is UpdateUiState.Checking,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.checkUpdate(manual = true)
                    },
                )
            }

            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.about_log_export),
                    icon = SlteIcons.ExportLog,
                    chevron = true,
                    onClick = { exportLogs(context) },
                )
            }
        }
    }

    LaunchedEffect(state) {
        val res =
            when (state) {
                is UpdateUiState.Latest -> R.string.about_latest
                is UpdateUiState.Error -> R.string.about_update_failed
                else -> null
            }
        if (res != null) {
            Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
            viewModel.consumeTip()
        }
    }
}

/**
 * 「检查更新」行。
 *
 * 检查中仍然可点（与 v4 一致：重复点击由 VM 内部幂等处理），只是把尾部箭头换成 v5 转圈，
 * 这样状态切换不会改变行高、整张卡不跳动。
 *
 * 转圈必须套一个固定 18dp 的 [Box] 且 `fillMaxSize()`：Material3 的默认尺寸策略在小尺寸下
 * 会退化成 3dp 的小圆点（Robolectric 静态帧实测：直接给 18dp 只会渲染出一个 5×5 像素的蓝点，
 * 看不出来是加载中）。给足容器、让指示器吃满容器后，静态帧至少能画出可辨识的一段弧。
 */
@Composable
private fun CheckUpdateRow(
    checking: Boolean,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    V5RowItem(
        title = stringResource(R.string.about_check_update),
        icon = SlteIcons.Refresh,
        highlight = true,
        chevron = !checking,
        onClick = onClick,
        trailing = {
            if (checking) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = c.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    )
}

/**
 * 导出日志：导出成功弹提示并把文件交给系统分享面板。
 *
 * 行为与 v4 逐行一致（三条提示文案、FileProvider authority、`FLAG_GRANT_READ_URI_PERMISSION`）；
 * 只是把 catch 里的静默吞掉补上一条日志，便于用户反馈时定位（原先这里没有任何记录）。
 */
private fun exportLogs(context: Context) {
    val file = AppLog.export(context)
    if (file == null) {
        Toast.makeText(context, context.getString(R.string.about_log_export_failed), Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(context, context.getString(R.string.about_log_exported), Toast.LENGTH_SHORT).show()
    try {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_log_export))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.about_log_export_share)),
        )
    } catch (e: Exception) {
        AppLog.w("Polaris-About", "导出日志分享失败: ${sanitizeLog(e.message ?: "Unknown")}")
        Toast.makeText(context, context.getString(R.string.about_log_share_failed), Toast.LENGTH_SHORT).show()
    }
}
