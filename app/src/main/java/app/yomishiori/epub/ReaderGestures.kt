package app.yomishiori.epub

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import app.yomishiori.data.prefs.PageTurnDirection
import kotlin.math.abs

fun nextPageIsLeft(direction: PageTurnDirection, bookIsRtl: Boolean): Boolean = when (direction) {
    PageTurnDirection.AUTO -> bookIsRtl
    PageTurnDirection.LEFT_TO_RIGHT -> false
    PageTurnDirection.RIGHT_TO_LEFT -> true
}

fun isForward(tapLeft: Boolean, direction: PageTurnDirection, bookIsRtl: Boolean): Boolean =
    tapLeft == nextPageIsLeft(direction, bookIsRtl)

class SwipeBlockingLayout(context: Context) : FrameLayout(context) {
    var blockHorizontalSwipes = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var blocking = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                blocking = false
            }
            MotionEvent.ACTION_MOVE -> if (blockHorizontalSwipes) {
                val dx = abs(event.x - downX)
                if (dx > slop && dx > abs(event.y - downY)) blocking = true
            }
        }
        return blocking
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) blocking = false
        return blocking || super.onTouchEvent(event)
    }
}
