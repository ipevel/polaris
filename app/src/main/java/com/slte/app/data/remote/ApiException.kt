// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import androidx.annotation.StringRes
import java.io.IOException

class ApiException(
    override val message: String,
    @StringRes val stringResId: Int? = null,
) : IOException(message)
