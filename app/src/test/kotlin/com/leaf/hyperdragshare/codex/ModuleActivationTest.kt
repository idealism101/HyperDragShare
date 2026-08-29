package com.leaf.hyperdragshare.codex

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModuleActivationTest {
    @Test
    fun injectionAndPortalRootReportsMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE.toLong()))
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L))

        val context = RuntimeEnvironment.getApplication()
        val rootDenied = Bundle()
        rootDenied.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong(),
        )
        rootDenied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false)
        ModuleActivation.recordInjected(context, rootDenied)
        assertTrue(ModuleActivation.isCurrentBuildInjected(context))
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context))

        val rootGranted = Bundle()
        rootGranted.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong(),
        )
        rootGranted.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true)
        ModuleActivation.recordInjected(context, rootGranted)
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context))

        val staleRootReport = Bundle()
        staleRootReport.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE - 1L,
        )
        staleRootReport.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false)
        ModuleActivation.recordInjected(context, staleRootReport)
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context))
    }

    @Test
    fun handshakeOnlyStartsThePortalService() {
        val command = ModuleActivation.portalHandshakeCommand()
        assertTrue(command.contains("com.miui.contentextension/"))
        assertTrue(command.contains("TextContentExtensionService"))
        assertFalse(command.contains("com.miui.contentcatcher"))
        assertFalse(command.contains("force-stop"))
    }

    @Test
    fun blacklistCommandStartsOnlyTheRequestedPortalActivity() {
        assertEquals(
            "am start --user current -n com.miui.contentextension/" +
                "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity",
            ModuleActivation.portalBlacklistCommand(),
        )
    }
}
