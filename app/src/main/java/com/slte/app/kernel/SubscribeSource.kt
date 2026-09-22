// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import okhttp3.ResponseBody

interface SubscribeSource {
    fun getEmail(): String?

    suspend fun fetchSubscribeYaml(): ResponseBody?

    fun saveSubscriptionUpdatedAt()
}
