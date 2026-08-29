package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The portal package name; the public runtime must not reach into the Xposed-facing classes. */
internal const val PORTAL_PACKAGE = "com.miui.contentextension"

/** A cold portal has to be started over root before its hook can answer, which takes seconds. */
private const val PORTAL_INJECTION_TIMEOUT_MS = 12_000L

/** The root probe is a second report from a portal that already answered, so it comes sooner. */
private const val PORTAL_ROOT_REPORT_TIMEOUT_MS = 6_000L

private const val LOG_TAG = "DragShare/Activation"

internal enum class ActivationLevel { Checking, Inactive, Partial, Active }

/** One row of the detection card: the checked item plus the state it resolved to. */
internal data class ActivationCheck(
    val title: String,
    val content: String,
    val failed: Boolean = false,
)

/** Why the portal did not confirm the running build, so the row can name the actual blocker. */
internal enum class PortalInjectionState { Injected, Stopped, NotInjected }

internal data class ActivationSnapshot(
    val level: ActivationLevel = ActivationLevel.Checking,
    val accessibilityMode: Boolean = false,
    val checks: List<ActivationCheck> = emptyList(),
) {
    /** Where the activation comes from. This is the source line, not a hint message. */
    val activationMethod: String
        get() = when (level) {
            ActivationLevel.Checking -> "正在检测"
            ActivationLevel.Inactive -> "ROOT"
            else -> if (accessibilityMode) "ROOT · 无障碍" else "ROOT · LSPosed"
        }
}

