// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.id.UndefinedIds
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.util.sendProfileUpdateCompleted
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

// 上游 CMA 的定时更新 worker：本应用没有调度方（无 AlarmManager / JobScheduler 触发，
// ACTION_PROFILE_REQUEST_UPDATE 也没有生产者），processing()/completed()/failed()/resultBuilder() 均无调用者。
// 后续若要对它做「修复」，请先确认可达性。
class ProfileWorker : BaseService() {
    private val jobs = java.util.concurrent.ConcurrentLinkedQueue<Job>()

    override fun onCreate() {
        super.onCreate()

        createChannels()

        foreground()

        launch {
            delay(TimeUnit.SECONDS.toMillis(10))

            drain()

            delay(IDLE_GRACE_MS)

            drain()

            stopSelf()
        }
    }

    private suspend fun drain() {
        while (true) {
            jobs.poll()?.join() ?: break
        }
    }

    override fun onDestroy() {
        stopForeground(true)

        while (true) {
            val job = jobs.poll() ?: break
            runBlocking { withTimeoutOrNull(DRAIN_TIMEOUT_MS) { job.join() } }
        }

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                intent.uuid?.also {
                    val job = launch {
                        run(it)
                    }

                    jobs.offer(job)
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun run(uuid: UUID) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        try {
            ProfileProcessor.update(this, imported.uuid, null)
            // 订阅更新由 App 端统一负责，此处不调度定时抓取
        } catch (e: Exception) {
            Log.w("ProfileWorker: update failed: ${sanitize(e.message ?: "Unknown")}")
        }
    }

    // 静态脱敏规则与 app 端 AppLog.sanitize 对齐：app 依赖本模块、不能反向复用，故本地维护等价镜像，
    // 两处需同步演进；AppLog 中依赖 BuildConfig 的裸域名规则因模块依赖方向无法在此镜像（残余风险）
    private val bearerPattern = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+")

    private val keyValuePattern =
        Regex(
            "(?i)((?:subscribe_token|access_token|refresh_token|auth_data|authorization|token|password|passwd|pwd)\\s*[\"']?\\s*[=:]\\s*)" +
                "(?:Bearer\\s+|Basic\\s+)?(?:([\"'])(.*?)\\2|([^\\s\"',;&{}\\]]+))",
        )

    private val urlCredentialsPattern = Regex("(?i)\\b([a-z][a-z0-9+.\\-]*://)([^\\s/]+)@")

    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val urlHostPattern = Regex("(?i)((?:https?|wss?)://)([^/\\s\"'<>]+)")

    private fun sanitize(message: String): String =
        message
            .replace(Regex("(?i)token=\\s*[^&\\s\"'}\\]]+"), "token=***")
            .replace(Regex("(?i)token%3D[^&\\s\"']+"), "token%3D***")
            .replace(bearerPattern, "Bearer ***")
            .replace(keyValuePattern) { m ->
                val quote = m.groupValues[2]
                val masked = if (m.groups[2] != null) quote + "***" + quote else "***"
                m.groupValues[1] + masked
            }
            .replace(urlCredentialsPattern, "$1***@")
            .replace(emailPattern) { m ->
                val at = m.value.indexOf('@')
                m.value.take(1) + "***" + m.value.substring(at)
            }
            .replace(urlHostPattern, "$1***")

    private fun createChannels() {
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(
                    SERVICE_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.profile_service_status)).build(),
                NotificationChannelCompat.Builder(
                    STATUS_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.profile_process_status)).build(),
                NotificationChannelCompat.Builder(
                    RESULT_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                ).setName(getString(R.string.profile_process_result)).build()
            )
        )
    }

    private fun foreground() {
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL)
            .setContentTitle(getString(R.string.profile_updater))
            .setContentText(getString(R.string.running))
            .setColor(getColorCompat(R.color.color_clash))
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForegroundCompat(R.id.nf_profile_worker, notification)
    }

    private suspend inline fun processing(name: String, block: () -> Unit) {
        val id = UndefinedIds.next()

        val notification = NotificationCompat.Builder(this, STATUS_CHANNEL)
            .setContentTitle(getString(R.string.profile_updating))
            .setContentText(name)
            .setColor(getColorCompat(R.color.color_clash))
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(STATUS_CHANNEL)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(id, notification)
        try {
            block()
        } finally {
            withContext(NonCancellable) {
                NotificationManagerCompat.from(applicationContext)
                    .cancel(id)
            }
        }
    }

    private fun resultBuilder(id: Int, uuid: UUID): NotificationCompat.Builder {
        val intent = PendingIntent.getActivity(
            this,
            id,
            Intent().setComponent(Components.PROPERTIES_ACTIVITY).setUUID(uuid),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setColor(getColorCompat(R.color.color_clash))
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOnlyAlertOnce(true)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setGroup(RESULT_CHANNEL)
    }

    private fun completed(uuid: UUID, name: String) {
        val id = UndefinedIds.next()

        val notification = resultBuilder(id, uuid)
            .setContentTitle(getString(R.string.update_successfully))
            .setContentText(getString(R.string.format_update_complete, name))
            .build()

        NotificationManagerCompat.from(this)
            .notify(id, notification)

        sendProfileUpdateCompleted(uuid)
    }

    private fun failed(uuid: UUID, name: String, reason: String) {
        val id = UndefinedIds.next()

        val content = getString(R.string.format_update_failure, name, reason)

        val notification = resultBuilder(id, uuid)
            .setContentTitle(getString(R.string.update_failure))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()

        NotificationManagerCompat.from(this)
            .notify(id, notification)

        sendProfileUpdateFailed(uuid, reason)
    }

    companion object {
        private const val SERVICE_CHANNEL = "profile_service_channel"
        private const val STATUS_CHANNEL = "profile_status_channel"
        private const val RESULT_CHANNEL = "profile_result_channel"
        private const val IDLE_GRACE_MS = 2_000L
        private const val DRAIN_TIMEOUT_MS = 15_000L
    }

    override fun onBind(intent: Intent?): IBinder {
        return Binder()
    }
}
