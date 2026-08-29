package com.leaf.hyperdragshare.codex

import android.content.Context

/**
 * Values consumed by the imported BigBang word-chip core. DragShare does not expose the
 * reference app's separate search-settings surface, so the original defaults are retained.
 */
class BigBangSettings private constructor() {
    val gapRowHeightPercent: Int
        get() = 100

    val webSearchType: Int
        get() = BoomActionHandler.SEARCH_WEB

    val dictSearchType: Int
        get() = BoomActionHandler.SEARCH_DICTIONARY

    companion object {
        private val INSTANCE = BigBangSettings()

        @JvmStatic
        fun get(context: Context?): BigBangSettings = INSTANCE
    }
}
