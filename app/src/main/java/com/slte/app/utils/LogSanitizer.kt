package com.slte.app.utils

/**
 * 日志脱敏工具：任何可能携带订阅 URL（含 token 查询参数）的日志
 * 都必须经过 [sanitizeLog] 后再输出，避免订阅令牌泄漏到 logcat。
 */
internal fun sanitizeLog(message: String): String = AppLog.sanitize(message)