internal fun portalVersionText(context: Context): String? = try {
    val info = context.packageManager.getPackageInfo(PORTAL_PACKAGE, 0)
    val versionName = info.versionName?.takeIf { it.isNotBlank() }
    if (versionName != null) {
        "$versionName (${info.longVersionCode})"
    } else {
        info.longVersionCode.toString()
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

internal fun activationChecks(
    accessibilityMode: Boolean,
    rootGranted: Boolean? = null,
    portalInjection: PortalInjectionState? = null,
    portalRootGranted: Boolean? = null,
    serviceEnabled: Boolean? = null,
    serviceConnected: Boolean? = null,
    rootInputReady: Boolean? = null,
    portalInstalled: Boolean? = null,
    portalVersion: String? = null,
): List<ActivationCheck> {
    fun check(title: String, value: Boolean?, ok: String, fail: String) = ActivationCheck(
        title = title,
        content = when (value) {
            null -> "检测中"
            true -> ok
            false -> fail
        },
        failed = value == false,
    )
    return buildList {
        add(check("Root 权限", rootGranted, "已授权", "未授权"))
        if (accessibilityMode) {
            add(check("无障碍服务", serviceEnabled, "已启用", "未启用"))
            add(check("无障碍连接", serviceConnected, "已连接", "未连接"))
            add(check("Root 输入", rootInputReady, "已就绪", "未就绪"))
        } else {
            add(check("传送门 Root 权限", portalRootGranted, "已授权", "不可用"))
            add(
                ActivationCheck(
                    title = "LSPosed 注入",
                    content = when (portalInjection) {
                        null -> "检测中"
                        PortalInjectionState.Injected -> "已注入当前版本"
                        PortalInjectionState.Stopped -> "传送门未运行"
                        PortalInjectionState.NotInjected -> "未注入"
                    },
                    failed = portalInjection != null
                        && portalInjection != PortalInjectionState.Injected,
                ),
            )
            add(
                ActivationCheck(
                    title = "传送门版本",
                    content = when (portalInstalled) {
                        null -> "检测中"
                        true -> portalVersion ?: "已安装"
                        false -> "未安装"
                    },
                    failed = portalInstalled == false,
                ),
            )
        }
        add(ActivationCheck("内容获取方式", if (accessibilityMode) "无障碍" else "传送门"))
        add(ActivationCheck("模块版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
    }
}

/**
 * Process wide activation detection. The result outlives the composition that shows it, so
 * switching tabs or popping a sub page never restarts a probe; only a resumed activity, a
 * content capture mode change or an explicit tap does.
 */
internal object ActivationMonitor {
    private val mutableSnapshot = MutableStateFlow(ActivationSnapshot())
    val snapshot: StateFlow<ActivationSnapshot> = mutableSnapshot.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var detection: Job? = null
    private var detectedMode: Boolean? = null

    /** Detects once per mode; a probe that is already running or already finished is reused. */
    fun detect(context: Context, accessibilityMode: Boolean) {
        if (detectedMode == accessibilityMode) {
            return
        }
        restart(context, accessibilityMode)
    }

    /** Re-runs the probe for the current mode; the previous result stays visible meanwhile. */
    fun refresh(context: Context, accessibilityMode: Boolean) {
        if (detection?.isActive == true && detectedMode == accessibilityMode) {
            return
        }
        restart(context, accessibilityMode)
    }

    private fun restart(context: Context, accessibilityMode: Boolean) {
        val appContext = context.applicationContext ?: context
        detection?.cancel()
        detectedMode = accessibilityMode
        detection = scope.launch { runDetection(appContext, accessibilityMode) }
    }

    private suspend fun runDetection(context: Context, accessibilityMode: Boolean) {
        val portalVersion = if (accessibilityMode) {
            null
        } else {
            withContext(Dispatchers.IO) { portalVersionText(context) }
        }
        val portalInstalled = if (accessibilityMode) null else portalVersion != null

        fun publish(
            level: ActivationLevel,
            rootGranted: Boolean? = null,
            portalInjection: PortalInjectionState? = null,
            portalRootGranted: Boolean? = null,
            serviceEnabled: Boolean? = null,
            serviceConnected: Boolean? = null,
            rootInputReady: Boolean? = null,
        ) {
            mutableSnapshot.value = ActivationSnapshot(
                level = level,
                accessibilityMode = accessibilityMode,
                checks = activationChecks(
                    accessibilityMode = accessibilityMode,
                    rootGranted = rootGranted,
                    portalInjection = portalInjection,
                    portalRootGranted = portalRootGranted,
                    serviceEnabled = serviceEnabled,
                    serviceConnected = serviceConnected,
                    rootInputReady = rootInputReady,
                    portalInstalled = portalInstalled,
                    portalVersion = portalVersion,
                ),
            )
        }

        // Re-detection keeps the last result on screen; only a first run or a mode switch
        // falls back to the neutral checking card.
        val previous = mutableSnapshot.value
        if (previous.checks.isEmpty() || previous.accessibilityMode != accessibilityMode) {
            publish(ActivationLevel.Checking)
        }

        val rootGranted = withContext(Dispatchers.IO) { ModuleActivation.hasRootAccess() }
        if (!rootGranted) {
            publish(ActivationLevel.Inactive, rootGranted = false)
            return
        }
        if (accessibilityMode) {
            val serviceEnabled = withContext(Dispatchers.IO) {
                AccessibilityRuntimeStatus.isServiceEnabled(context)
            }
            val serviceConnected = AccessibilityRuntimeStatus.isConnected()
            val rootInputReady = AccessibilityRuntimeStatus.isRootInputReady()
            publish(
                level = if (serviceEnabled && serviceConnected && rootInputReady) {
                    ActivationLevel.Active
                } else {
                    ActivationLevel.Partial
                },
                rootGranted = true,
                serviceEnabled = serviceEnabled,
                serviceConnected = serviceConnected,
                rootInputReady = rootInputReady,
            )
            return
        }

        var injected = withContext(Dispatchers.IO) {
            ModuleActivation.isCurrentBuildInjected(context)
        }
        var portalRootGranted = ModuleActivation.isCurrentBuildPortalRootGranted(context)
        var portalStopped = false
        if (!injected || !portalRootGranted) {
            // The portal only carries the hook while one of its processes lives, so a stopped
            // portal is started over root instead of being reported as not injected.
            withContext(Dispatchers.IO) { ModuleActivation.requestPortalInjectionHandshake() }
            injected = awaitPortalReport(context, PORTAL_INJECTION_TIMEOUT_MS) {
                ModuleActivation.isCurrentBuildInjected(context)
            }
            if (injected) {
                // The portal probes its own root grant on a second thread and answers again.
                portalRootGranted = awaitPortalReport(context, PORTAL_ROOT_REPORT_TIMEOUT_MS) {
                    ModuleActivation.isCurrentBuildPortalRootGranted(context)
                }
            } else {
                portalStopped = portalInstalled == true
                    && !withContext(Dispatchers.IO) { ModuleActivation.isPortalRunning() }
            }
        }
        publish(
            level = if (injected && portalRootGranted) {
                ActivationLevel.Active
            } else {
                ActivationLevel.Partial
            },
            rootGranted = true,
            portalInjection = when {
                injected -> PortalInjectionState.Injected
                portalStopped -> PortalInjectionState.Stopped
                else -> PortalInjectionState.NotInjected
            },
            portalRootGranted = portalRootGranted,
        )
        DragShareLog.i(
            LOG_TAG,
            "portal activation injected=" + injected
                + " portalRoot=" + portalRootGranted
                + " portalRunning=" + !portalStopped,
        )
    }

    /**
     * Waits for the injected portal to answer over [ShareImageProvider]. Both reports land in
     * this process, so the stored report is observed instead of being polled.
     */
    private suspend fun awaitPortalReport(
        context: Context,
        timeoutMs: Long,
        satisfied: () -> Boolean,
    ): Boolean {
        if (satisfied()) {
            return true
        }
        val preferences = ModuleActivation.activationPreferences(context)
        val reported = CompletableDeferred<Unit>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (satisfied()) {
                reported.complete(Unit)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return try {
            withTimeoutOrNull(timeoutMs) { reported.await() } != null
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}

/**
 * Observes [ActivationMonitor]. Detection starts on the first composition and refreshes when the
 * activity is started again, never when the home tab is merely swiped back into the pager.
 */
@Composable
internal fun rememberActivationSnapshot(
    context: Context,
    contentCaptureMode: Int,
): State<ActivationSnapshot> {
    val accessibilityMode = contentCaptureMode == DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY
    val lifecycleOwner = LocalView.current.context as? LifecycleOwner

    LaunchedEffect(accessibilityMode) {
        ActivationMonitor.detect(context, accessibilityMode)
    }
    DisposableEffect(lifecycleOwner, accessibilityMode) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            // Registering on a started lifecycle replays ON_START at once. That replay is the
            // page being composed again, not the user coming back to the app, so it is dropped.
            var replayedStart = lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            val observer = LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_START) {
                    return@LifecycleEventObserver
                }
                if (replayedStart) {
                    replayedStart = false
                } else {
                    ActivationMonitor.refresh(context, accessibilityMode)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    return ActivationMonitor.snapshot.collectAsState()
}
