package com.leaf.hyperdragshare.codex

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

/**
 * The libxposed API 102 entry point, named in `META-INF/xposed/java_init.list`. The declared scope
 * is Taplus only, and the package name is checked again here so a wider scope cannot inject
 * anything else.
 */
class DragShareModule : XposedModule() {
    /** One portal process loads the package once, but a hot reload may deliver it again. */
    private val installedPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        DragShareLog.i(
            TAG,
            "module loaded process=" + param.processName
                + " framework=" + frameworkName + " " + frameworkVersion
                + " api=" + apiVersion,
        )
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TAPLUS_PACKAGE) {
            return
        }
        if (!installedPackages.add(param.packageName)) {
            return
        }
        PortalHooks.install(this, param.defaultClassLoader)
    }

    companion object {
        const val TAPLUS_PACKAGE = "com.miui.contentextension"

        private const val TAG = "DragShare/Module"
    }
}
