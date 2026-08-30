package com.leaf.hyperdragshare.codex

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun aDeniedPortalRootGrantCountsAsAnAnswerInsteadOfAMissingReport() {
        val context = RuntimeEnvironment.getApplication()
        ModuleActivation.activationPreferences(context).edit().clear().commit()
        assertFalse(ModuleActivation.hasCurrentBuildPortalRootReport(context))

        val denied = Bundle()
        denied.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
        denied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false)
        ModuleActivation.recordInjected(context, denied)

        // Only the portal can probe its own grant, so a denial ends the wait instead of being
        // retried over root until the report timeout expires.
        assertTrue(ModuleActivation.hasCurrentBuildPortalRootReport(context))
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context))
    }

    @Test
    fun anInjectionReportWithoutTheRootFlagLeavesTheRootQuestionOpen() {
        val context = RuntimeEnvironment.getApplication()
        ModuleActivation.activationPreferences(context).edit().clear().commit()

        val injectionOnly = Bundle()
        injectionOnly.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong(),
        )
        ModuleActivation.recordInjected(context, injectionOnly)

        assertTrue(ModuleActivation.isCurrentBuildInjected(context))
        assertFalse(ModuleActivation.hasCurrentBuildPortalRootReport(context))
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
    fun aReportFromAnotherBuildNamesTheProcessThatStillRunsIt() {
        val context = RuntimeEnvironment.getApplication()
        ModuleActivation.activationPreferences(context).edit().clear().commit()
        assertNull(ModuleActivation.staleReportPid(context))

        val older = Bundle()
        older.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE - 1L)
        older.putInt(ModuleActivation.EXTRA_PORTAL_PID, 4321)
        ModuleActivation.recordInjected(context, older)

        // The report proves nothing about the running build, but it does say which process to look
        // for: finding it alive is the difference between 旧版本仍在运行 and 未注入.
        assertFalse(ModuleActivation.isCurrentBuildInjected(context))
        assertEquals(4321, ModuleActivation.staleReportPid(context))

        val current = Bundle()
        current.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong(),
        )
        current.putInt(ModuleActivation.EXTRA_PORTAL_PID, 4322)
        ModuleActivation.recordInjected(context, current)
        assertTrue(ModuleActivation.isCurrentBuildInjected(context))
        assertNull(ModuleActivation.staleReportPid(context))
    }

    @Test
    fun everyRootReportMovesTheSequenceSoAFreshAnswerIsRecognisable() {
        val context = RuntimeEnvironment.getApplication()
        ModuleActivation.activationPreferences(context).edit().clear().commit()
        assertEquals(0L, ModuleActivation.portalRootReportSequence(context))

        ModuleActivation.recordInjected(context, rootReport(true))
        val first = ModuleActivation.portalRootReportSequence(context)
        assertTrue(first > 0L)

        // The same answer still counts as a new report: a re-probe is awaited by the round trip
        // having happened, not by the value having changed.
        ModuleActivation.recordInjected(context, rootReport(true))
        assertEquals(first + 1L, ModuleActivation.portalRootReportSequence(context))

        // An injection report on its own says nothing about the grant.
        val injectionOnly = Bundle()
        injectionOnly.putLong(
            ModuleActivation.EXTRA_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong(),
        )
        ModuleActivation.recordInjected(context, injectionOnly)
        assertEquals(first + 1L, ModuleActivation.portalRootReportSequence(context))
    }

    @Test
    fun anUninstalledPortalHasNoUidToProbe() {
        // Robolectric knows no portal package, which is the same shape as an uninstalled portal:
        // the direct probe must say "unknown" instead of "denied" so the report still decides.
        assertNull(ModuleActivation.probePortalRootGrant(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun theSpawnCommandOnlyReadsThePortalsOwnProvider() {
        val command = ModuleActivation.portalSpawnCommand()
        // Publishing a provider runs the portal Application, which is where the hook reports the
        // loaded build. Nothing else may be touched to get there.
        assertTrue(command.startsWith("content query --uri content://"))
        assertTrue(command.contains("com.miui.contentextension.provider."))
        assertFalse(command.contains("com.miui.contentcatcher"))
        assertFalse(command.contains("force-stop"))
        assertFalse(command.contains("am start"))
    }

    @Test
    fun blacklistCommandStartsOnlyTheRequestedPortalActivity() {
        assertEquals(
            "am start --user current -n com.miui.contentextension/" +
                "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity",
            ModuleActivation.portalBlacklistCommand(),
        )
    }

    private companion object {
        private fun rootReport(granted: Boolean): Bundle {
            val extras = Bundle()
            extras.putLong(
                ModuleActivation.EXTRA_VERSION_CODE,
                BuildConfig.VERSION_CODE.toLong(),
            )
            extras.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, granted)
            return extras
        }
    }
}
