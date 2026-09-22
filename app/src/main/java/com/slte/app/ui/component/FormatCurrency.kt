// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.utils.FormatUtils

@Composable
fun formatCurrency(cents: Int): String = stringResource(R.string.currency_symbol) + FormatUtils.balance(cents)

@Composable
fun formatNegCurrency(cents: Int): String = "-" + stringResource(R.string.currency_symbol) + FormatUtils.balance(cents)

@Composable
fun formatPlusCurrency(cents: Int): String = "+" + stringResource(R.string.currency_symbol) + FormatUtils.balance(cents)
