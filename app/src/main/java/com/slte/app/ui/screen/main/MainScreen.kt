// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.data.local.SecurePreferences
import com.slte.app.data.local.ThemeMode
import com.slte.app.ui.component.CircleIconButton
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.UsageCard
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel,
    data: DashboardData,
    onTraffic: () -> Unit = {},
    onServer: () -> Unit = {},
    onNotice: () -> Unit = {},
    onProfile: () -> Unit = {},
    onRenew: () -> Unit = {},
) {
    val context = LocalContext.current
    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                mainViewModel.toggleConnection()
            } else {
                mainViewModel.onVpnPermissionDenied()
            }
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

    LaunchedEffect(Unit) {
        mainViewModel.refreshKernelInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = data.siteName.ifBlank { stringResource(R.string.app_name) },
                            style = SlteType.title,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ProxyStatusBadge(
                            isConnected = data.isConnected,
                            proxyMode = data.proxyMode,
                            modifier = Modifier.padding(start = Dimens.gap.sm),
                        )
                    }
                },
                actions = {
                    CircleIconButton(
                        icon = when (themeMode) {
                            ThemeMode.DARK -> SlteIcons.LightMode
                            ThemeMode.LIGHT -> SlteIcons.DarkMode
                            ThemeMode.SYSTEM -> SlteIcons.DarkMode
                        },
                        description = stringResource(
                            when (themeMode) {
                                ThemeMode.DARK -> R.string.topbar_light_mode
                                ThemeMode.LIGHT -> R.string.topbar_dark_mode
                                ThemeMode.SYSTEM -> R.string.topbar_auto_mode
                            },
                        ),
                        onClick = mainViewModel::toggleDarkMode,
                    )
                    CircleIconButton(
                        icon = SlteIcons.Notifications,
                        description = stringResource(R.string.topbar_notice),
                        onClick = onNotice,
                    )
                    CircleIconButton(
                        icon = SlteIcons.Profile,
                        description = stringResource(R.string.topbar_profile),
                        onClick = onProfile,
                    )
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        // 加密存储降级警告
        if (SecurePreferences.isDegraded) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.secure_prefs_degraded_warning),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Dimens.gap.md),
                )
            }
        }
        DashboardContent(
            data = data,
            onToggleConnection = {
                if (!data.hasPlan) {
                    android.widget.Toast
                        .makeText(
                            context,
                            context.getString(R.string.dashboard_no_plan_tip),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    onRenew()
                } else {
                    requestNotificationPermission(context, notificationPermissionLauncher)
                    val request = mainViewModel.vpnRequestIntent()
                    if (request != null) {
                        vpnPermissionLauncher.launch(request)
                    } else {
                        mainViewModel.toggleConnection()
                    }
                }
            },
            onSelectProxyMode = mainViewModel::setProxyMode,
            onUpdateSubscription = {
                if (data.hasPlan) {
                    mainViewModel.updateSubscription()
                } else {
                    onRenew()
                }
            },
            onTraffic = onTraffic,
            onRenew = onRenew,
            onServer = onServer,
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

private fun requestNotificationPermission(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
internal fun DashboardContent(
    data: DashboardData,
    onToggleConnection: () -> Unit,
    onSelectProxyMode: (String) -> Unit,
    onUpdateSubscription: () -> Unit,
    onTraffic: () -> Unit,
    onRenew: () -> Unit,
    onServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        val compact = maxHeight < Dimens.dashboardCompactBreakpoint
        androidx.compose.foundation.lazy.LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement =
            Arrangement.spacedBy(
                if (compact) Dimens.dashboardCardSpacingCompact else Dimens.dashboardCardSpacing,
            ),
            contentPadding =
            PaddingValues(
                vertical = if (compact) Dimens.dashboardScreenPaddingVCompact else Dimens.dashboardScreenPaddingV,
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
                    actionText =
                    stringResource(
                        if (data.hasPlan) {
                            R.string.plan_renew_button
                        } else {
                            R.string.plan_buy_button
                        },
                    ),
                    actionEnabled = true,
                    onAction = onRenew,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
                ) {
                    ProxyModeCard(
                        modifier = Modifier.weight(1f),
                        proxyMode = data.proxyMode,
                        onSelectMode = onSelectProxyMode,
                    )
                    CurrentIpCard(
                        modifier = Modifier.weight(1f),
                        currentIp = data.currentIp,
                        ipCountryCode = data.ipCountryCode,
                    )
                }
            }
            item {
                DashboardActionButtons(
                    onUpdateSubscription = onUpdateSubscription,
                    hasPlan = data.hasPlan,
                    onTraffic = onTraffic,
                    onServer = onServer,
                )
            }
            item {
                ConnectToggleCard(
                    isConnected = data.isConnected,
                    isConnecting = data.isConnecting,
                    onToggle = onToggleConnection,
                    minHeight =
                    if (compact) {
                        Dimens.dashboardToggleCardMinHeightCompact
                    } else {
                        Dimens.dashboardToggleCardMinHeight
                    },
                )
            }
        }
    }
}

@Composable
private fun SiteInfoCard(
    siteName: String,
    siteDescription: String,
) {
    SlteCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.gap.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = siteName,
                style = SlteType.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (siteDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gap.xs))
                Text(
                    text = siteDescription,
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
