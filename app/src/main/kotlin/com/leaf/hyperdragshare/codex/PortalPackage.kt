package com.leaf.hyperdragshare.codex

/**
 * The one hook target, shared by the module process and the Xposed-facing classes so neither has
 * to reach into the other. LSPosed scope is pinned to this package alone.
 */
internal const val PORTAL_PACKAGE = "com.miui.contentextension"
