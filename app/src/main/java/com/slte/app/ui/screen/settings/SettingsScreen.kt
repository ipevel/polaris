package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.SlteGroup
import com.slte.app.ui.component.SlteGroupDivider
import com.slte.app.ui.component.SlteOptionMenu
import com.slte.app.ui.component.SlteOptionMenuItem
import com.slte.app.ui.component.SlteRow
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.SlteSwitchRow
import com.slte.app.ui.component.SubmitTipHost
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showKernelMenu by remember { mutableStateOf(false) }
    var showTunStackMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    // 触发行的位置：小弹窗要以它为锚点（行滚动时同步刷新）
    var kernelBounds by remember { mutableStateOf<Rect?>(null) }
    var tunStackBounds by remember { mutableStateOf<Rect?>(null) }
    var languageBounds by remember { mutableStateOf<Rect?>(null) }
    val data by viewModel.data.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        SlteScaffold(
            title = stringResource(R.string.settings_title),
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
                    SlteGroup {
                        // 单内核包不显示切换入口
                        if (data.availableKernels.size > 1) {
                            SlteRow(
                                icon = SlteIcons.Kernel,
                                title = stringResource(R.string.settings_kernel),
                                value = data.currentKernel?.displayName.orEmpty(),
                                chevron = true,
                                onClick = { showKernelMenu = true },
                                modifier = Modifier.onGloballyPositioned { kernelBounds = it.boundsInWindow() },
                            )
                            SlteGroupDivider()
                        }
                        SlteRow(
                            icon = SlteIcons.TunStack,
                            title = stringResource(R.string.settings_tun_stack),
                            value = stringResource(data.tunStackMode.labelRes),
                            chevron = true,
                            onClick = { showTunStackMenu = true },
                            modifier = Modifier.onGloballyPositioned { tunStackBounds = it.boundsInWindow() },
                        )
                        SlteGroupDivider()
                        SlteRow(
                            icon = SlteIcons.Language,
                            title = stringResource(R.string.settings_language),
                            value = stringResource(LanguageMode.fromLocale(data.locale).labelRes),
                            chevron = true,
                            onClick = { showLanguageMenu = true },
                            modifier = Modifier.onGloballyPositioned { languageBounds = it.boundsInWindow() },
                        )
                        SlteGroupDivider()
                        SlteRow(
                            icon = SlteIcons.ChangePassword,
                            title = stringResource(R.string.settings_change_password),
                            chevron = true,
                            onClick = viewModel::showChangePassword,
                        )
                    }
                }

                item {
                    SlteGroup {
                        SlteSwitchRow(
                            icon = if (data.darkModeEnabled) SlteIcons.LightMode else SlteIcons.DarkMode,
                            title = stringResource(R.string.settings_dark_mode),
                            checked = data.darkModeEnabled,
                            onCheckedChange = viewModel::setDarkMode,
                        )
                        SlteGroupDivider()
                        SlteSwitchRow(
                            icon = SlteIcons.Email,
                            title = stringResource(R.string.settings_expire_remind),
                            checked = data.expireRemindEnabled,
                            enabled = data.remindSync == RemindSync.Idle,
                            onCheckedChange = viewModel::setExpireRemind,
                        )
                        SlteGroupDivider()
                        SlteSwitchRow(
                            icon = SlteIcons.Remind,
                            title = stringResource(R.string.settings_traffic_remind),
                            checked = data.trafficRemindEnabled,
                            enabled = data.remindSync == RemindSync.Idle,
                            onCheckedChange = viewModel::setTrafficRemind,
                        )
                    }
                }

                data.errorMessageRes?.let { res ->
                    item {
                        Text(
                            text = stringResource(res),
                            style = SlteType.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Dimens.gap.lg),
                        )
                    }
                }
            }
        }

        // 小弹窗放在页面最上层：既不会被列表裁切，也能盖住顶栏
        SlteOptionMenu(
            expanded = showKernelMenu,
            anchorBounds = kernelBounds,
            onDismissRequest = { showKernelMenu = false },
        ) {
            data.availableKernels.forEach { type ->
                SlteOptionMenuItem(
                    label = type.displayName,
                    selected = data.currentKernel == type,
                    onClick = {
                        viewModel.switchKernel(type)
                        showKernelMenu = false
                    },
                )
            }
        }

        SlteOptionMenu(
            expanded = showTunStackMenu,
            anchorBounds = tunStackBounds,
            onDismissRequest = { showTunStackMenu = false },
        ) {
            TunStackMode.entries.forEach { mode ->
                SlteOptionMenuItem(
                    label = stringResource(mode.labelRes),
                    selected = data.tunStackMode == mode,
                    onClick = {
                        viewModel.setTunStackMode(mode)
                        showTunStackMenu = false
                    },
                )
            }
        }

        SlteOptionMenu(
            expanded = showLanguageMenu,
            anchorBounds = languageBounds,
            onDismissRequest = { showLanguageMenu = false },
        ) {
            LanguageMode.entries.forEach { mode ->
                SlteOptionMenuItem(
                    label = stringResource(mode.labelRes),
                    selected = LanguageMode.fromLocale(data.locale) == mode,
                    onClick = {
                        viewModel.setLocale(mode.locale)
                        showLanguageMenu = false
                    },
                )
            }
        }
    }

    val changePasswordState = viewModel.changePasswordState.collectAsStateWithLifecycle().value
    val editing = changePasswordState as? ChangePasswordState.Editing
    if (editing != null) {
        ChangePasswordSheet(
            state = editing,
            onOldPasswordChange = viewModel::onOldPasswordChange,
            onNewPasswordChange = viewModel::onNewPasswordChange,
            onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
            onSubmit = viewModel::submitChangePassword,
            onDismiss = viewModel::dismissChangePassword,
        )
    }

    val toast = rememberToast()
    val tip = viewModel.tip.collectAsStateWithLifecycle().value
    SubmitTipHost(tip = tip, onTipShown = viewModel::clearTip)

    LaunchedEffect(data.tunStackSwitchCount) {
        if (data.tunStackSwitchCount > 0) {
            toast.show(R.string.settings_tun_stack_switched)
            viewModel.consumeTunStackSwitch()
        }
    }
}
