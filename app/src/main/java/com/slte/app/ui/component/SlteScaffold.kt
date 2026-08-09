package com.slte.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.theme.TextSizes
import com.slte.app.utils.Dimens

/** 统一页面脚手架：保证所有二级页面 TopBar 样式一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlteScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = TextSizes.topBarTitle
                    )
                },
                navigationIcon = {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = stringResource(R.string.back),
                        onClick = onBack,
                        showBackground = false
                    )
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        content = content
    )
}
