package com.slte.app.data.remote.adapter

import com.slte.app.BuildConfig
import com.slte.app.data.remote.ApiException
import com.slte.app.data.remote.api.ApiResponse
import com.slte.app.utils.ApiErrors
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

internal object AdapterExecute {
    suspend fun <R> raw(block: suspend () -> R): R = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        throw e
    } catch (e: HttpException) {
        val errorBody = e.response()?.errorBody()?.string()
        AppLog.w(
            "SLTE-Api",
            "execute HttpException: code=${e.code()}, body=${AppLog.sanitize(errorBody?.take(500) ?: "")}",
        )
        val serverMessage = extractServerMessage(errorBody)
        if (serverMessage != null) {
            throw ApiException(serverMessage)
        }
        throw ApiException("服务器返回 ${e.code()}，请稍后重试", ApiErrors.NETWORK)
    } catch (e: SerializationException) {
        AppLog.e(
            "SLTE-Api",
            "响应解析失败（后端字段类型可能与客户端不一致）: ${e.javaClass.simpleName}: ${sanitizeLog(e.message ?: "Unknown")}",
        )
        throw ApiException("服务器响应格式异常，请稍后重试", ApiErrors.SERIALIZATION)
    } catch (e: IOException) {
        AppLog.w("SLTE-Api", "execute IOException: ${sanitizeLog(e.message ?: "Unknown")}")
        throw ApiException("请求失败，请检查网络连接", ApiErrors.NETWORK)
    } catch (e: Exception) {
        AppLog.w("SLTE-Api", "execute unexpected ${e.javaClass.simpleName}: ${sanitizeLog(e.message ?: "Unknown")}")
        throw ApiException("服务器响应异常", ApiErrors.NETWORK)
    }

    suspend fun <T> typed(block: suspend () -> ApiResponse<T>): ApiResponse<T> {
        val response = raw(block)
        val message = response.message
        if (response.data == null && message != null) {
            if (BuildConfig.DEBUG) {
                AppLog.w("SLTE-Api", "execute: data=null, message=${sanitizeLog(message)}")
            }
            throw ApiException(message)
        }
        return response
    }

    fun extractServerMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        val root =
            try {
                Json.parseToJsonElement(errorBody) as? JsonObject
            } catch (_: Exception) {
                return null
            } ?: return null

        val validationMessage = root["errors"]?.let { detailText(it) }
        if (!validationMessage.isNullOrBlank()) return validationMessage.trim()

        val genericMessage = (root["message"] as? JsonPrimitive)?.content
        return genericMessage?.takeIf { it.isNotBlank() }?.trim()
    }

    private fun detailText(value: JsonElement): String? = when (value) {
        is JsonPrimitive -> value.content.takeIf { it.isNotBlank() }
        is JsonArray -> value.firstNotNullOfOrNull { detailText(it) }
        is JsonObject -> value.values.firstNotNullOfOrNull { detailText(it) }
        else -> null
    }
}

internal fun <T> List<T>?.orEmptyLogged(what: String): List<T> {
    if (this != null) return this
    AppLog.w("SLTE-Api", "$what: 响应 data 为空且无 message，按空结果处理")
    return emptyList()
}

internal fun Boolean?.orFalseLogged(what: String): Boolean {
    if (this != null) return this
    AppLog.w("SLTE-Api", "$what: 响应 data 为空且无 message，按 false 处理")
    return false
}

internal fun <T : Any> T?.orNullLogged(what: String): T? {
    if (this == null) AppLog.w("SLTE-Api", "$what: 响应 data 为空且无 message，按空对象处理")
    return this
}
