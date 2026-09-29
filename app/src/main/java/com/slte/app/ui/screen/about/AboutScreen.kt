// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.slte.app.utils.Constants
import com.slte.app.utils.LogExport

/**
 * 关于页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SlteCard`/`SlteRow`/`SlteRowCard` 版本。入口与行为逐项对齐
 * （详见本轮交付报告的「关于页入口对账清单」）：返回、应用标识卡、应用版本、内核版本、
 * 导出日志（导出 + 系统分享 + 三种提示）。
 *
 * 「检查更新」入口已随应用内更新功能一并移除：本页不再有获取新版本或提示升级的行为。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(key = "update"),
) {
    val kernelVersion by viewModel.kernelVersion.collectAsStateWithLifecycle()
    val siteInfo by viewModel.siteInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
            }

            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.about_log_export),
                    icon = SlteIcons.ExportLog,
                    chevron = true,
                    onClick = { LogExport.exportAndShare(context, viewModel.diagnosticsExtra()) },
                )
            }
        }
    }
}

// 导出逻辑已抽到 utils/LogExport：关于页与流量页失败卡片共用同一套「导出 + 系统分享」流程，
// 避免两处实现各自漂移（提示文案 / FileProvider authority / 权限位必须一致）。
