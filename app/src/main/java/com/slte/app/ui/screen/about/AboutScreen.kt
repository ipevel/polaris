// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SlteRow
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens

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

    SlteScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
            contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
        ) {
            item {
                AboutIdentityCard(
                    appName = siteInfo?.appName?.ifBlank { null } ?: stringResource(R.string.app_name),
                    description =
                    siteInfo?.appDescription?.ifBlank { null }
                        ?: stringResource(R.string.about_app_desc),
                )
            }

            item {
                SlteCard(modifier = Modifier.fillMaxWidth()) {
                    SlteRow(
                        icon = SlteIcons.About,
                        title = stringResource(R.string.about_app_version),
                        value = BuildConfig.VERSION_NAME,
                        valueMono = true,
                    )
                    SlteRow(
                        icon = SlteIcons.Settings,
                        title = stringResource(R.string.about_kernel_version),
                        value = kernelVersion ?: Constants.PLACEHOLDER_DASH,
                        valueMono = true,
                        topDivider = true,
                    )
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimens.size.row)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.checkUpdate(manual = true)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (state is UpdateUiState.Checking) {
                            LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
                        } else {
                            Text(
                                text = stringResource(R.string.about_check_update),
                                style = SlteType.cardTitle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(Dimens.gap.xs))
                            Icon(
                                imageVector = SlteIcons.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.icon.md),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item {
                AboutRowCard(
                    icon = SlteIcons.ExportLog,
                    title = stringResource(R.string.about_log_export),
                    onClick = {
                        val file = AppLog.export(context)
                        if (file == null) {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.about_log_export_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            return@AboutRowCard
                        }
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.about_log_exported),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
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
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.about_log_share_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
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
            android.widget.Toast
                .makeText(context, context.getString(res), android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.consumeTip()
        }
    }
}
