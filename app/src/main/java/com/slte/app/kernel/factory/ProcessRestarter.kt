package com.slte.app.kernel.factory

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 切换内核后重启应用进程：单例引用随进程重建，新内核实例自然生效。 */
class ProcessRestarter
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
