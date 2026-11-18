package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws alignment guides over the live camera preview: 4 corner squares
 * positioned using the exact same fractions as the sheet's own printed
 * corner markers (via GuideGeometry, shared with AlignmentAnalyzer) so the
 * guide, the live alignment check, and the real printed sheet all agree on
 * the same target positions.
 *
 * Each corner square turns green independently as soon as AlignmentAnalyzer
 * reports that corner looks aligned, giving live feedback before capture —
 * MarkerDetector still does the real, careful validation on the actual
 * captured photo afterward. (An earlier version also drew a big connecting
 * rectangle between the 4 squares; removed since the corner squares alone
 * are enough to align to, and the rectangle was just visual clutter.)
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Which paper layout this test uses. v3.4: QUARTER is now the only
     *  value (HALF_CROSS/HALF_LENGTH removed). */
    var paperLayout: PaperLayout = PaperLayout.QUARTER
        set(value) {
            field = value
            invalidate()
        }

    /** Which of the 4 corners (top-left, top-right, bottom-left, bottom-right)
     *  currently look aligned, from AlignmentAnalyzer's live check. */
    var cornersAligned: BooleanArray = BooleanArray(4)
        set(value) {
            field = value
            invalidate()
        }

    private val goldColor = Color.parseColor("#E8B923")
    private val greenColor = Color.parseColor("#4CAF50")

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val frame = GuideGeometry.computeFrame(width.toFloat(), height.toFloat(), paperLayout)
        // Anchored to the view's own width (not frame.width) so the guide box
        // is the same visual size across all 3 paper layouts — see
        // GuideGeometry.markerSizeFractionOfView for why frame.width varies
        // too much between a wide landscape tile and a narrow portrait one.
        val markerW = (SheetTemplate.MARKER_SIZE * GuideGeometry.TOLERANCE_SCALE * width * (1f - 2f * GuideGeometry.OUTER_MARGIN_FRACTION)).coerceAtLeast(24f)
        val markerH = markerW

        val markerFractions = GuideGeometry.markerFractionsOfView(width.toFloat(), height.toFloat(), paperLayout)
        markerFractions.forEachIndexed { i, (fx, fy) ->
            val cx = fx * width
            val cy = fy * height
            val isAligned = cornersAligned.getOrNull(i) == true
            markerPaint.color = if (isAligned) greenColor else goldColor
            drawCornerSquare(canvas, cx - markerW / 2f, cy - markerH / 2f, markerW, markerH)
        }
    }

    private fun drawCornerSquare(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        canvas.drawRect(x, y, x + w, y + h, markerPaint)
    }
}
