// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.slteAuroraBackground

/**
 * 统一顶部栏脚手架：返回键 + 标题 + 可选操作区，供各详情/设置页复用。
 * 顶栏容器色透明，避免一条不透明的 surface 把页面极光氛围在顶部截断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlteScaffold(
    title: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.slteAuroraBackground(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = SlteType.pageTitle,
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        CircleIconButton(
                            icon = SlteIcons.Back,
                            description = stringResource(R.string.back),
                            onClick = onBack,
                            showBackground = false,
                        )
                    }
                },
                actions = { actions() },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        content = content,
    )
}
