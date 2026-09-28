// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

/**
 * 关于页「法务与许可」入口的目标地址。
 *
 * 这些文档随仓库一起分发（GPL-3.0 要求分发二进制时同时提供许可文本），应用内只做跳转，
 * 不自带 WebView，也不离线打包副本——静态链接指向默认分支，用户看到的就是与当前 Release
 * 对应的这份文档。
 */
internal object LegalLinks {
    private const val REPO = "https://github.com/ipevel/polaris/blob/main"

    /** 用户协议。 */
    const val TERMS = "$REPO/TERMS.md"

    /** 隐私政策。 */
    const val PRIVACY = "$REPO/PRIVACY.md"

    /** 开源许可与第三方组件声明（GPL-3.0 正文见仓库根 LICENSE）。 */
    const val LICENSES = "$REPO/THIRD-PARTY-NOTICES.md"
}
