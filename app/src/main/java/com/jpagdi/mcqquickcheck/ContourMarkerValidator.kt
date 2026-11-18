package com.jpagdi.mcqquickcheck

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * Confirms a real corner marker (not a bubble, not scene clutter) sits near
 * a given point, using contour topology + geometry rather than brightness or
 * shape-correlation:
 *
 * - Our marker is a solid black SQUARE with a white square cut out of its
 *   center — a "ring." In OpenCV's contour hierarchy (RETR_CCOMP), that
 *   produces an outer contour with exactly one nested child contour (the
 *   hole).
 * - A FILLED answer bubble is a solid dark circle with NO hole — no child
 *   contour, rejected immediately.
 * - An UNFILLED (empty) bubble outline DOES have a hole (its white paper-
 *   colored center), so the hole check alone can't rule it out — but its
 *   shape is round, not square, which polygon approximation and bounding-
 *   box geometry both catch: a circle needs far more vertices than a square
 *   to approximate well at a tight tolerance, and this is really the
 *   deciding check here.
 *
 * This is deliberately NOT the same technique as MarkerDetector (the final,
 * proven-accurate check on the captured photo, which uses template
 * matching + centroid refinement) — contour/geometry validation is cheaper
 * per-frame and, more importantly, is a fundamentally different signal
 * (topology + shape) than correlation, so it doesn't share template
 * matching's weakness of scoring a cluster of circles as "close enough" to
 * a square at typical tolerances.
 */
object ContourMarkerValidator {

    /** Polygon-approximation tolerance, as a fraction of the contour's own
     *  perimeter — larger tolerates real print/photo imperfections in the
     *  square's edges; too large would also start accepting circles. */
    private const val APPROX_EPSILON_FRACTION = 0.05

    /** Accepted approxPolyDP vertex count for the outer contour. A clean
     *  square is 4; real photos of a printed square often approximate to
     *  5-6 due to rounded corners from print/JPEG softening. A circle at
     *  this same tolerance typically still needs 8+ to approximate well,
     *  so this range comfortably excludes bubbles while tolerating a
     *  slightly rough square. */
    private val VERTEX_COUNT_RANGE = 4..6

    /** Accepted bounding-box aspect ratio (width/height) — near 1.0 for a
     *  square. (A circle is also ~1.0, which is exactly why this check
     *  alone isn't sufficient and vertex count / extent matter more.) */
    private val ASPECT_RATIO_RANGE = 0.65..1.55

    /** Accepted contour size relative to the expected marker pixel size. */
    private const val MIN_SIZE_SCALE = 0.35
    private const val MAX_SIZE_SCALE = 2.8

    /** Accepted "extent" (contour area / bounding-box area). The outer
     *  contour of a solid-ish square region (ignoring its inner hole, which
     *  contourArea doesn't subtract) sits close to 1.0. A circle's extent
     *  is capped at π/4 ≈ 0.785 by geometry alone, so this range still
     *  leaves circles some room — vertex count is the primary
     *  discriminator, this is a secondary sanity check. */
    private val EXTENT_RANGE = 0.6..1.05

    /**
     * Searches a window around ([centerX], [centerY]) for a contour matching
     * our marker's ring topology + square geometry. Returns the matched
     * contour's centroid if found, or null.
     */
    fun findMarkerNear(gray: Mat, centerX: Int, centerY: Int, expectedMarkerPx: Float): org.opencv.core.Point? {
        val searchRadius = (expectedMarkerPx * GuideGeometry.TOLERANCE_SCALE).toInt().coerceAtLeast(12)
        val left = (centerX - searchRadius).coerceIn(0, gray.cols() - 1)
        val top = (centerY - searchRadius).coerceIn(0, gray.rows() - 1)
        val right = (centerX + searchRadius).coerceIn(left + 1, gray.cols())
        val bottom = (centerY + searchRadius).coerceIn(top + 1, gray.rows())
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 8 || regionH <= 8) return null

        val region = Mat(gray, Rect(left, top, regionW, regionH))
        val binary = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            // THRESH_BINARY_INV + OTSU: dark ink (the ring's black border)
            // becomes the white foreground findContours traces; the white
            // paper (and the ring's white center hole) becomes background.
            Imgproc.threshold(region, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) return null

            for (i in contours.indices) {
                val row = hierarchy.get(0, i) ?: continue
                val hasChild = row.size > 2 && row[2].toInt() != -1
                if (!hasChild) continue // no hole -> can't be our ring (rules out filled bubbles)
                if (isSquareRing(contours[i], expectedMarkerPx)) {
                    val boundingRect = Imgproc.boundingRect(contours[i])
                    val cx = left + boundingRect.x + boundingRect.width / 2.0
                    val cy = top + boundingRect.y + boundingRect.height / 2.0
                    return org.opencv.core.Point(cx, cy)
                }
            }
            return null
        } finally {
            region.release()
            binary.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun isSquareRing(contour: MatOfPoint, expectedMarkerPx: Float): Boolean {
        val area = Imgproc.contourArea(contour)
        if (area <= 1.0) return false

        val boundingRect = Imgproc.boundingRect(contour)
        val w = boundingRect.width.toDouble()
        val h = boundingRect.height.toDouble()
        if (w <= 0 || h <= 0) return false

        val aspect = w / h
        if (aspect !in ASPECT_RATIO_RANGE) return false

        val sizeScale = ((w + h) / 2.0) / expectedMarkerPx
        if (sizeScale < MIN_SIZE_SCALE || sizeScale > MAX_SIZE_SCALE) return false

        val extent = area / (w * h)
        if (extent !in EXTENT_RANGE) return false

        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, APPROX_EPSILON_FRACTION * peri, true)
        val vertexCount = approx.total().toInt()
        contour2f.release()
        approx.release()

        return vertexCount in VERTEX_COUNT_RANGE
    }
}
