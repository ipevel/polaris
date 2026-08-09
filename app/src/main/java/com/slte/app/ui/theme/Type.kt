package com.slte.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 极简风 Material 3 字体档位 */
val SlteTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/** 业务语义化字号常量 */
object TextSizes {
    private val Xs = 11.sp
    private val Sm = 12.sp
    private val Md = 13.sp
    private val Lg = 14.sp
    private val Xl = 16.sp
    private val Xxl = 18.sp
    private val Xxxl = 24.sp

    val topBarTitle = Xl
    val loadingMessage = Md
    val actionTitle = Xl
    val actionSubtitle = Md
    val planName = Xl
    val planMeta = Sm
    val planButton = Md
    val planEmptyTitle = Xl
    val dashboardUsageTitle = Lg
    val dashboardUsageBadge = Xs
    val dashboardUsageText = Lg
    val dashboardListLabel = Lg
    val dashboardListDesc = Sm
    val dashboardListValue = Lg
    val dashboardIpValue = Md
    val dashboardActionBtn = Lg
    val dashboardToggleStatus = Md
    val inviteStatValue = Lg
    val inviteStatLabel = Xs
    val inviteCodeText = Xl
    val inviteRecordAmount = Lg
    val inviteRecordOrder = Sm
    val inviteBalanceLarge = Xxxl
    val inviteSheetDesc = Md
    val inviteSheetBalance = Xxxl
    val inviteSheetBalanceLabel = Sm
    val inviteSheetMethod = Md
    val inviteEmpty = Md
    val flagFontSize = Sm
    val sheetTitle = Xxl
    val htmlBaseFontSize = Lg
}
