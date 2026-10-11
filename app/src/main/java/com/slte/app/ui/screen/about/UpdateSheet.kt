// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.update.AppUpdateDownloader
import com.slte.app.data.update.ReleaseInfo
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Sheet
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/**
 * 发现新版本弹窗。
 *
 * 两种形态共用同一个面板，点「立即更新」后原地切换，不关窗再开窗：
 * - 未下载：[progress] 为 `null`，展示版本号、更新日志、「立即更新」
 * - 下载中：[progress] 非空，展示进度条、已下载/总大小、「取消」
 *
 * 为什么下载中不允许关闭（`dismissible = false`）：下载活在应用单例里，关掉弹窗并不会
 * 中断它，只会留下一个看不见的下载——用户既看不到进度，也找不到取消入口。要离开就先取消。
 */
@Composable
internal fun UpdateSheet(
    info: ReleaseInfo,
    progress: AppUpdateDownloader.Progress?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = V5ThemeColors.current
    V5Sheet(
        title = stringResource(R.string.update_title),
        subtitle =
        if (progress == null) {
            stringResource(R.string.update_version_line, info.version, BuildConfig.VERSION_NAME)
        } else {
            stringResource(R.string.update_downloading)
        },
        onDismiss = onDismiss,
        dismissible = progress == null,
    ) {
        if (progress == null) {
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
        } else {
            DownloadProgress(progress = progress, onCancel = onCancel)
        }
    }
}

/** 下载中形态：进度条 + 字节数 + 取消。 */
@Composable
private fun DownloadProgress(
    progress: AppUpdateDownloader.Progress,
    onCancel: () -> Unit,
) {
    val c = V5ThemeColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        DownloadProgressBar(fraction = progress.fraction)
        Spacer(modifier = Modifier.height(Dimens.gap.sm))
        Text(
            text =
            if (progress.totalBytes > 0) {
                stringResource(
                    R.string.update_download_progress,
                    FormatUtils.traffic(progress.bytesRead),
                    FormatUtils.traffic(progress.totalBytes),
                )
            } else {
                // 服务端没给 Content-Length（GitHub Releases 会给，属于兜底）：
                // 进度条走不确定态，但字节数照报，用户仍能看出在动。
                stringResource(R.string.update_download_progress_unknown, FormatUtils.traffic(progress.bytesRead))
            },
            style = SlteType.bodySmall,
            color = c.text3,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.lg))
        V5Button(
            text = stringResource(R.string.update_download_cancel),
            style = ButtonStyle.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel,
        )
    }
}

/**
 * 自绘进度条，而不是 Material3 的 `LinearProgressIndicator`。
 *
 * 本仓 UI 是 V5 自绘体系（圆角胶囊、`V5ThemeColors`），M3 进度条自带的停止圆点、
 * 高度与圆角跟这套语言对不上，改它的参数比直接画两个 Box 还麻烦。
 */
@Composable
private fun DownloadProgressBar(fraction: Float?) {
    val c = V5ThemeColors.current
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(c.surface3),
    ) {
        Box(
            modifier =
            Modifier
                // 总长未知时画一段固定宽度，避免看起来"卡在 0%"。
                .fillMaxWidth(fraction?.coerceIn(0f, 1f) ?: UNKNOWN_TOTAL_BAR_FRACTION)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(c.accent),
        )
    }
}

/** 总长未知时进度条的占位宽度（30%）。 */
private const val UNKNOWN_TOTAL_BAR_FRACTION = 0.3f
