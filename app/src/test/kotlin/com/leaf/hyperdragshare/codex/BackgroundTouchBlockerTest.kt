package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BackgroundTouchBlockerTest {
    @Test
    fun rootServiceCommandUsesTheDynamicallyResolvedTransaction() {
        val command = BackgroundTouchBlocker.rootServiceCallCommand(123)

        assertTrue(command.contains("service call input 123"))
        assertFalse(command.contains("grep"))
    }

    @Test
    fun acceptsTheCurrentRomSuccessfulServiceCallParcel() {
        assertTrue(
            BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\t00000000    '....')",
            ),
        )
        assertTrue(
            BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: 00000000 00000000)",
            ),
        )
    }

    @Test
    fun rejectsServiceCallExceptionParcel() {
        assertFalse(
            BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: ffffffff 00000021 00650052)",
            ),
        )
    }

    @Test
    fun usesRootCancellationWhenPortalInputApisAreDenied() {
        val rootCalled = AtomicBoolean()
        val blocker = BackgroundTouchBlocker(
            inputContext(Any()),
            { _, _, _ ->
                throw SecurityException("Requires MONITOR_INPUT permission")
            },
            {
                rootCalled.set(true)
                true
            },
        )

        assertTrue(blocker.start())
        assertTrue(rootCalled.get())
        blocker.stop()
    }

    @Test
    fun stopDisposesInputMonitorAfterPilfering() {
        val inputManager = Any()
        val monitor = Any()
        val disposed = AtomicBoolean()
        val blocker = BackgroundTouchBlocker(
            inputContext(inputManager),
            { target, methodName, _ ->
                if (target === inputManager && "cancelCurrentTouch" == methodName) {
                    throw SecurityException("Requires MONITOR_INPUT permission")
                }
                if (target === inputManager && "monitorGestureInput" == methodName) {
                    monitor
                } else if (target === monitor && "pilferPointers" == methodName) {
                    null
                } else if (target === monitor && "dispose" == methodName) {
                    disposed.set(true)
                    null
                } else {
                    throw NoSuchMethodException(methodName)
                }
            },
            { false },
        )

        assertTrue(blocker.start())
        blocker.stop()

        assertTrue(disposed.get())
    }

    private companion object {
        private fun inputContext(inputManager: Any): Context {
            return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getApplicationContext(): Context = this

                override fun getDisplay(): Display {
                    throw UnsupportedOperationException("Application context has no display")
                }

                override fun getSystemService(name: String): Any? =
                    if (Context.INPUT_SERVICE == name) inputManager else null
            }
        }
    }
}
