package com.github.kr328.clash.core

import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.core.model.OverrideSlot
import com.github.kr328.clash.core.util.parseInetSocketAddress
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

object Clash {

    private val ConfigurationOverrideJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun reset() {
        Bridge.nativeReset()
    }

    fun forceGc() {
        Bridge.nativeForceGc()
    }

    fun suspendCore(suspended: Boolean) {
        Bridge.nativeSuspend(suspended)
    }

    fun queryTunnelState(): TunnelState {
        val json = Bridge.nativeQueryTunnelState()

        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    fun coreVersion(): String = Bridge.nativeCoreVersion()

    fun queryTrafficNow(): Traffic = Bridge.nativeQueryTrafficNow()

    fun queryTrafficTotal(): Traffic = Bridge.nativeQueryTrafficTotal()

    fun notifyDnsChanged(dns: List<String>) {
        Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
    }

    fun notifyTimeZoneChanged(name: String, offset: Int) {
        Bridge.nativeNotifyTimeZoneChanged(name, offset)
    }

    fun notifyInstalledAppsChanged(uids: List<Pair<Int, String>>) {
        val uidList = uids.joinToString(separator = ",") { "${it.first}:${it.second}" }

        Bridge.nativeNotifyInstalledAppChanged(uidList)
    }

    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
    ): Int = Bridge.nativeStartTun(
        fd,
        stack,
        gateway,
        portal,
        dns,
        object : TunInterface {
            override fun markSocket(fd: Int) {
                markSocket(fd)
            }

            override fun querySocketUid(protocol: Int, source: String, target: String): Int = querySocketUid(
                protocol,
                parseInetSocketAddress(source),
                parseInetSocketAddress(target),
            )
        },
    )

    fun stopTun() {
        Bridge.nativeStopTun()
    }

    fun startHttp(listenAt: String): String? = Bridge.nativeStartHttp(listenAt)

    fun stopHttp() {
        Bridge.nativeStopHttp()
    }

    fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
        val names = Json.Default.decodeFromString(
            JsonArray.serializer(),
            Bridge.nativeQueryGroupNames(excludeNotSelectable),
        )

        return names.map {
            require(it.jsonPrimitive.isString)

            it.jsonPrimitive.content
        }
    }

    fun queryGroup(name: String, sort: ProxySort): ProxyGroup = Bridge.nativeQueryGroup(name, sort.name)
        ?.let { Json.Default.decodeFromString(ProxyGroup.serializer(), it) }
        ?: ProxyGroup("Unknown", emptyList(), "")

    fun healthCheck(name: String): CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply {
        Bridge.nativeHealthCheck(this, name)
    }

    fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    fun patchSelector(selector: String, name: String): Boolean = Bridge.nativePatchSelector(selector, name)

    /** 对单个节点跑一次真实测速；null = 内核里没有这个节点。 */
    fun urlTest(name: String, timeoutMs: Int): UrlTestResult? = Bridge.nativeUrlTest(name, timeoutMs)
        ?.let { Json.Default.decodeFromString(UrlTestResult.serializer(), it) }

    fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply {
        Bridge.nativeFetchAndValid(
            object : FetchCallback {
                override fun report(statusJson: String) {
                    reportStatus(
                        Json.Default.decodeFromString(
                            FetchStatus.serializer(),
                            statusJson,
                        ),
                    )
                }

                override fun complete(error: String?) {
                    if (error != null) {
                        completeExceptionally(ClashException(error))
                    } else {
                        complete(Unit)
                    }
                }
            },
            path.absolutePath,
            url,
            force,
        )
    }

    fun load(path: File): CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply {
        Bridge.nativeLoad(this, path.absolutePath)
    }

    fun queryOverride(slot: OverrideSlot): ConfigurationOverride = try {
        ConfigurationOverrideJson.decodeFromString(
            ConfigurationOverride.serializer(),
            Bridge.nativeReadOverride(slot.ordinal),
        )
    } catch (e: Exception) {
        ConfigurationOverride()
    }

    fun patchOverride(slot: OverrideSlot, configuration: ConfigurationOverride) {
        Bridge.nativeWriteOverride(
            slot.ordinal,
            ConfigurationOverrideJson.encodeToString(
                ConfigurationOverride.serializer(),
                configuration,
            ),
        )
    }

    fun clearOverride(slot: OverrideSlot) {
        Bridge.nativeClearOverride(slot.ordinal)
    }

    fun subscribeLogcat(): ReceiveChannel<LogMessage> = Channel<LogMessage>(32).apply {
        Bridge.nativeSubscribeLogcat(object : LogcatInterface {
            override fun received(jsonPayload: String): Boolean {
                // 单条畸形日志解析失败只丢弃该条，不结束订阅
                val message = runCatching {
                    Json.decodeFromString(LogMessage.serializer(), jsonPayload)
                }.getOrNull() ?: return false

                // 通道已关闭说明消费者已退订；通道满只丢弃该条，保持订阅
                return trySend(message).isClosed
            }
        })
    }

    fun setAgeSecretKey(key: String?) {
        Bridge.nativeSetAgeSecretKey(key)
    }

    fun genX25519KeyPair(): AgeKeyPair {
        return parseAgeKeyPair(checkNotNull(Bridge.nativeGenX25519KeyPair()))
    }

    fun genHybridKeyPair(): AgeKeyPair {
        return parseAgeKeyPair(checkNotNull(Bridge.nativeGenHybridKeyPair()))
    }

    fun veritySecretKeys(vararg secretKeys: String): Boolean {
        return Bridge.nativeVeritySecretKeys(secretKeys.firstOrNull() ?: "")
    }

    fun toPublicKeys(vararg secretKeys: String): List<String> {
        return Bridge.nativeToPublicKeys(secretKeys.firstOrNull() ?: "")
            ?.let { Json.Default.decodeFromString(ListSerializer(String.serializer()), it) }
            ?: emptyList()
    }

    fun verityPublicKeys(vararg publicKeys: String): Boolean {
        return Bridge.nativeVerityPublicKeys(publicKeys.firstOrNull() ?: "")
    }

    fun queryConfiguration(): UiConfiguration {
        return Json.Default.decodeFromString(
            UiConfiguration.serializer(),
            Bridge.nativeQueryConfiguration()
        )
    }

    fun queryProviders(): List<Provider> {
        val providers =
            Json.Default.decodeFromString(JsonArray.serializer(), Bridge.nativeQueryProviders())

        return List(providers.size) {
            Json.Default.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeUpdateProvider(this, type.toString(), name)
        }
    }
}
