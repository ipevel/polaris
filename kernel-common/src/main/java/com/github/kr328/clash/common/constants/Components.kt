package com.github.kr328.clash.common.constants

import android.content.ComponentName
import com.github.kr328.clash.common.util.packageName

object Components {
    // SLTE 没有 CMA 的 View 系 Activity，通知统一回到本应用主界面
    val MAIN_ACTIVITY = ComponentName(packageName, "com.slte.app.MainActivity")
    val PROPERTIES_ACTIVITY = ComponentName(packageName, "com.slte.app.MainActivity")
}
