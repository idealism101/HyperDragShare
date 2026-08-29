package com.leaf.hyperdragshare.codex

import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The framework half of activation detection. A modern framework hands every module process a
 * binder, so the module app can ask the framework itself whether it is enabled, which packages it
 * may hook and which portal process loaded which build. That answer needs neither root nor a
 * portal cold start, which is what the [ShareImageProvider] handshake had to pay for.
 *
 * This is the only class in the public runtime allowed to touch `io.github.libxposed.service`; the
 * hooking API stays behind [PortalHooks].
 */
internal object XposedServiceStatus {
    /** The framework binder is delivered while the process starts, so it is there or it is not. */
    const val BIND_TIMEOUT_MS = 1_500L

    private const val LOG_TAG = "DragShare/XposedService"

    private val mutableService = MutableStateFlow<XposedService?>(null)

    /** The bound framework service, or null while no framework answered this process. */
    val service: StateFlow<XposedService?> = mutableService.asStateFlow()

    private val listener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            mutableService.value = service
            DragShareLog.i(LOG_TAG, "framework service bound " + describe(service))
        }

        override fun onServiceDied(service: XposedService) {
            mutableService.value = null
            DragShareLog.w(LOG_TAG, "framework service died")
        }
    }

    private var registered = false

    /**
     * Registers the single framework listener for this process. A binder that arrived before this
     * call is replayed by the helper, so the module app may register whenever it is convenient.
     */
    @Synchronized
    fun register() {
        if (registered) {
            return
        }
        registered = true
        try {
            XposedServiceHelper.registerListener(listener)
        } catch (error: Throwable) {
            DragShareLog.w(LOG_TAG, "unable to register the framework listener", error)
        }
    }

    /** Waits briefly for the binder so a detection started at app launch does not miss it. */
    suspend fun awaitService(timeoutMs: Long = BIND_TIMEOUT_MS): XposedService? =
        service.value ?: withTimeoutOrNull(timeoutMs) { service.filterNotNull().first() }

    /** The framework name, version and API level, or null when the service stopped answering. */
    fun frameworkLabel(service: XposedService): String? = try {
        val name = service.frameworkName.takeIf { it.isNotBlank() } ?: "未知框架"
        val version = service.frameworkVersion.takeIf { it.isNotBlank() }
        if (version == null) {
            "$name · API ${service.apiVersion}"
        } else {
            "$name $version · API ${service.apiVersion}"
        }
    } catch (error: Throwable) {
        DragShareLog.w(LOG_TAG, "unable to read the framework identity", error)
        null
    }

    /**
     * Whether the module scope covers the portal. Null means the framework refused the query, not
     * that the portal is missing from the scope.
     */
    fun scopeIncludesPortal(service: XposedService, portalPackage: String): Boolean? = try {
        val scope = service.scope
        DragShareLog.d(LOG_TAG, "module scope=$scope")
        scope.contains(portalPackage)
    } catch (error: Throwable) {
        DragShareLog.w(LOG_TAG, "unable to read the module scope", error)
        null
    }

    /**
     * Which build of this module the live portal processes loaded. Null means the framework did
     * not answer, so the caller has to fall back to the portal handshake.
     */
    fun portalInjection(service: XposedService, portalPackage: String): PortalInjectionState? {
        val targets = try {
            service.runningTargets
        } catch (error: Throwable) {
            DragShareLog.w(LOG_TAG, "unable to read the running targets", error)
            return null
        }
        val portalTargets = targets.filter { it.belongsTo(portalPackage) }
        DragShareLog.d(
            LOG_TAG,
            "portal targets=" + portalTargets.joinToString { describe(it) }
                + " otherTargets=" + targets.size.minus(portalTargets.size),
        )
        return when {
            portalTargets.isEmpty() -> PortalInjectionState.Stopped
            portalTargets.any { it.loadedVersionCode == BuildConfig.VERSION_CODE.toLong() } ->
                PortalInjectionState.Injected
            else -> PortalInjectionState.Stale
        }
    }

    /** A portal target is the portal process itself or one of its private sub processes. */
    private fun HookedTarget.belongsTo(portalPackage: String): Boolean =
        processName == portalPackage || processName.startsWith("$portalPackage:")

    private fun describe(target: HookedTarget): String =
        "${target.processName}/pid=${target.pid} state=${target.state}" +
            " module=${target.loadedVersionCode}"

    private fun describe(service: XposedService): String =
        frameworkLabel(service) ?: "identity unavailable"
}
