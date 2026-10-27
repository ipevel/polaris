package com.slte.app.kernel.api

import java.util.UUID

interface KernelProfiles {
    suspend fun create(
        type: KernelProfileType,
        name: String,
        source: String = "",
    ): UUID

    suspend fun commit(uuid: UUID)

    suspend fun delete(uuid: UUID)

    suspend fun queryByUUID(uuid: UUID): KernelProfile?

    suspend fun queryAll(): List<KernelProfile>

    suspend fun queryActive(): KernelProfile?

    suspend fun setActive(uuid: UUID)
}
