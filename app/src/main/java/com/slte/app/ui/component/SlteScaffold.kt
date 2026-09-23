// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType

/** 统一顶部栏脚手架：返回键 + 标题 + 可选操作区，供各详情/设置页复用。 */
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
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        style = SlteType.title,
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
        content = content,
    )
}
