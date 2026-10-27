package com.slte.app.data.remote.api

/**
 * 会话探测结果：[VALID] 服务端接受当前凭据；[INVALID] 服务端明确拒绝（401/403）；
 * [UNKNOWN] 网络异常或服务端故障，不可据此判定失效。
 */
enum class SessionProbeResult {
    VALID,
    INVALID,
    UNKNOWN,
}
