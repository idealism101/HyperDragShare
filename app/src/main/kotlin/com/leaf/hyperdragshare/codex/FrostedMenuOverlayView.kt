package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 磨砂菜单内容：**一整块深色圆角背板**（上区 = 5 个功能 chip，下区 = 4 列 × 5 行 App 网格，
 * 底部 ‹ 圆点 › 翻页）。背板本身的模糊由承载它的 [FrostedMenuWindow] 通过窗口背景模糊完成，
 * 这里只负责画深色半透明面板与内容。
 */
internal enum class FrostedFunction { SEGMENT, SAVE, SHARE, TRANSLATE, COPY }

internal class FrostedMenuOverlayView(
    context: Context,
    targets: List<ShareTarget>,
    private val iconResolver: (ShareTarget) -> Drawable?,
    private val onFunction: (FrostedFunction) -> Unit,
    private val onTarget: (ShareTarget) -> Unit,
    private val plateAlphaPercent: Int,
    private val plateDarknessPercent: Int,
) : FrameLayout(context) {

    companion object {
        // 背板上的浅色内容（暗色背板）
        private val CHIP_BG_DARK = 0x33FFFFFFL.toInt()
        private val CHIP_TEXT_DARK = 0xFFFFFFFFL.toInt()
        private val LABEL_DARK = 0xFFEDEFF2L.toInt()
        private val NAV_DARK = 0xFFDADCE0L.toInt()
        private val DOT_ON_DARK = 0xFFFFFFFFL.toInt()
        private val DOT_OFF_DARK = 0x59FFFFFFL.toInt()
        // 背板上的深色内容（背板调得很亮时自动切换，避免白底白字看不清）
        private val CHIP_BG_LIGHT = 0x1F000000L.toInt()
        private val CHIP_TEXT_LIGHT = 0xFF1F1F1FL.toInt()
        private val LABEL_LIGHT = 0xFF3C4043L.toInt()
        private val NAV_LIGHT = 0xFF5F6368L.toInt()
        private val DOT_ON_LIGHT = 0xFF202124L.toInt()
        private val DOT_OFF_LIGHT = 0x33000000L.toInt()
        private const val PER_PAGE = 20
        private const val PLATE_RADIUS_DP = 20
    }

    private val pages = ArrayList<View>()
    private val dots = ArrayList<View>()
    private var pageIndex = 0

    /** 背板是否偏暗：决定上面的文字/图标用浅色还是深色。 */
    private val lightContent: Boolean = plateDarknessPercent >= 50
    private val chipBackground: Int = if (lightContent) CHIP_BG_DARK else CHIP_BG_LIGHT
    private val chipText: Int = if (lightContent) CHIP_TEXT_DARK else CHIP_TEXT_LIGHT
    private val labelColor: Int = if (lightContent) LABEL_DARK else LABEL_LIGHT
    private val navColor: Int = if (lightContent) NAV_DARK else NAV_LIGHT
    private val dotOn: Int = if (lightContent) DOT_ON_DARK else DOT_ON_LIGHT
    private val dotOff: Int = if (lightContent) DOT_OFF_DARK else DOT_OFF_LIGHT

    init {
        val plateColor = plateColor(plateAlphaPercent, plateDarknessPercent)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundDrawable(plateColor, PLATE_RADIUS_DP)
            setPadding(dp(12), dp(12), dp(12), dp(10))
        }
        // 按需求：点背板空白处**不消失**（只有点面板以外才取消），所以背板不挂点击监听。
        root.addView(buildFunctionRow(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(View(context), LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        val pagerLp = LinearLayout.LayoutParams(MATCH_PARENT, 0)
        pagerLp.weight = 1f
        root.addView(buildPager(targets), pagerLp)
        root.addView(buildNav(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(root, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** 背板颜色：透明度控制 alpha，暗黑程度控制灰度（0% = 白玻璃，100% = 纯黑）。 */
    private fun plateColor(alphaPercent: Int, darknessPercent: Int): Int {
        val alpha = (255 * alphaPercent / 100).coerceIn(0, 255)
        val shade = (255 * (100 - darknessPercent) / 100).coerceIn(0, 255)
        return Color.argb(alpha, shade, shade, shade)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundDrawable(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun buildFunctionRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val functions = listOf(
            FrostedFunction.SEGMENT to "分词",
            FrostedFunction.SAVE to "保存",
            FrostedFunction.SHARE to "分享",
            FrostedFunction.TRANSLATE to "翻译",
            FrostedFunction.COPY to "复制",
        )
        for ((fn, label) in functions) {
            val chip = TextView(context).apply {
                text = label
                setTextColor(chipText)
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                background = roundDrawable(chipBackground, 14)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener { onFunction(fn) }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        return row
    }

    private fun buildPager(targets: List<ShareTarget>): View {
        val pager = FrameLayout(context)
        val pageCount = (targets.size + PER_PAGE - 1) / PER_PAGE.coerceAtLeast(1)
        for (p in 0 until pageCount) {
            val from = p * PER_PAGE
            val to = minOf(from + PER_PAGE, targets.size)
            val page = buildPage(targets.subList(from, to))
            page.visibility = if (p == 0) View.VISIBLE else View.GONE
            pager.addView(page, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            pages.add(page)
        }
        // 左右滑动翻页（监听器返回 false，不吞子项点击）。
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (Math.abs(velocityX) > Math.abs(velocityY) && Math.abs(velocityX) > 900f) {
                        if (velocityX < 0) {
                            switchPage(pageIndex + 1)
                        } else {
                            switchPage(pageIndex - 1)
                        }
                        return true
                    }
                    return false
                }
            },
        )
        pager.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
        return pager
    }

    private fun buildNav(): View {
        val nav = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val pageCount = pages.size
        val prev = TextView(context).apply {
            text = "‹"
            textSize = 18f
            setTextColor(navColor)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { switchPage(pageIndex - 1) }
        }
        val next = TextView(context).apply {
            text = "›"
            textSize = 18f
            setTextColor(navColor)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { switchPage(pageIndex + 1) }
        }
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (p in 0 until pageCount) {
            val dot = View(context)
            val d = dp(6)
            dot.background = roundDrawable(if (p == 0) dotOn else dotOff, d / 2)
            val lp = LinearLayout.LayoutParams(d, d)
            lp.setMargins(dp(3), 0, dp(3), 0)
            dot.layoutParams = lp
            dots.add(dot)
            dotsRow.addView(dot)
        }
        nav.addView(prev)
        nav.addView(dotsRow)
        nav.addView(next)
        val navLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(2)
        }
        nav.layoutParams = navLp
        return nav
    }

    private fun buildPage(slice: List<ShareTarget>): View {
        // 嵌套 LinearLayout：外层 5 行各 weight=1 均分高度、行内 4 格各 weight=1 均分宽度，
        // 行为完全确定（GridLayout 的“双向 weight + MATCH_PARENT”会量成 0）。
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        var i = 0
        for (row in 0 until 5) {
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            for (col in 0 until 4) {
                if (i < slice.size) {
                    rowView.addView(
                        buildGridItem(slice[i]),
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                    )
                    i++
                } else {
                    rowView.addView(View(context), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                }
            }
            page.addView(rowView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        return page
    }

    private fun buildGridItem(target: ShareTarget): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        val icon = ImageView(context).apply {
            val s = dp(38)
            layoutParams = LinearLayout.LayoutParams(s, s)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(iconResolver(target))
        }
        val label = TextView(context).apply {
            text = target.label
            setTextColor(labelColor)
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val labelLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        labelLp.topMargin = dp(3)
        label.layoutParams = labelLp
        item.addView(icon)
        item.addView(label)
        item.setOnClickListener { onTarget(target) }
        return item
    }

    private fun switchPage(next: Int) {
        val count = pages.size
        if (count <= 1) return
        val idx = next.coerceIn(0, count - 1)
        if (idx == pageIndex) return
        pages[pageIndex].visibility = View.GONE
        pages[idx].visibility = View.VISIBLE
        pageIndex = idx
        for (i in dots.indices) {
            (dots[i].background as? GradientDrawable)?.setColor(if (i == pageIndex) dotOn else dotOff)
        }
    }
}
