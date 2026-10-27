package com.github.kr328.clash.core.bridge

import androidx.annotation.Keep

@Keep
interface LogcatInterface {
    /** 返回 true 表示订阅已结束，原生侧释放回调并退出日志循环。 */
    fun received(jsonPayload: String): Boolean
}
