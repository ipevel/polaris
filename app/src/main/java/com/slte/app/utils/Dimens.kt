// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import androidx.compose.ui.unit.dp

object Dimens {

    object gap {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
    }

    object icon {
        val sm = 16.dp
        val md = 20.dp
        val lg = 24.dp
    }

    val iconBadgeSize = 34.dp
    val iconBadgeRadius = 10.dp

    object size {
        val button = 48.dp
        val buttonMd = 40.dp
        val row = 56.dp
        val touchTarget = 48.dp
    }

    val logoSize = 96.dp

    val periodGridItemPaddingV = 20.dp

    val paymentMethodCellHeight = 48.dp

    val strokeMedium = 1.5.dp
    val strokeThick = 2.dp
    val dividerThickness = 1.dp

    val maxContentWidth = 480.dp

    val loadingBoxSize = 76.dp
    val loadingAnimSize = 32.dp
    val loadingTextGap = 5.dp
    val loadingScrimAlpha = 0.3f
    val loadingBoxElevation = 8.dp

    val stateStickerSize = 80.dp

    val switchTrackWidth = 48.dp
    val switchTrackHeight = 28.dp

    val switchTouchHeight = 48.dp
    val switchThumbSize = 20.dp
    val switchThumbPadding = 4.dp

    val flagSize = 40.dp

    val flagCornerRadius = 4.dp

    val sendCodeButtonWidth = 110.dp

    val topBarActionBgSize = 36.dp

    val cardElevation = 0.dp

    val planStatusPaddingV = 4.dp
    val planStatusChipCornerRadius = 50

    val inviteCodeItemBgAlpha = 0.5f
    val noticeTimeAlpha = 0.75f
    val paymentMethodDotAlpha = 0.4f
    val dividerAlpha = 0.5f
    val disabledAlpha = 0.4f

    val dashboardScreenPaddingH = gap.lg
    val dashboardScreenPaddingV = 10.dp
    val dashboardCardSpacing = 10.dp

    val dashboardCompactBreakpoint = 840.dp
    val dashboardScreenPaddingVCompact = 6.dp
    val dashboardCardSpacingCompact = 6.dp
    val dashboardUsageBarHeight = 6.dp
    val dashboardUsageBarRadius = 3.dp
    val dashboardListValueMaxWidth = 180.dp
    val dashboardChevronGap = 2.dp
    val dashboardToggleWidth = 100.dp
    val dashboardToggleHeight = 52.dp

    val dashboardToggleCardMinHeight = 240.dp

    val dashboardToggleCardMinHeightCompact = 170.dp
    val dashboardToggleThumbSize = 44.dp
    val dashboardToggleThumbOffset = 52.dp
    val dashboardToggleThumbPadding = 4.dp
    val dashboardToggleGap = 10.dp
    val dashboardToggleAnimDurationMs = 350
    val dashboardActionBtnHeight = 76.dp

    val inviteStatCardPaddingV = 20.dp
    val inviteStickerSize = 100.dp
    val inviteCodeItemHeight = 52.dp
    val inviteCodeItemPaddingH = 16.dp
    val inviteCodeCopyIconSize = 18.dp
    val inviteCodeCopyBtnSize = 48.dp

    val sheetPaddingH = gap.xl
    val sheetPaddingV = gap.sm

    val inviteMethodListMaxHeight = 240.dp
    val popupShadowElevation = 8.dp

    val radioDotSize = 18.dp
    val radioDotInnerSize = 9.dp
    val radioDotGap = 5.dp
    val paymentMethodDotSize = 14.dp

    val noticeTagPaddingH = 8.dp
    val noticeTagSpacing = 6.dp
    val noticeBodyMaxLines = 2

    val bottomNavHeight = 64.dp
    val bottomNavIndicatorSize = 4.dp
}

object VerificationCodeConfig {
    const val countdownSeconds = 60
    const val countdownIntervalMs = 1000L
}
