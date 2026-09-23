// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** 底部四个根 Tab，代替原先的四个根页。 */
enum class RootTab { Home, Server, Traffic, Profile }

/** 二级叶子页（全屏 push，叠加在根 Tab 之上）。 */
enum class Page { Invite, Notice, Orders, Plans, Settings, About, Ticket }

internal enum class PendingNav {
    Invite,
    Notice,
    Orders,
    Plans,
    ;

    val page: Page
        get() =
            when (this) {
                Invite -> Page.Invite
                Notice -> Page.Notice
                Orders -> Page.Orders
                Plans -> Page.Plans
            }
}

private val pageStackSaver =
    listSaver<SnapshotStateList<Page>, String>(
        save = { stack -> stack.map { it.name } },
        restore = { names -> names.map { Page.valueOf(it) }.toMutableStateList() },
    )

@Composable
internal fun rememberSaveablePageStack(): SnapshotStateList<Page> = rememberSaveable(saver = pageStackSaver) { mutableStateListOf() }
