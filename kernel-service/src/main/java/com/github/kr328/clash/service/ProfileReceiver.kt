package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.service.data.ImportedDao
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                Global.launch {
                    // 清理排定的内核抓取闹钟
                    ImportedDao().queryAllUUIDs().forEach {
                        cancelNext(context, it)
                    }
                }
            }
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                val redirect = intent.setComponent(ProfileWorker::class.componentName)

                context.startForegroundServiceCompat(redirect)
            }
        }
    }

    companion object {
        fun cancelNext(context: Context, uuid: UUID) {
            val intent = pendingIntentOf(context, uuid)

            context.getSystemService<AlarmManager>()?.cancel(intent)
        }

        private fun pendingIntentOf(context: Context, uuid: UUID): PendingIntent {
            val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
                .setComponent(ProfileReceiver::class.componentName)
                .setUUID(uuid)

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
    }
}
