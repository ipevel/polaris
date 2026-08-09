package com.slte.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * TGS 动态贴纸组件。
 *
 * 用于渲染 Telegram TGS 格式贴纸（gzip 压缩的 Lottie JSON），
 * 文件放在 assets 目录，本地加载播放，无需网络。
 *
 * @param assetPath assets 中的文件路径，如 "stickers/login.tgs"
 * @param modifier 尺寸约束，建议用 Modifier.size(96.dp)
 * @param iterations 循环次数，默认无限循环
 */
@Composable
fun AnimatedSticker(
    assetPath: String,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(assetPath)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}
