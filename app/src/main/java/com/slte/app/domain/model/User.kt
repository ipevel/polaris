package com.slte.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val displayName: String,
    val email: String = "",
    val authData: String = "",
    val subscribeToken: String = "",
    val balance: String = "0.00",
    val remindExpire: Int = 0,
    val remindTraffic: Int = 0,
)
