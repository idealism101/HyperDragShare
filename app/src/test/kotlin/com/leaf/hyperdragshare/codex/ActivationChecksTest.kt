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
}
