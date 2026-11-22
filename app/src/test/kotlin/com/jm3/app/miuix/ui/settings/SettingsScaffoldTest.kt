package com.jm3.app.miuix.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 设置页共享骨架的线路诊断状态机。
 *
 * 四态全部由读回结果推导：没有读回结果是加载态、读回成功但没有可测域名是空态、
 * 读回失败且没有旧延迟行是错误态、有延迟行是内容态。失败时若仍留着上一次的延迟行，
 * 形态仍是内容态——与既有「失败不清空已有结果」的界面语义一致。
 * 重试入口只以「探活是否在途」为准，与页面按钮的禁用条件同源。
 */
class SettingsScaffoldTest {

    @Test
    fun 未读回时是加载态并显示占位行() {
        val state = SettingsDiagnostics()

        assertEquals(SettingsDiagnosticsPhase.Loading, state.phase)
        assertFalse(state.showsLatencyRows)
        assertNull(state.errorMessage)
        assertNull(state.usedDomain)
        assertTrue(state.canRetry)
    }

    @Test
    fun 读回成功但没有任何可测域名是空态() {
        val state = SettingsDiagnostics().succeeded(usedDomain = null, latency = emptyList())

        assertEquals(SettingsDiagnosticsPhase.Empty, state.phase)
        assertFalse(state.showsLatencyRows)
        assertNull(state.usedDomain)
        assertTrue(state.latency.isEmpty())
    }

    @Test
    fun 读回失败且没有旧延迟行时是错误态() {
        val state = SettingsDiagnostics().failed("网络超时，请稍后重试")

        assertEquals(SettingsDiagnosticsPhase.Failed, state.phase)
        assertEquals("网络超时，请稍后重试", state.errorMessage)
        assertFalse(state.showsLatencyRows)
        assertTrue(state.canRetry)
    }

    @Test
    fun 有延迟行时是内容态并逐行渲染() {
        val latency = listOf(DomainLatency("api.example.com", 42), DomainLatency("cdn.example.com", 118))
        val state = SettingsDiagnostics().succeeded(usedDomain = "api.example.com", latency = latency)

        assertEquals(SettingsDiagnosticsPhase.Content, state.phase)
        assertEquals(latency, state.latency)
        assertEquals("api.example.com", state.usedDomain)
        assertTrue(state.showsLatencyRows)
        assertNull(state.errorMessage)
    }

    @Test
    fun 失败时保留旧延迟行仍算内容态且错误单独给出() {
        val previous = SettingsDiagnostics().succeeded(
            usedDomain = "api.example.com",
            latency = listOf(DomainLatency("api.example.com", 42)),
        )

        val state = previous.failed("探活超时")

        assertEquals(SettingsDiagnosticsPhase.Content, state.phase)
        assertTrue(state.showsLatencyRows)
        assertEquals("探活超时", state.errorMessage)
        assertEquals(previous.latency, state.latency)
        assertEquals("api.example.com", state.usedDomain)
    }

    @Test
    fun 重试开始时拒绝重复发起() {
        val probing = SettingsDiagnostics().probingStarted()

        assertTrue(probing.probing)
        assertFalse(probing.canRetry)
        assertEquals(probing, probing.probingStarted())
    }

    @Test
    fun 探活期间保留旧的延迟行() {
        val content = SettingsDiagnostics().succeeded(
            usedDomain = "api.example.com",
            latency = listOf(DomainLatency("api.example.com", 42)),
        )

        val probing = content.probingStarted()

        assertEquals(SettingsDiagnosticsPhase.Content, probing.phase)
        assertEquals(content.latency, probing.latency)
    }

    @Test
    fun 探活结束只解锁按钮不动延迟与错误() {
        val probing = SettingsDiagnostics(
            settled = true,
            probing = true,
            errorMessage = "探活超时",
            usedDomain = "api.example.com",
            latency = listOf(DomainLatency("api.example.com", 42)),
        )

        val finished = probing.probingFinished()

        assertFalse(finished.probing)
        assertTrue(finished.canRetry)
        assertEquals(probing.latency, finished.latency)
        assertEquals("探活超时", finished.errorMessage)
    }

    @Test
    fun 重新读回清掉错误并回到加载态() {
        val failed = SettingsDiagnostics().failed("网络超时，请稍后重试")

        val reloading = failed.reloading()

        assertEquals(SettingsDiagnosticsPhase.Loading, reloading.phase)
        assertNull(reloading.errorMessage)
        assertFalse(reloading.settled)
    }

    @Test
    fun 重新读回不清掉已有延迟行() {
        val content = SettingsDiagnostics().succeeded(
            usedDomain = "api.example.com",
            latency = listOf(DomainLatency("api.example.com", 42)),
        )

        val reloading = content.reloading()

        assertEquals(SettingsDiagnosticsPhase.Content, reloading.phase)
        assertEquals(content.latency, reloading.latency)
        assertNull(reloading.errorMessage)
    }

    @Test
    fun `sortDomainLatencies 将可用节点按毫秒升序排前且超时负值沉底`() {
        val sorted = sortDomainLatencies(
            mapOf(
                "dead1.example" to -1,
                "slow.example" to 210,
                "fast.example" to 38,
                "dead2.example" to -1,
            ),
        )

        assertEquals(
            listOf(
                DomainLatency("fast.example", 38),
                DomainLatency("slow.example", 210),
                DomainLatency("dead1.example", -1),
                DomainLatency("dead2.example", -1),
            ),
            sorted,
        )
        assertTrue(sorted[0].isReachable)
        assertFalse(sorted[2].isReachable)
    }

    @Test
    fun `buildLineCandidates 合并去重候选节点并按延迟状态稳定排序`() {
        val nodes = buildLineCandidates(
            candidates = listOf("official1.example", "official2.example", "custom.example"),
            customDomains = listOf("custom.example"),
            selectedDomain = null,
            latency = listOf(
                DomainLatency("official1.example", -1),
                DomainLatency("official2.example", 65),
            ),
        )

        assertEquals(
            listOf(
                LineNodeOption("official2.example", isCustom = false, latencyMs = 65),
                LineNodeOption("custom.example", isCustom = true, latencyMs = null),
                LineNodeOption("official1.example", isCustom = false, latencyMs = -1),
            ),
            nodes,
        )
    }

    @Test
    fun `settingsDiagnosticLines 呈现线路模式与超时文案`() {
        val lines = settingsDiagnosticLines(
            packageName = "com.jm3.app",
            versionName = "2.0.0",
            versionCode = 20000L,
            manufacturer = "Google",
            model = "Pixel",
            release = "15",
            sdkInt = 35,
            selectedDomain = "cdn1.example",
            usedDomain = "cdn1.example",
            activeDomains = "",
            latency = listOf(DomainLatency("cdn1.example", 42), DomainLatency("dead.example", -1)),
            paginationMode = com.jm3.core.settings.PaginationMode.INFINITE,
            themeMode = com.jm3.core.settings.ThemeMode.SYSTEM,
            dynamicColorEffective = false,
        ).associate { it.label to it.value }

        assertEquals("手动固定（cdn1.example）", lines["线路模式"])
        assertEquals("cdn1.example", lines["当前生效线路"])
        assertEquals("cdn1.example · 42 ms\ndead.example · 超时", lines["线路测速"])
    }
}
