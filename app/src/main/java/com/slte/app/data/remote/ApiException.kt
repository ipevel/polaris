package com.slte.app.data.remote

import androidx.annotation.StringRes
import java.io.IOException

class ApiException(
    override val message: String,
    @StringRes val stringResId: Int? = null,
) : IOException(message)
