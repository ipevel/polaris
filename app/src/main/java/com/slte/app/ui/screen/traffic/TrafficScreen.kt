// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 概览卡主数值：SlteType.display 的字号档，但必须走等宽数字族。 */
private val SummaryValueSize = 26.sp
private val SummaryValueLineHeight = 32.sp
private val DailyDonutSize = 36.dp
private const val IsoDateTailLength = 5

/** 接口日期是 yyyy-MM-dd；界面只展示 ISO MM-DD（30 天窗口内年份没有信息量）。 */
internal fun shortDate(date: String): String = date.takeLast(IsoDateTailLength)

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
        if (data.records.isEmpty()) {
            item {
                EmptyRecords()
            }
        } else {
            item {
                TrafficSummaryCard(records = data.records)
            }

            if (data.records.size >= 2) {
                item {
                    TrafficTrendChart(records = data.records)
                }
            }

            // 区块标题与页标题（流量明细）区分用词与字号，避免两级同名同级
            item {
                Text(
                    text = stringResource(R.string.traffic_detail_section),
                    style = SlteType.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                TrafficDailyCard(records = data.records)
            }
        }
    }
}

/** 概览卡：已用总量 + 上行 / 下行 / 统计天数三个小槽。 */
@Composable
private fun TrafficSummaryCard(records: List<TrafficLogRecord>) {
    val days = records.size

    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.lg),
        ) {
            Text(
                text = stringResource(R.string.plan_used_prefix),
                style = SlteType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.xs))

            Text(
                text = FormatUtils.traffic(records.sumOf { it.totalBytes }),
                style =
                SlteType.value.copy(
                    fontSize = SummaryValueSize,
                    lineHeight = SummaryValueLineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
            ) {
                SummaryTile(
                    icon = SlteIcons.ArrowUp,
                    tint = SlteColors.current.brandGold,
                    description = stringResource(R.string.traffic_upload),
                    value = FormatUtils.traffic(records.sumOf { it.uploadBytes }),
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    icon = SlteIcons.ArrowDown,
                    tint = MaterialTheme.colorScheme.primary,
                    description = stringResource(R.string.traffic_download),
                    value = FormatUtils.traffic(records.sumOf { it.downloadBytes }),
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    icon = SlteIcons.Expiry,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = null,
                    value = pluralStringResource(R.plurals.dashboard_days, days, days),
                    modifier = Modifier.weight(1.25f),
                )
            }
        }
    }
}

/** 卡内小槽：surfaceVariant 底 + 内圆角，数值统一等宽，图标承担方向语义。 */
@Composable
private fun SummaryTile(
    icon: ImageVector,
    tint: Color,
    description: String?,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(SlteRadii.inner),
            )
            .padding(horizontal = Dimens.gap.sm, vertical = Dimens.gap.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(Dimens.icon.sm),
        )
        Spacer(modifier = Modifier.width(Dimens.gap.xs))
        Text(
            text = value,
            style = SlteType.valueSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 每日明细：一张卡装下所有天，行间用 1dp 发丝线分隔。 */
@Composable
private fun TrafficDailyCard(records: List<TrafficLogRecord>) {
    SlteCard(modifier = Modifier.fillMaxWidth()) {
        records.forEachIndexed { index, record ->
            TrafficDayRow(record = record, topDivider = index > 0)
        }
    }
}

/** 单日一行：日期 | ↑上行 ↓下行 | 当日合计 + 环形图（环内不再放数字，避免被裁切）。 */
@Composable
private fun TrafficDayRow(
    record: TrafficLogRecord,
    topDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shortDate(record.date),
                style = SlteType.valueSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(Dimens.gap.md))

            DirectionValue(isUp = true, label = FormatUtils.traffic(record.uploadBytes))

            Spacer(modifier = Modifier.width(Dimens.gap.md))

            DirectionValue(isUp = false, label = FormatUtils.traffic(record.downloadBytes))

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = FormatUtils.traffic(record.totalBytes),
                style = SlteType.valueSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(Dimens.gap.sm))

            TrafficDonut(
                uploadBytes = record.uploadBytes,
                downloadBytes = record.downloadBytes,
                modifier = Modifier.size(DailyDonutSize),
            )
        }
    }
}

/** 上下行方向值：箭头 + 等宽数值，金=上行、强调色=下行。 */
@Composable
private fun DirectionValue(
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
            contentDescription = stringResource(if (isUp) R.string.traffic_upload else R.string.traffic_download),
            tint = color,
            modifier = Modifier.size(Dimens.icon.sm),
        )
        Spacer(modifier = Modifier.width(Dimens.gap.xs))
        Text(
            text = label,
            style = SlteType.valueSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 无记录态。 */
@Composable
private fun EmptyRecords() {
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
