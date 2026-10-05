// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.update.ReleaseInfo
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Sheet
import com.slte.app.utils.Dimens

/** 发现新版本弹窗：版本号、更新日志、立即更新。 */
@Composable
internal fun UpdateSheet(
    info: ReleaseInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val c = V5ThemeColors.current
    V5Sheet(
        title = stringResource(R.string.update_title),
        subtitle = stringResource(R.string.update_version_line, info.version, BuildConfig.VERSION_NAME),
        onDismiss = onDismiss,
    ) {
        if (info.changelog.isNotBlank()) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = markdownToPlainText(info.changelog),
                    style = SlteType.bodySmall,
                    color = c.text2,
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gap.md))
        }
        V5Button(
            text = stringResource(R.string.update_now),
            style = ButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            onClick = onUpdate,
        )
    }
}
