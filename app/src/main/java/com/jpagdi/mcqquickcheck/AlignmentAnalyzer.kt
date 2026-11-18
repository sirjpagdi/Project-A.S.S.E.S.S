package com.jpagdi.mcqquickcheck

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Lightweight per-frame "does each corner look aligned yet" check for the
 * live camera preview, so ScanOverlayView can turn each corner square green
 * independently as a hint before the user taps Capture.
 *
 * Rewritten to a two-stage pipeline after real-device testing kept showing
 * the guide reacting to rows of shaded/unshaded answer bubbles almost as
 * readily as the actual corner markers — template matching (even against a
 * distinctive ring shape) still isn't a strong enough discriminator against
 * a dense grid of circles at tolerances loose enough to be usable live:
 *
 *  1. PAPER DETECTION: look for the physical sheet's own boundary in the
 *     frame (the largest bright, roughly-quadrilateral contour) and use ITS
 *     4 corners as search centers when found — this is a much better
 *     starting point than the purely geometric guide position, since it
 *     reacts to where the paper actually is. Falls back to the geometric
 *     guide position for any corner it can't confidently find.
 *  2. CORNER ROI VALIDATION: within a small window at each corner, use
 *     ContourMarkerValidator — contour hierarchy (does this shape have a
 *     hole?) plus geometric validation (is it actually square, not round?)
 *     — instead of brightness/correlation. See that file for why this is a
 *     fundamentally stronger discriminator against circular bubbles.
 *
 * Operates directly on the analysis frame's Y (luminance) plane — no bitmap
 * decode needed — wrapped as a Mat, which keeps this fast enough to run
 * continuously without stalling the preview. Public API (constructor,
 * BooleanArray-per-corner callback) is unchanged from before, so nothing
 * else needed to change to pick this up.
 */
