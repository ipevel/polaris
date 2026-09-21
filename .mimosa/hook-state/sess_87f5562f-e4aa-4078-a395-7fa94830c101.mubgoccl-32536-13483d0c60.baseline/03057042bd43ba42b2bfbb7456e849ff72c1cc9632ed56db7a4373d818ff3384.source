package com.slte.app.ui.screen.traffic

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    onBack: () -> Unit,
    onRenew: () -> Unit,
    viewModel: TrafficViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()

    ToastTip(
        message = data.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    SlteScaffold(
        title = stringResource(R.string.traffic_title),
        onBack = onBack,
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
                        TrafficContent(
                            data = data,
                            onRenew = onRenew,
                        )
                }
            }
        }
    }
}

@Composable
private fun TrafficContent(
    data: TrafficData,
    onRenew: () -> Unit,
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
        // 用量总览卡片（有套餐才显示）
        item {
            if (data.hasPlan) {
                TrafficUsageCard(data = data, onRenew = onRenew)
            }
        }

        // 流量记录标题
        item {
            Text(
                text = stringResource(R.string.traffic_records_title),
                style = SlteType.title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Dimens.gap.sm),
            )
        }

        // 30天流量趋势图
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

// region 用量总览卡片

@Composable
private fun TrafficUsageCard(
    data: TrafficData,
    onRenew: () -> Unit,
) {
    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            // 第一行：剩余流量 + 状态徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "剩余流量",
                        style = SlteType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Dimens.gap.xs))
                    Text(
                        text = FormatUtils.traffic(data.totalBytes - data.usedBytes),
                        style = SlteType.heading,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                TrafficStatusBadge(isValid = data.isValid)
            }

            // 第二行：距离重置
            if (data.daysUntilExpired != null) {
                Spacer(modifier = Modifier.height(Dimens.gap.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "距离重置",
                        style = SlteType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${data.daysUntilExpired} 天",
                        style = SlteType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 第三行：套餐到期
            if (data.expiredAt > 0L) {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "套餐到期",
                        style = SlteType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = FormatUtils.formatExpiryDate(data.expiredAt),
                        style = SlteType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 底部：续费按钮
            Spacer(modifier = Modifier.height(Dimens.gap.lg))
            SlteButton(
                text = stringResource(R.string.plan_renew_button),
                onClick = onRenew,
                style = SlteButtonStyle.Medium,
            )
        }
    }
}

@Composable
private fun TrafficStatusBadge(isValid: Boolean) {
    val bg =
        if (isValid) {
            SlteColors.current.statusSuccessBg
        } else {
            SlteColors.current.statusDangerBg
        }
    val fg =
        if (isValid) {
            SlteColors.current.statusSuccess
        } else {
            SlteColors.current.statusDanger
        }

    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(Dimens.planStatusChipCornerRadius))
            .background(bg)
            .padding(horizontal = Dimens.gap.lg, vertical = Dimens.planStatusPaddingV),
    ) {
        Text(
            text =
            if (isValid) {
                stringResource(R.string.dashboard_usage_valid)
            } else {
                stringResource(R.string.plan_status_expired)
            },
            fontWeight = FontWeight.SemiBold,
            style = SlteType.caption,
            color = fg,
        )
    }
}

// endregion

// region 流量记录行

@Composable
private fun TrafficRecordRow(record: TrafficLogRecord) {
    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            // 第一行：日期（左）+ 总流量（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.date.ifBlank { stringResource(R.string.traffic_records_empty) },
                    style = SlteType.body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = FormatUtils.traffic(record.totalBytes),
                    style = SlteType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            // 第二行：上传 ↑（左）+ 下载 ↓（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "↑ ${FormatUtils.traffic(record.uploadBytes)}",
                    style = SlteType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = SlteColors.current.statusSuccess,
                )
                Text(
                    text = "↓ ${FormatUtils.traffic(record.downloadBytes)}",
                    style = SlteType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// endregion

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
