package com.slte.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.ui.theme.TextSizes
import com.slte.app.utils.Dimens

/** 代理模式选择弹窗（选择结果由调用方写入内核） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyModeSheet(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val options = listOf(
        stringResource(R.string.dashboard_proxy_rule) to stringResource(R.string.proxy_rule_desc),
        stringResource(R.string.dashboard_proxy_global) to stringResource(R.string.proxy_global_desc)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(
                    horizontal = Dimens.inviteSheetPaddingH,
                    vertical = Dimens.inviteSheetPaddingV
                )
        ) {
            Text(
                text = stringResource(R.string.action_proxy_mode),
                fontSize = TextSizes.sheetTitle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            options.forEach { (mode, desc) ->
                val selected = currentMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                onSelect(mode)
                                onDismiss()
                            }
                        )
                        .padding(vertical = Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.spacingSm)
                    ) {
                        Text(
                            text = mode,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = TextSizes.actionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            fontSize = TextSizes.inviteSheetDesc,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXl))
        }
    }
}