class AlignmentAnalyzer(
    viewWidth: Int,
    viewHeight: Int,
    paperLayout: PaperLayout,
    private val onResult: (BooleanArray) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastProcessedAtMs = 0L
    private val minIntervalMs = 180L // throttle to ~5-6 checks/sec

    // How far (in multiples of the expected marker size) a detected
    // paper-quad corner is allowed to sit from the geometric guess and
    // still be trusted as a refinement of the search center. Deliberately
    // much wider than GuideGeometry.TOLERANCE_SCALE (used for the final
    // per-marker contour search) — see call site comment.
    private val PAPER_QUAD_MATCH_SCALE = 12f

    // Computed once from the (fixed, for this scan session) preview size —
    // these are fractions of the DISPLAY-oriented view, matching exactly
    // what ScanOverlayView draws.
    private val markerFractions =
        GuideGeometry.markerFractionsOfView(viewWidth.toFloat(), viewHeight.toFloat(), paperLayout)
    private val markerSizeFraction =
        GuideGeometry.markerSizeFractionOfView(viewWidth.toFloat(), viewHeight.toFloat(), paperLayout)

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedAtMs < minIntervalMs || !OpenCvSupport.ensureInitialized()) {
            image.close()
            return
        }
        lastProcessedAtMs = now

        val result = try {
            checkAlignment(image)
        } catch (e: Exception) {
            BooleanArray(4)
        } finally {
            image.close()
        }
        onResult(result)
    }

    private fun checkAlignment(image: ImageProxy): BooleanArray {
        val yPlane = image.planes[0]
        if (yPlane.pixelStride != 1) return BooleanArray(4) // Y plane is always pixelStride 1 in practice

        val rowStride = yPlane.rowStride
        val rawW = image.width
        val rawH = image.height
        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360

        val buffer = yPlane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Wrap the raw Y-plane bytes as a Mat, then drop row padding (rowStride
        // can be wider than the actual image width) via a sub-Mat.
        val padded = Mat(rawH, rowStride, CvType.CV_8UC1)
        padded.put(0, 0, bytes)
        val gray = if (rowStride != rawW) padded.submat(0, rawH, 0, rawW) else padded

        try {
            val displayWidth = if (rotation == 90 || rotation == 270) rawH else rawW
            val expectedMarkerPx = markerSizeFraction * displayWidth

            // Stage 1: expected (buffer-space) center for each corner, from
            // the geometric guide — always computed as the fallback.
            val guessPoints = markerFractions.map { (fx, fy) ->
                displayFractionToBuffer(fx, fy, rawW, rawH, rotation)
            }

            // Stage 1b: try to find the paper's own quad in the frame and
            // use its corners to refine the search centers where possible.
            // Uses a WIDER match distance than the per-marker contour search
            // below (PAPER_QUAD_MATCH_SCALE, not TOLERANCE_SCALE) — a small
            // paper layout (e.g. 1/4 page) held at a normal distance can sit
            // well off the geometric guess's exact position even when
            // correctly framed, and this refinement step existing at all is
            // what should be pulling the search centers toward reality, so
            // tying it to the same tight tolerance as the final per-marker
            // check defeated its own purpose.
            val paperQuad = findPaperQuadBufferPoints(gray)
            val searchCenters = refineWithPaperQuad(guessPoints, paperQuad, expectedMarkerPx * PAPER_QUAD_MATCH_SCALE)

            // Stage 2: confirm an actual marker (not a bubble, not scene
            // clutter) sits near each search center via contour + geometry.
            // Returns the matched centroid per corner (or null if none found).
            val matchedPoints = searchCenters.map { (bx, by) ->
                ContourMarkerValidator.findMarkerNear(gray, bx, by, expectedMarkerPx)
            }

            // Stage 3: diagonal-pair cross-validation. markerFractions()
            // order is [top-left, top-right, bottom-left, bottom-right], so
            // the two diagonals are (0,3) and (1,2). In an actual rectangle,
            // both diagonals bisect each other at the same midpoint AND are
            // the same length — a locally square-and-holed contour that
            // passed the per-corner check but doesn't fit that relationship
            // (e.g. it matched a coincidentally square-ish cluster of
            // bubbles rather than the real opposite corner) gets rejected
            // here even though it passed on its own.
            val result = BooleanArray(4) { matchedPoints[it] != null }
            crossValidateDiagonals(matchedPoints, expectedMarkerPx, result)
            return result
        } finally {
            if (gray !== padded) gray.release()
            padded.release()
        }
    }

    /**
     * Requires the two diagonals (top-left↔bottom-right and
     * top-right↔bottom-left) to actually look like a rectangle's diagonals
     * when BOTH are fully found: sharing a midpoint, and comparable length.
     * A locally-plausible false match (e.g. a coincidentally square-ish
     * cluster of bubbles) that doesn't fit this relationship gets demoted
     * back to "not found" on both ends of whichever diagonal(s) fail, even
     * though each end individually passed its own contour+geometry check.
     * If a diagonal isn't fully found yet (still positioning the phone),
     * there's nothing to compare it against — leaves single found corners
     * as-is so alignment still shows progress one corner at a time.
     */
    private fun crossValidateDiagonals(
        points: List<org.opencv.core.Point?>,
        expectedMarkerPx: Float,
        result: BooleanArray
    ) {
        val tl = points[0]; val tr = points[1]; val bl = points[2]; val br = points[3]

        val diagTLBR = if (tl != null && br != null) Diagonal(tl, br) else null
        val diagTRBL = if (tr != null && bl != null) Diagonal(tr, bl) else null

        if (diagTLBR == null || diagTRBL == null) return // nothing to cross-check yet

        val midpointDistance = sqrt(
            (diagTLBR.midX - diagTRBL.midX) * (diagTLBR.midX - diagTRBL.midX) +
                (diagTLBR.midY - diagTRBL.midY) * (diagTLBR.midY - diagTRBL.midY)
        )
        val maxMidpointDrift = expectedMarkerPx * GuideGeometry.TOLERANCE_SCALE * 1.5
        val lengthRatio = if (diagTRBL.length > 0) diagTLBR.length / diagTRBL.length else 0.0

        val looksLikeARectangle = midpointDistance <= maxMidpointDrift && lengthRatio in 0.6..1.67
        if (!looksLikeARectangle) {
            result[0] = false; result[1] = false; result[2] = false; result[3] = false
        }
    }

    private data class Diagonal(val a: org.opencv.core.Point, val b: org.opencv.core.Point) {
        val midX = (a.x + b.x) / 2.0
        val midY = (a.y + b.y) / 2.0
        val length = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }

    /**
     * Converts normalized display-space coordinates into buffer-space
     * sensor-buffer pixel coordinates, accounting for CameraX's reported rotation.
     */
    private fun displayFractionToBuffer(fx: Float, fy: Float, rawW: Int, rawH: Int, rotation: Int): Pair<Int, Int> =
        when (rotation) {
            90 -> (fy * rawW).toInt() to ((1 - fx) * rawH).toInt()
            270 -> ((1 - fy) * rawW).toInt() to (fx * rawH).toInt()
            180 -> ((1 - fx) * rawW).toInt() to ((1 - fy) * rawH).toInt()
            else -> (fx * rawW).toInt() to (fy * rawH).toInt()
        }

    /**
     * Looks for the physical paper's own boundary: the largest bright
     * contour in the frame that approximates to a quadrilateral and covers
     * a substantial share of the frame (so a small bright speck can't win).
     * Returns its 4 corners in buffer-space pixel coordinates (unordered),
     * or null if no confident quad was found.
     */
    private fun findPaperQuadBufferPoints(gray: Mat): List<Pair<Int, Int>>? {
        val binary = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) return null

            val frameArea = gray.rows().toDouble() * gray.cols().toDouble()
            var bestPoints: List<Pair<Int, Int>>? = null
            var bestArea = 0.0

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < frameArea * 0.06) continue // too small to plausibly be the whole sheet
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                if (approx.total() == 4L && area > bestArea) {
                    bestArea = area
                    bestPoints = approx.toArray().map { it.x.toInt() to it.y.toInt() }
                }
                c2f.release()
                approx.release()
            }
            return bestPoints
        } finally {
            binary.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * For each expected (guide-based) corner point, if a detected paper-quad
     * corner sits within [maxMatchDistance] of it, use that refined point
     * instead — otherwise keep the geometric guess. Each detected quad
     * corner can only be matched to one expected corner (closest first).
     */
    private fun refineWithPaperQuad(
        expectedPoints: List<Pair<Int, Int>>,
        quadPoints: List<Pair<Int, Int>>?,
        maxMatchDistance: Float
    ): List<Pair<Int, Int>> {
        if (quadPoints == null || quadPoints.size != 4) return expectedPoints
        val used = BooleanArray(4)

        // Greedily assign expected corners in order of how close their best
        // available match is, so two expected corners can't both grab the
        // same detected quad corner while a better pairing exists elsewhere.
        data class Candidate(val expectedIdx: Int, val quadIdx: Int, val dist: Double)
        val candidates = mutableListOf<Candidate>()
        for (ei in expectedPoints.indices) {
            val (ex, ey) = expectedPoints[ei]
            for (qi in quadPoints.indices) {
                val (qx, qy) = quadPoints[qi]
                val dx = (qx - ex).toDouble()
                val dy = (qy - ey).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= maxMatchDistance) candidates.add(Candidate(ei, qi, dist))
            }
        }
        candidates.sortBy { it.dist }

        val refined = expectedPoints.toMutableList()
        val expectedAssigned = BooleanArray(expectedPoints.size)
        for (cand in candidates) {
            if (expectedAssigned[cand.expectedIdx] || used[cand.quadIdx]) continue
            expectedAssigned[cand.expectedIdx] = true
            used[cand.quadIdx] = true
            refined[cand.expectedIdx] = quadPoints[cand.quadIdx]
        }
        return refined
    }
}
