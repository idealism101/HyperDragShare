package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationChecksTest {
    @Test
    fun portalChecksKeepTheDocumentedSourceOrder() {
        val titles = activationChecks(accessibilityMode = false).map { it.title }
        assertEquals(
            listOf("Root 权限", "传送门 Root 权限", "LSPosed 注入", "传送门版本", "内容获取方式", "模块版本"),
            titles,
        )
    }

    @Test
    fun accessibilityChecksKeepTheDocumentedSourceOrder() {
        val titles = activationChecks(accessibilityMode = true).map { it.title }
        assertEquals(
            listOf("Root 权限", "无障碍服务", "无障碍连接", "Root 输入", "内容获取方式", "模块版本"),
            titles,
        )
    }

    @Test
    fun pendingChecksReadAsCheckingWithoutBeingMarkedAsFailed() {
        val checks = activationChecks(accessibilityMode = false)
        val pending = checks.filter { it.content == "检测中" }
        assertEquals(4, pending.size)
        assertTrue(pending.none { it.failed })
    }

    @Test
    fun aStoppedPortalIsNotReportedAsAnUninjectedModule() {
        val stopped = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            portalInjection = PortalInjectionState.Stopped,
            portalRootGranted = false,
            portalInstalled = true,
            portalVersion = "4.2.1 (4020001)",
        )
        val injection = stopped.first { it.title == "LSPosed 注入" }
        assertEquals("传送门未运行", injection.content)
        assertTrue(injection.failed)
        assertEquals("4.2.1 (4020001)", stopped.first { it.title == "传送门版本" }.content)

        val injected = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            portalInjection = PortalInjectionState.Injected,
            portalRootGranted = true,
            portalInstalled = true,
        )
        val confirmed = injected.first { it.title == "LSPosed 注入" }
        assertEquals("已注入当前版本", confirmed.content)
        assertFalse(confirmed.failed)
        assertEquals("已安装", injected.first { it.title == "传送门版本" }.content)
    }

    @Test
    fun theStatusCardNamesTheActivationSourceForEveryLevel() {
        assertEquals("正在检测", ActivationSnapshot().activationMethod)
        assertEquals(
            "ROOT",
            ActivationSnapshot(level = ActivationLevel.Inactive).activationMethod,
        )
        assertEquals(
            "ROOT · LSPosed",
            ActivationSnapshot(level = ActivationLevel.Partial).activationMethod,
        )
        assertEquals(
            "ROOT · 无障碍",
            ActivationSnapshot(
                level = ActivationLevel.Active,
                accessibilityMode = true,
            ).activationMethod,
        )
    }

    @Test
    fun theFrameworkRowsOnlyExistWhenTheFrameworkAnswered() {
        val answered = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            frameworkLabel = "LSPosed 1.10.2 · API 102",
            scopeIncludesPortal = true,
            portalInjection = PortalInjectionState.Injected,
            portalRootGranted = true,
            portalInstalled = true,
        )
        assertEquals(
            listOf(
                "Root 权限",
                "LSPosed 服务",
                "模块作用域",
                "传送门 Root 权限",
                "LSPosed 注入",
                "传送门版本",
                "内容获取方式",
                "模块版本",
            ),
            answered.map { it.title },
        )
        val framework = answered.first { it.title == "LSPosed 服务" }
        assertEquals("LSPosed 1.10.2 · API 102", framework.content)
        assertFalse(framework.failed)
        assertEquals("已包含传送门", answered.first { it.title == "模块作用域" }.content)

        // No binder reached the process, so the handshake rows answer alone and neither framework
        // row is shown as a failure.
        val silent = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            portalInjection = PortalInjectionState.Injected,
            portalRootGranted = true,
            portalInstalled = true,
        )
        assertTrue(silent.none { it.title == "LSPosed 服务" || it.title == "模块作用域" })
    }

    @Test
    fun aPortalOutsideTheScopeAndAStalePortalNameTheirOwnBlocker() {
        val outOfScope = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            frameworkLabel = "LSPosed 1.10.2 · API 102",
            scopeIncludesPortal = false,
            portalInjection = PortalInjectionState.NotInjected,
            portalRootGranted = false,
            portalInstalled = true,
        )
        val scope = outOfScope.first { it.title == "模块作用域" }
        assertEquals("缺少传送门", scope.content)
        assertTrue(scope.failed)

        val stale = activationChecks(
            accessibilityMode = false,
            rootGranted = true,
            frameworkLabel = "LSPosed 1.10.2 · API 102",
            scopeIncludesPortal = true,
            portalInjection = PortalInjectionState.Stale,
            portalRootGranted = false,
            portalInstalled = true,
        )
        val injection = stale.first { it.title == "LSPosed 注入" }
        assertEquals("旧版本仍在运行", injection.content)
        assertTrue(injection.failed)
    }
}
