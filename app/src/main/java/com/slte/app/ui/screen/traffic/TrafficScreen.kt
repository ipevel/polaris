package com.slte.app.ui.screen.traffic

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.slte.app.ui.component.UsageCard
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
        item {
            UsageCard(
                planName = data.planName,
                usedBytes = data.usedBytes,
                totalBytes = data.totalBytes,
                isValid = data.isValid,
                hasPlan = data.hasPlan,
                daysUntilExpired = if (data.expiredAt > 0L) data.daysUntilExpired else null,
                expiredAtDate = if (data.expiredAt > 0L) FormatUtils.formatExpiryDate(data.expiredAt) else null,
                actionText = stringResource(R.string.plan_renew_button),
                onAction = onRenew,
            )
        }

        item {
            Text(
                text = stringResource(R.string.traffic_records_title),
                style = SlteType.title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Dimens.gap.sm),
            )
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
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
                    text = stringResource(R.string.traffic_total, FormatUtils.traffic(record.totalBytes)),
                    style = SlteType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TrafficMetric(
                    label = stringResource(R.string.traffic_upload),
                    value = FormatUtils.traffic(record.uploadBytes),
                )
                TrafficMetric(
                    label = stringResource(R.string.traffic_download),
                    value = FormatUtils.traffic(record.downloadBytes),
                )
            }
        }
    }
}

@Composable
private fun TrafficMetric(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Dimens.gap.sm))
        Text(
            text = value,
            style = SlteType.label,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
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
