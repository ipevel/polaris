package com.slte.app.data.remote.config

import com.slte.app.BuildConfig
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class RemoteConfigDto(
    @SerialName("api_base_url") val apiBaseUrl: String? = null,

    @SerialName("api_base_urls") val apiBaseUrls: JsonElement? = null,

    @SerialName("api") val api: JsonElement? = null,
    @SerialName("direct_domains") val directDomains: JsonElement? = null,
    @SerialName("api_type") val apiType: String? = null,
    @SerialName("crisp_website_id") val crispWebsiteId: String? = null,
    @SerialName("crisp_enabled") val crispEnabled: Boolean? = null,

    @SerialName("config_version") val configVersion: String? = null,
    @SerialName("update_version") val updateVersion: String? = null,
    @SerialName("update_changelog_title") val updateChangelogTitle: String? = null,
    @SerialName("update_changelog") val updateChangelog: String? = null,
    @SerialName("update_force") val updateForce: Boolean? = null,
    @SerialName("update_apk_url") val updateApkUrl: String? = null,
)

internal object RemoteConfigParser {
    private val ALLOWED_HOST_SUFFIXES: List<String> get() = AllowedHosts.SUFFIXES

    fun validateDto(dto: RemoteConfigDto): Boolean {
        val version = dto.configVersion ?: return true

        return version.matches(Regex("[0-9a-zA-Z.\\-]+"))
    }

    fun resolveApiCandidates(dto: RemoteConfigDto): List<String> {
        val fromArray = dto.apiBaseUrls?.let { el -> jsonElementToList(el) } ?: emptyList()
        val fromApi = dto.api?.let { el -> jsonElementToList(el) } ?: emptyList()
        val list =
            buildList {
                dto.apiBaseUrl?.let { takeIfAllowed(it) }?.let { add(it) }
                (fromArray + fromApi).mapNotNull { takeIfAllowed(it) }.forEach { if (it !in this) add(it) }
                if (isEmpty()) add(BuildConfig.API_BASE_URL)
            }
        return list
    }

    fun buildMergedConfig(
        dto: RemoteConfigDto,
        primary: String,
        candidates: List<String>,
    ): RemoteConfigData {
        val updateApkUrl = dto.updateApkUrl?.trim()?.let { takeIfAllowed(it) } ?: ""
        return RemoteConfigData(
            apiBaseUrl = primary,
            apiBaseUrls = candidates.ifEmpty { listOf(BuildConfig.API_BASE_URL) },
            directDomains = resolveDirectDomains(dto.directDomains),
            apiType = dto.apiType?.trim()?.takeIf { it == BuildConfig.API_TYPE } ?: BuildConfig.API_TYPE,
            crispWebsiteId =
            dto.crispWebsiteId?.trim()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.CRISP_WEBSITE_ID,
            crispEnabled = dto.crispEnabled ?: BuildConfig.CRISP_ENABLED,
            updateVersion = dto.updateVersion?.trim() ?: "",
            updateChangelogTitle = dto.updateChangelogTitle?.trim() ?: "",
            updateChangelog = dto.updateChangelog ?: "",
            updateForce = dto.updateForce == true && updateApkUrl.isNotBlank(),
            updateApkUrl = updateApkUrl,
        )
    }

    private fun jsonElementToList(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> listOf(element.content)
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
        else -> emptyList()
    }

    private fun takeIfAllowed(value: String): String? {
        val candidate = ConfigValidation.decodeApiCandidate(value)
        val accepted = ConfigValidation.isValidApiUrl(candidate, ALLOWED_HOST_SUFFIXES)
        if (!accepted) {
            AppLog.w(
                "Polaris-Config",
                "RemoteConfig: API 候选被拒（https 且主机需在编译期白名单内）: ${sanitizeLog(candidate)}",
            )
            return null
        }
        return candidate.trim()
    }

    private fun resolveDirectDomains(element: JsonElement?): List<String> = buildList {
        val values =
            when (element) {
                null -> emptyList()
                is JsonPrimitive -> listOf(element.content)
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
                else -> emptyList()
            }
        values.mapNotNull { takeDomain(it) }.forEach { if (it !in this) add(it) }
    }

    private fun takeDomain(value: String): String? = if (ConfigValidation.isValidDomain(value, ALLOWED_HOST_SUFFIXES)) {
        value.trim().lowercase().trimEnd('.')
    } else {
        AppLog.w(
            "Polaris-Config",
            "RemoteConfig: 直连域名被拒（需两段以上且在编译期白名单内）: ${sanitizeLog(value)}",
        )
        null
    }
}
