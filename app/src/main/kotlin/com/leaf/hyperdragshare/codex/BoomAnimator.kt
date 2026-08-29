package com.leaf.hyperdragshare.codex

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

object BoomAnimator {
    private val mIterpolator = DecelerateInterpolator(1.5f)

    const val BOOM_DURATION = 200L
    const val FADE_DURATION = 200L
    private const val MOVE_DURATION = 200L
    private const val HIDE_DURATION = 100L

    private fun makeScaleAnimator(view: View, from: Float, to: Float, duration: Long): Animator {
        val animatorSet = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, from, to)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, from, to)

        animatorSet.interpolator = mIterpolator
        animatorSet.duration = duration
        animatorSet.play(scaleX).with(scaleY)
        return animatorSet
    }

    private fun makeAlphaAnimator(view: View, from: Float, to: Float, duration: Long): Animator {
        val animator: Animator = ObjectAnimator.ofFloat(view, View.ALPHA, from, to)
        animator.duration = duration
        animator.interpolator = mIterpolator
        return animator
    }

    private fun makeXYAnimator(view: View, toX: Float, toY: Float, duration: Long): Animator {
        val animatorSet = AnimatorSet()
        val x = ValueAnimator.ofFloat(view.x, toX)
        x.addUpdateListener { animation -> view.x = animation.animatedValue as Float }
        val y = ValueAnimator.ofFloat(view.y, toY)
        y.addUpdateListener { animation -> view.y = animation.animatedValue as Float }
        animatorSet.interpolator = mIterpolator
        animatorSet.duration = duration
        animatorSet.play(x).with(y)
        return animatorSet
    }

    private fun makeTranslationAnimator(view: View, duration: Long): Animator {
        val animatorSet = AnimatorSet()
        val transX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, 0f)
        val transY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, 0f)

        animatorSet.interpolator = mIterpolator
        animatorSet.duration = duration
        animatorSet.play(transX).with(transY)
        return animatorSet
    }

    private fun makeTranslationYAnimator(
        view: View,
        start: Float,
        end: Float,
        duration: Long,
    ): Animator {
        val transY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, start, end)
        transY.interpolator = mIterpolator
        transY.duration = duration
        return transY
    }

    private fun makeHeightAnimator(view: View, targetHeight: Int, duration: Long): Animator {
        val anim = ValueAnimator.ofInt(view.measuredHeight, targetHeight)
        anim.addUpdateListener { valueAnimator ->
            setHeight(view, valueAnimator.animatedValue as Int)
        }
        anim.interpolator = mIterpolator
        anim.duration = duration
        return anim
    }

    private fun setHeight(view: View, height: Int) {
        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
        layoutParams.height = height
        view.layoutParams = layoutParams
    }

    fun makeFadeIn(view: View, duration: Long) {
        val animator = makeAlphaAnimator(view, 0f, 1.0f, duration)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                view.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animator) {
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animator.start()
    }

    fun makeFadeOut(view: View, duration: Long) {
        val animator = makeAlphaAnimator(view, 1.0f, 0f, duration)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
                view.visibility = View.INVISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {
                view.visibility = View.INVISIBLE
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animator.start()
    }

    fun makeHeightAnimation(view: View, targetHeight: Int, startY: Float, endY: Float) {
        if (startY == endY && targetHeight == view.measuredHeight) {
            return
        }
        val animatorSet = AnimatorSet()
        val heightAnimator = makeHeightAnimator(view, targetHeight, MOVE_DURATION)
        val transAnimator = makeTranslationYAnimator(view, startY, endY, MOVE_DURATION)
        animatorSet.playTogether(heightAnimator, transAnimator)
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                if (view.visibility != View.VISIBLE) {
                    view.visibility = View.VISIBLE
                }
            }

            override fun onAnimationEnd(animation: Animator) {
            }

            override fun onAnimationCancel(animation: Animator) {
                setHeight(view, targetHeight)
                view.translationY = endY
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animatorSet.start()
    }

    fun makeBarAndRectShowAnimation(bar: View, rect: View, targetHeight: Int) {
        val animatorSet = AnimatorSet()
        val heightAnimator = makeHeightAnimator(rect, targetHeight, MOVE_DURATION)
        val bgAlpha = makeAlphaAnimator(rect, 0f, 1.0f, MOVE_DURATION)
        val barAlpha = makeAlphaAnimator(bar, 0f, 1.0f, MOVE_DURATION)
        animatorSet.playTogether(heightAnimator, bgAlpha, barAlpha)
        animatorSet.start()
    }

    fun makeBarAndRectHideAnimation(bar: View, rect: View) {
        val animatorSet = AnimatorSet()
        val bgAlpha = makeAlphaAnimator(rect, 1.0f, 0f, HIDE_DURATION)
        val barAlpha = makeAlphaAnimator(bar, 1.0f, 0f, HIDE_DURATION)
        animatorSet.playTogether(bgAlpha, barAlpha)
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
                setHeight(rect, 0)
                rect.visibility = View.INVISIBLE
                bar.visibility = View.INVISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animatorSet.start()
    }

    fun makeSectorAnimation(view: View, toX: Float, toY: Float) {
        val animatorSet = AnimatorSet()
        val scaleAnimator = makeScaleAnimator(view, 0f, 1f, BOOM_DURATION)
        val alphaAnimator = makeAlphaAnimator(view, 0f, 1f, BOOM_DURATION)
        animatorSet.playTogether(
            scaleAnimator,
            alphaAnimator,
            makeXYAnimator(view, toX, toY, BOOM_DURATION),
        )
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
            }

            override fun onAnimationCancel(animation: Animator) {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.alpha = 1.0f
                view.translationX = 0f
                view.translationY = 0f
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animatorSet.start()
    }

    fun makeBoomAnimation(view: View) {
        val animatorSet = AnimatorSet()
        val scaleAnimator = makeScaleAnimator(view, 0f, 1f, BOOM_DURATION)
        val alphaAnimator = makeAlphaAnimator(view, 0f, 1f, BOOM_DURATION)
        animatorSet.playTogether(
            scaleAnimator,
            alphaAnimator,
            makeTranslationAnimator(view, BOOM_DURATION),
        )
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
            }

            override fun onAnimationCancel(animation: Animator) {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.alpha = 1.0f
                view.translationX = 0f
                view.translationY = 0f
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
        animatorSet.start()
    }

    fun makeMoveAnimation(view: View, startY: Float, endY: Float) {
        if (startY != endY) {
            val animatorSet = AnimatorSet()
            val moveAnimator = makeTranslationYAnimator(view, startY, endY, MOVE_DURATION)
            animatorSet.playTogether(moveAnimator)
            animatorSet.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.translationY = endY
                }

                override fun onAnimationRepeat(animation: Animator) {
                }
            })
            animatorSet.start()
        }
    }
}
