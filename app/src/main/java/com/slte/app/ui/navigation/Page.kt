package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

enum class Page { Dashboard, Invite, Server, Notice, Orders, Plans, Profile, Settings, About }

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
internal fun rememberSaveablePageStack(): SnapshotStateList<Page> = rememberSaveable(saver = pageStackSaver) { mutableStateListOf(Page.Dashboard) }
