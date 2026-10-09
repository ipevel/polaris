// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

object SubscriptionSanitizer {

    fun isValidSubscribeYaml(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.any { it < ' ' && it != '\n' && it != '\r' && it != '\t' }) return false
        val body = SanitizerYamlLines.stripBom(text)
        val head = body.trimStart()
        if (head.startsWith("<") || head.startsWith("{")) return false
        return body.lineSequence().any { SanitizerRules.SUBSCRIBE_ENTRY_KEY.containsMatchIn(it.trimStart()) }
    }

    /**
     * 清洗后的配置能否被内核加载。
     *
     * 内核遇到重名代理、缺 `name:` 的代理条目会拒绝加载**整份**配置。订阅更新时若把这种
     * 配置直接写进工作目录，用户就会在"更新订阅"之后全部节点不可用（旧配置已被覆盖，
     * 无从回滚）——所以写盘前必须先过这道闸门，不通过就保留现有配置。
     */
    fun isKernelLoadable(text: String): Boolean {
        if (text.isBlank()) return false
        val names = SanitizerNameDeduper.proxyNames(text)
        // 没有内联节点时，只有 proxy-providers 能提供节点；两者皆无则内核加载出空配置
        if (names.isEmpty()) return SanitizerNameDeduper.hasProxyProviders(text)
        if (SanitizerNameDeduper.itemsWithoutName(text) > 0) return false
        return SanitizerNameDeduper.duplicateProxyNames(text).isEmpty()
    }

    fun sanitize(
        text: String,
        domains: List<String>,
    ): String {
        if (text.isBlank()) return text
        val lines = SanitizerYamlLines.stripBom(text).lines().toMutableList()
        SanitizerYamlLines.dedentRootKeys(lines)
        SanitizerYamlLines.normalizeQuotedKeys(lines)

        val portsRewritten = runStep("zeroTopLevelPorts") { SanitizerNeutralizer.zeroTopLevelPorts(lines) }
        val controlNeutralized = runStep("neutralizeControlSurface") { SanitizerNeutralizer.neutralizeControlSurface(lines) }
        runStep("clearSubtitlePattern") { SanitizerNeutralizer.clearSubtitlePattern(lines) }
        // 重名节点会让内核拒绝加载整份配置，必须在交给内核前改成唯一名
        runStep("dedupeProxyNames") { SanitizerNameDeduper.dedupeProxyNames(lines) }
        // 面板把账号信息伪装成真代理塞进组里，内核 url-test 会真的选中它们（连上却不通）。
        // 必须在配置层剔除——只改 UI 没用，内核不知道 App 的排序。
        runStep("dropInfoPseudoProxies") { SanitizerInfoProxyDropper.dropInfoProxies(lines) }
        runStep("injectHealthCheckConfig") { SanitizerInjector.injectHealthCheckConfig(lines) }

        var ruleInjected = true
        SanitizerInjector.directDomains(domains).forEach { domain ->
            if (!runStep("injectDirectRule") { SanitizerInjector.injectDirectRule(lines, domain) }) ruleInjected = false
            runStep("injectFakeIpFilter") { SanitizerInjector.injectFakeIpFilter(lines, domain) }
        }

        if (!portsRewritten || !controlNeutralized || !ruleInjected) {
            AppLog.w(SanitizerRules.LOG_TAG, "清洗关键步骤失败，放弃输出")
            return ""
        }
        if (SanitizerNeutralizer.hasUnsafeResidue(lines)) {
            AppLog.w(SanitizerRules.LOG_TAG, "清洗后仍存在危险顶层键，放弃输出")
            return ""
        }
        return lines.joinToString("\n")
    }

    private fun runStep(
        step: String,
        block: () -> Unit,
    ): Boolean = try {
        block()
        true
    } catch (e: Throwable) {
        AppLog.w(SanitizerRules.LOG_TAG, "清洗步骤 $step 出错，跳过该步: ${e.javaClass.simpleName}: ${sanitizeLog(e.message ?: "Unknown")}")
        false
    }
}
