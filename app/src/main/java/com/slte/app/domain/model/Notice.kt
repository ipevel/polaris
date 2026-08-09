package com.slte.app.domain.model

/**
 * 公告信息。
 *
 * @param body 公告正文，HTML 格式
 * @param createdAt 创建时间（Unix 秒级时间戳）
 */
data class Notice(
    val id: Int,
    val title: String,
    val body: String,
    val tags: List<String>,
    val createdAt: Long
)
