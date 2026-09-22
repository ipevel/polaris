// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.data

import androidx.room.*
import java.util.*

@Dao
@TypeConverters(Converters::class)
interface PendingDao {
    @Query("SELECT * FROM pending WHERE uuid = :uuid")
    suspend fun queryByUUID(uuid: UUID): Pending?

    @Query("DELETE FROM pending WHERE uuid = :uuid")
    suspend fun remove(uuid: UUID)

    @Query("SELECT EXISTS(SELECT 1 FROM pending WHERE uuid = :uuid)")
    suspend fun exists(uuid: UUID): Boolean

    @Query("SELECT uuid FROM pending ORDER BY createdAt")
    suspend fun queryAllUUIDs(): List<UUID>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: Pending)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(pending: Pending)
}
