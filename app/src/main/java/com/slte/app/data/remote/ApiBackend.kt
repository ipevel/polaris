package com.slte.app.data.remote

data class ApiBackend(
    val type: String,
    val baseUrl: String,
    val apiPrefix: String = "/api/v1"
)
