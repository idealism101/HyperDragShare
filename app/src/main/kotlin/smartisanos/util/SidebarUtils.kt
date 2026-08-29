package smartisanos.util

import android.content.Context
import android.view.View

/**
 * DragShare has no Smartisan sidebar integration; keep the imported chip view's optional path inert.
 */
object SidebarUtils {
    @JvmStatic
    fun isSidebarShowing(context: Context?): Boolean = false

    @JvmStatic
    fun dragText(source: View?, context: Context?, text: String?) {
        // The sidebar is unavailable in DragShare.
    }
}
