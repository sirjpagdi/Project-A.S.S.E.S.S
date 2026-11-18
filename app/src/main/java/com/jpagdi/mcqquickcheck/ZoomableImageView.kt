package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * A minimal pinch-to-zoom + pan ImageView, used for the scanned-sheet result
 * so a teacher can zoom in on a specific bubble to double-check an ambiguous
 * or wrong mark. No external library — just a Matrix-based transform driven
 * by ScaleGestureDetector (pinch) and GestureDetector (drag/double-tap).
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    private val workingMatrix = Matrix()

    private var scaleFactor = 1f
    private val minScale = 1f
    private val maxScale = 6f

    /** True while zoomed in past the default fit — used externally (e.g. by
     *  FullscreenImageViewer's swipe-to-navigate) to avoid treating a pan
     *  gesture while zoomed as a swipe-to-next-image gesture instead. */
    val isZoomed: Boolean get() = scaleFactor > 1.01f

    /** True when this instance is hosted in the fullscreen viewer dialog
     *  rather than the small inline result preview. In that mode there's no
     *  ScrollView parent to fight with, so it always claims touches instead
     *  of conditionally releasing them — simplifies the fullscreen swipe/
     *  pan/zoom experience and avoids the small-preview-only interop logic
     *  below running somewhere it isn't needed. */
    var fullscreenMode = false

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    /** Fires on a double-tap when this view is NOT in fullscreen mode — used
     *  by the small inline preview to launch the fullscreen viewer instead
     *  of zooming in place. Fullscreen-mode instances handle double-tap
     *  zoom themselves via PanListener and never call this. */
    var onRequestFullscreen: (() -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, PanListener())
        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (fullscreenMode) {
                // No ScrollView parent here — always claim touches so a
                // pinch or swipe-pan never gets bumped by anything else on
                // screen (e.g. the dialog's own dismiss-on-outside-tap).
                return@setOnTouchListener true
            }

            // IMPORTANT: must return true for ACTION_DOWN specifically,
            // always — not conditionally. Returning false from onTouch for
            // ACTION_DOWN tells Android this view doesn't want ANY part of
            // the gesture, so it never receives ACTION_POINTER_DOWN (a
            // second finger landing) afterward. Since a pinch always starts
            // as ONE finger going down first (pointerCount is 1, scaleFactor
            // is still 1f at that exact first frame), the old code — which
            // returned `scaleFactor > 1.01f || event.pointerCount > 1` for
            // every event including this first one — always evaluated to
            // false right at the start, so the view rejected the gesture
            // before a second finger could ever be seen. This is why
            // pinch-zoom didn't work AT ALL, not just imperfectly.
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                return@setOnTouchListener true
            }

            // For every event after the initial down, only claim (and tell
            // the parent ScrollView to back off) while actually zoomed in
            // or during a real multi-finger pinch — otherwise let it fall
            // through so normal vertical scrolling still works at 1x zoom.
            val claiming = scaleFactor > 1.01f || event.pointerCount > 1
            parent?.requestDisallowInterceptTouchEvent(claiming)
            claiming
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) resetToFit()
    }

    /** Resets to the default fit-center placement (called whenever a new
     *  image is set/laid out, and externally by FullscreenImageViewer's
     *  swipe-to-navigate before loading the next/previous image, so
     *  swiping to a new scan doesn't inherit the previous one's zoom
     *  level). */
    fun resetToFit() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return

        val scale = minOf(viewWidth / drawableWidth, viewHeight / drawableHeight)
        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        workingMatrix.reset()
        workingMatrix.postScale(scale, scale)
        workingMatrix.postTranslate(dx, dy)
        imageMatrix = workingMatrix
        scaleFactor = 1f
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { resetToFit() }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = newScale / scaleFactor
            scaleFactor = newScale
            workingMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            imageMatrix = workingMatrix
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // Only pan while zoomed in — at 1x, let the parent ScrollView handle vertical scroll normally.
            if (scaleFactor <= 1.01f) return false
            workingMatrix.postTranslate(-distanceX, -distanceY)
            imageMatrix = workingMatrix
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!fullscreenMode) {
                // Inline preview: double-tap now opens the fullscreen
                // viewer instead of zooming in place — the small preview
                // area was too cramped to usefully inspect a bubble even
                // when zoomed, per user feedback.
                onRequestFullscreen?.invoke()
                return true
            }
            if (scaleFactor > 1.01f) {
                resetToFit()
            } else {
                scaleFactor = 3f
                workingMatrix.postScale(3f, 3f, e.x, e.y)
                imageMatrix = workingMatrix
            }
            return true
        }
    }
}
