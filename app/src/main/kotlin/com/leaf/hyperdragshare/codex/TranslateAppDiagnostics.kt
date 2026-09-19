package com.leaf.hyperdragshare.codex

import java.util.Collections

/**
 * 「翻译应用打不开 / 不自动填字」用的入口诊断。
 *
 * 目的：把目标应用在系统里**真实注册**的入口（Activity + intent-filter 的 action/scheme/mime）
 * 写进日志，用户只需回传日志即可定位，无需在电脑上跑 adb/dumpsys。
 *
 * 实现：复用模块已有的 root 通道执行 `dumpsys package <pkg>`，过滤出关键行后按行数/字符数限流写入，
 * 并且**每个进程对每个包只跑一次**，避免反复刷日志。
 *
 * 注意：`dumpsys` 最长等 5 秒，因此这里**另起后台线程**，绝不阻塞主线程。
 */
internal object TranslateAppDiagnostics {
    private const val TAG = "DragShare/Translate"
    private const val MAX_LINES = 60
    private const val MAX_CHARS = 2_000

    /** 只在 `dumpsys` 输出里挑这些关键行。 */
    private val KEY_LINE_HINTS = listOf(
        "Activity Resolver Table:",
        "android.intent.action.",
        "scheme",
        "mimeType",
        "Category:",
        "Component{",
        "IntentFilter",
    )

    private val loggedPackages = Collections.synchronizedSet(HashSet<String>())

    /** 非阻塞：后台线程跑诊断，同一包名每个进程只跑一次。 */
    fun logEntryPoints(packageName: String) {
        if (packageName.isEmpty() || !loggedPackages.add(packageName)) {
            return
        }
        val worker = Thread({ dumpEntryPoints(packageName) }, "drag-share-translate-entry")
        worker.isDaemon = true
        worker.start()
    }

    private fun dumpEntryPoints(packageName: String) {
        try {
            val dump = DragShareDiagnostics.runRootCommand("dumpsys package $packageName")
            val lines = dump.lineSequence()
                .map { it.trim() }
                .filter { line ->
                    KEY_LINE_HINTS.any { hint -> line.contains(hint, ignoreCase = true) }
                }
                .take(MAX_LINES)
                .toList()
            if (lines.isEmpty()) {
                DragShareLog.i(
                    TAG,
                    "entry dump empty package=" + packageName + " (root 不可用或无匹配行)",
                )
                return
            }
            val text = lines.joinToString("\n").let { joined ->
                if (joined.length > MAX_CHARS) {
                    joined.substring(0, MAX_CHARS) + "\n[truncated]"
                } else {
                    joined
                }
            }
            DragShareLog.i(TAG, "entry dump package=" + packageName + ":\n" + text)
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "entry dump failed package=" + packageName, error)
        }
    }
}
