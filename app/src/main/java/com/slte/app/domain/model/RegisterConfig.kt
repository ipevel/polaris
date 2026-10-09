// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.model

data class RegisterConfig(

    val emailVerifyEnabled: Boolean = false,

    val inviteForceEnabled: Boolean = false,

    /** 注册邮箱后缀白名单；后端未启用时为空，不做限制。 */
    val emailWhitelist: EmailWhitelist = EmailWhitelist.None,
)
