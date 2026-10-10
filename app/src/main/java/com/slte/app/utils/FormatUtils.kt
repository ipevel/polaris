// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import android.content.Context
import com.slte.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object FormatUtils {

    fun balance(balanceCents: Int): String {
        val yuan = balanceCents.toDouble() / 100.0
        return String.format(Locale.US, "%.2f", yuan)
    }

    fun compactIp(ip: String): String {
        if (ip.length <= 20 || !ip.contains(':')) return ip
        return "${ip.take(13)}…${ip.takeLast(6)}"
    }

    fun traffic(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        return when {
            bytes >= 1024L * 1024 * 1024 * 1024 -> tb(bytes)
            bytes >= 1024L * 1024 * 1024 -> gb(bytes)
            bytes >= 1024L * 1024 -> mb(bytes)
            bytes >= 1024 -> kb(bytes)
            else -> "${bytes}B"
        }
    }

    private fun tb(bytes: Long): String {
        val v = bytes.toDouble() / (1024.0 * 1024 * 1024 * 1024)
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "TB"
    }

    private fun gb(bytes: Long): String {
        val v = bytes.toDouble() / (1024.0 * 1024 * 1024)
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "GB"
    }

    private fun mb(bytes: Long): String {
        val v = bytes.toDouble() / (1024.0 * 1024)
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "MB"
    }

    private fun kb(bytes: Long): String {
        val v = bytes.toDouble() / 1024.0
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "KB"
    }

    fun speed(bytesPerSecond: Long): String = "${speedValue(bytesPerSecond)}/s"

    /**
     * 首页速率卡的数值文本（不含 `/s`，单位由 UI 单独排版）。
     *
     * 和 [traffic] 的唯一区别是**单位提升阈值取 `999.95` 而不是 `1024`**，小数固定 1 位。
     * 于是整数部分最多 3 位，现实速率（< 1 PB/s）下整串最长 7 个字符（`999.9KB`）。
     *
     * 速率卡把数值和 `/s` 排在同一行，能用的宽度是定死的，字号 × 字符数必须放得下。
     * 走 [traffic] 的话，`1024KB` 有 8 个字符、`8388608TB` 有 9 个，会被截成错误读数。
     */
    fun speedValue(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "0B"
        if (bytesPerSecond < 1024L) return "${bytesPerSecond}B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytesPerSecond.toDouble() / 1024.0
        var i = 0
        while (v >= UNIT_PROMOTE_AT && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.') + units[i]
    }

    /** 1 位小数下四舍五入到 1000 的边界；取 999.95 保证提升后整数部分永远 ≤ 3 位。 */
    private const val UNIT_PROMOTE_AT = 999.95

    /**
     * 连接时长：毫秒 → `H:MM:SS`（不足 1 小时给 `MM:SS`）。
     * 纯数值格式、无需本地化；`<= 0` 返回空串，由调用方决定是否展示（未连接时不显示时长）。
     */
    fun formatDuration(elapsedMs: Long): String {
        if (elapsedMs <= 0L) return ""
        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDate(epochSeconds: Long): String = formatEpochDate(epochSeconds, DateFormats.ISO)

    fun formatExpiryDate(epochSeconds: Long): String = formatEpochDate(epochSeconds, DateFormats.EXPIRY)

    private fun formatEpochDate(
        epochSeconds: Long,
        formatter: DateTimeFormatter,
    ): String {
        if (epochSeconds <= 0L || epochSeconds > MAX_EPOCH_SECOND) return ""
        return Instant
            .ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }

    private const val MAX_EPOCH_SECOND = 253_402_300_799L

    private object DateFormats {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        val EXPIRY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US)
    }

    fun periodLabel(
        period: String,
        context: Context,
    ): String {
        val resId =
            when (period) {
                "month_price" -> R.string.period_month
                "quarter_price" -> R.string.period_quarter
                "half_year_price" -> R.string.period_half_year
                "year_price" -> R.string.period_year
                "two_year_price" -> R.string.period_two_year
                "three_year_price" -> R.string.period_three_year
                "onetime_price" -> R.string.period_onetime
                "reset_price" -> R.string.period_reset
                else -> return period
            }
        return context.getString(resId)
    }
}
