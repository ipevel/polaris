// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.screen.main.TrafficDonut
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    viewModel: TrafficViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()

    ToastTip(
        message = data.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    SlteScaffold(
        title = stringResource(R.string.traffic_title),
        showBack = false,
    ) { innerPadding ->
        if (data.isLoading && data.records.isEmpty()) {
            LoadingContent(modifier = Modifier.padding(innerPadding))
        } else {
            val errorRes = data.errorMessageRes
            SltePullRefresh(
                isRefreshing = data.isLoading && data.records.isNotEmpty(),
                onRefresh = viewModel::load,
                modifier = Modifier.padding(innerPadding),
            ) {
                when {
                    errorRes != null && data.records.isEmpty() ->
                        PullRefreshScrollable {
                            ErrorState(
                                message = stringResource(errorRes),
                                onRetry = viewModel::load,
                            )
                        }
                    else ->
                        TrafficContent(data = data)
                }
            }
        }
    }
}

@Composable
private fun TrafficContent(
    data: TrafficData,
) {
    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.dashboardScreenPaddingH),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        contentPadding =
        PaddingValues(
            vertical = Dimens.gap.lg,
        ),
    ) {
        item {
            Text(
                text = stringResource(R.string.traffic_records_title),
                style = SlteType.title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Dimens.gap.sm),
            )
        }

        if (data.records.size >= 2) {
            item {
                TrafficTrendChart(records = data.records)
            }
        }

        if (data.records.isEmpty()) {
            item {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.gap.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.traffic_records_empty),
                        style = SlteType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(data.records, key = { it.date }) { record ->
                TrafficRecordRow(record = record)
            }
        }
    }
}

@Composable
private fun TrafficRecordRow(record: TrafficLogRecord) {
    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：日期 + 上行/下行紧凑列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
            ) {
                Text(
                    text = record.date.ifBlank { stringResource(R.string.traffic_records_empty) },
                    style = SlteType.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DirectionPill(
                        isUp = true,
                        label = FormatUtils.traffic(record.uploadBytes),
                    )
                    Spacer(modifier = Modifier.width(Dimens.gap.md))
                    DirectionPill(
                        isUp = false,
                        label = FormatUtils.traffic(record.downloadBytes),
                    )
                }
            }

            Spacer(modifier = Modifier.width(Dimens.gap.md))

            // 右侧：当天总流量环形图（中心合计）
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                TrafficDonut(
                    uploadBytes = record.uploadBytes,
                    downloadBytes = record.downloadBytes,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = FormatUtils.traffic(record.totalBytes),
                        style = SlteType.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 上下行方向小胶囊：箭头 + 数值。 */
@Composable
private fun DirectionPill(
    isUp: Boolean,
    label: String,
) {
    val color =
        if (isUp) {
            SlteColors.current.brandGold
        } else {
            MaterialTheme.colorScheme.primary
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isUp) SlteIcons.ArrowUp else SlteIcons.ArrowDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimens.icon.md),
        )
        Spacer(modifier = Modifier.width(Dimens.gap.xs))
        Text(
            text = label,
            style = SlteType.field,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

// region 30天流量趋势图

@Composable
private fun TrafficTrendChart(records: List<TrafficLogRecord>) {
    val last30 = records.takeLast(30)
    val maxBytes = last30.maxOf { it.totalBytes }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            Text(
                text = "30天流量趋势",
                style = SlteType.title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Canvas(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val barCount = last30.size
                val gapPx = 2.dp.toPx()
                val barWidth = (size.width - gapPx * (barCount - 1)) / barCount

                last30.forEachIndexed { index, record ->
                    val barHeight =
                        (record.totalBytes.toFloat() / maxBytes.toFloat()) * size.height
                    val x = index * (barWidth + gapPx)
                    val y = size.height - barHeight

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
        }
    }
}

// endregion

@Composable
private fun PullRefreshScrollable(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
    }
}
