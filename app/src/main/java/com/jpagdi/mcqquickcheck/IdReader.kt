package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * v1.2.0 — reads the handwritten ID digit boxes (SheetTemplate.idDigits of
 * them, 4..12) off a scanned sheet.
 *
 * Input is the WARPED, pre-annotation sheet bitmap ScanActivity already has
 * (`currentProcessedBitmap`), in which SheetTemplate fractions map straight to
 * pixels. Box positions come from SheetTemplate.idBoxRect — the same function
 * SheetGenerator draws the printed boxes with.
 *
 * v1.2.3 — cleanup rewritten after real student handwriting came back as
 * "????????????". The old cleanup shrank each crop by 10% and discarded EVERY
 * ink blob touching the crop edge, on the theory that those were the printed
 * box lines. But real digits are big (they fill most of the box), so they
 * touched the edge — and any digit that touched or crossed a printed line was
 * fused with it into one big blob. Both were thrown away: every box "blank".
 *
 * Per box (mirrors tools/id_model/id_features.py clean_ink_outset — keep in sync):
 *  1. crop the box PLUS a margin around it (OUTSET_X / OUTSET_Y) so a digit
 *     that spills over a line, or sits a few pixels off because the header is
 *     outside the corner markers the warp is anchored to, is still whole
 *  2. blank right away if the crop has no real dark ink (contrast gate)
 *  3. Otsu threshold (ink = white)
 *  4. REMOVE the printed lines themselves — long, straight runs near the
 *     expected box edges — instead of throwing away whole blobs; small gaps a
 *     removed line leaves in a stroke that crossed it are bridged back
 *  5. keep only blobs whose centre is inside this box (slightly inset) (drops the neighbouring
 *     boxes, the Section row's handwriting above and the title below), big
 *     enough to be ink, and not crumbs next to the main stroke
 *  6. blank if what's left is too little or too small to be a digit
 *  7. straighten handwriting slant (step 7a in code), then bounding box -> scaled to fit 32x32 (aspect kept, centered), thin
 *     strokes thickened, then 4x4 block sums -> 64 features (unchanged, so
 *     the trained model needs no retraining)
 *  8. IdDigitClassifier -> probabilities
 *
 * Because step 7 scales the digit to fit, large and small handwriting land on
 * the same 32x32 frame — size no longer matters as long as the digit survives
 * steps 1-6.
 */
object IdReader {

    // Crop margin around the printed box, as a fraction of the box's own
    // width/height. Vertical is larger: digits spill over the top/bottom
    // lines far more often than sideways, and the neighbours are close
    // horizontally.
    private const val OUTSET_X = 0.10
    private const val OUTSET_Y = 0.28

    // Printed-line removal. A line is a run at least this long (fraction of
    // the box width/height) that sits within a band around where the box edge
    // is supposed to be. Pen strokes are shorter than that, or elsewhere.
    private const val H_LINE_LEN = 0.60
    private const val V_LINE_LEN = 0.80
    private const val H_BAND = 0.20
    private const val V_BAND = 0.14

    // Blank-paper gate: the darkest ~3% of pixels must be at least this much
    // darker than the median, or there is no ink here at all.
    private const val MIN_CONTRAST = 55

    private const val MIN_COMPONENT_FRACTION = 0.004   // of the box area
    // A blob counts as THIS box's only if its centre is inside the box, inset a
    // little: crumbs of the printed box line sit ON the edge, and one such crumb
    // stretched a digit's bounding box until a "1" read as a 6.
    private const val CENTROID_TOLERANCE = -0.04       // of the box size (negative = inset)

    // Slant correction (step 7a). Students write at every angle; measured on
    // digits sheared up to ~25 degrees the model went from 96% to 99.7% right
    // after straightening, and upright digits lose nothing (99.8% -> 99.7%).
    private const val MAX_SKEW = 0.35
    private const val MIN_SKEW = 0.08
    private const val RELATIVE_PART_FRACTION = 0.10    // vs the largest blob
    private const val BLANK_INK_FRACTION = 0.006       // of the box area
    private const val BLANK_MIN_EXTENT = 0.15          // of the box height

    private const val FRAME = 32
    private const val HOG_GAIN = 4f
    private const val TARGET_INK_FRACTION = 0.45f
    private const val MAX_THICKEN = 3

    /** One position of the ID: [probs] is null when the box looked blank
     *  (or couldn't be read), otherwise probabilities for digits 0..9. */
    /** [blankReasons] (diagnostics only): per box, null when the box was read,
     *  otherwise why it came back blank — NO_CONTRAST (no dark ink in the crop),
     *  NO_BLOB (ink, but none centred in the box), LITTLE_INK, TOO_SMALL,
     *  TINY_BOX / TINY_CROP (geometry). */
    class Read(val probs: List<FloatArray?>, val blankReasons: List<String?> = emptyList()) {
        val readableCount: Int get() = probs.count { it != null }

        /** Compact text form (per box "-" or ten permille values, ";"-separated)
         *  stored with an unmatched scan, so a later re-check doesn't have to
         *  decode and re-read the saved image. */
        fun encode(): String = probs.joinToString(";") { p ->
            if (p == null) "-" else p.joinToString(",") { (it * 1000f).roundToInt().toString() }
        }

        /** Most-likely digit per box, '?' where blank/unreadable — for display only. */
        val digits: String
            get() = probs.joinToString("") { p ->
                if (p == null) "?" else p.indices.maxByOrNull { p[it] }.toString()
            }

        companion object {
            /** Inverse of [encode]; null if [s] is empty or malformed. */
            fun decode(s: String, digits: Int): Read? {
                if (s.isBlank()) return null
                val parts = s.split(';')
                if (parts.size != digits) return null
                return try {
                    Read(parts.map { part ->
                        if (part == "-") null else {
                            val v = part.split(',').map { it.toFloat() / 1000f }
                            require(v.size == 10)
                            val sum = v.sum().let { if (it > 0f) it else 1f }
                            FloatArray(10) { v[it] / sum }
                        }
                    })
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /** Reads the [digits] ID boxes. Returns null when the digit model isn't
     *  available or OpenCV can't start. [digits] must be the digit count the
     *  sheet was PRINTED with (SheetTemplate.idDigits) — the box layout
     *  depends on it. */
    fun read(context: Context, sheet: Bitmap, digits: Int): Read? {
        if (digits < SheetTemplate.ID_MIN_DIGITS || digits > SheetTemplate.ID_MAX_DIGITS) return null
        val classifier = IdDigitClassifier.load(context) ?: return null
        if (!OpenCvSupport.ensureInitialized()) return null

        val rgba = Mat()
        Utils.bitmapToMat(sheet, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        val probs = ArrayList<FloatArray?>(digits)
        val blankReasons = ArrayList<String?>(digits)
        try {
            for (i in 0 until digits) {
                val box = SheetTemplate.idBoxRect(i, digits)
                val x0 = (box.left * gray.cols()).toInt().coerceIn(0, gray.cols() - 1)
                val y0 = (box.top * gray.rows()).toInt().coerceIn(0, gray.rows() - 1)
                val x1 = (box.right * gray.cols()).toInt().coerceIn(x0 + 1, gray.cols())
                val y1 = (box.bottom * gray.rows()).toInt().coerceIn(y0 + 1, gray.rows())
                val boxResult = featuresFor(gray, x0, y0, x1, y1, classifier.featureKind)
                probs.add(boxResult.features?.let { classifier.predict(it) })
                blankReasons.add(boxResult.blankReason)
            }
        } finally {
            gray.release()
        }
        return Read(probs, blankReasons)
    }

    private class BoxResult(val features: FloatArray?, val blankReason: String?)

    /** features == null means a blank box (blankReason says why — diagnostics
     *  only, nothing branches on it). [sheet] is the whole grayscale sheet;
     *  (x0,y0)-(x1,y1) is the PRINTED box in sheet pixels. */
    private fun featuresFor(sheet: Mat, x0: Int, y0: Int, x1: Int, y1: Int, featureKind: String): BoxResult {
        val bw = x1 - x0
        val bh = y1 - y0
        if (bw < 8 || bh < 8) return BoxResult(null, "TINY_BOX")

        // 1. box + margin, clipped to the sheet
        val ox = round(bw * OUTSET_X).toInt()
        val oy = round(bh * OUTSET_Y).toInt()
        val cx0 = max(0, x0 - ox)
        val cy0 = max(0, y0 - oy)
        val cx1 = min(sheet.cols(), x1 + ox)
        val cy1 = min(sheet.rows(), y1 + oy)
        val cw = cx1 - cx0
        val ch = cy1 - cy0
        if (cw < 8 || ch < 8) return BoxResult(null, "TINY_CROP")
        // the printed box, in crop coordinates
        val nx0 = x0 - cx0
        val ny0 = y0 - cy0
        val nx1 = x1 - cx0
        val ny1 = y1 - cy0

        val owned = ArrayList<Mat>()
        fun mat(m: Mat = Mat()): Mat { owned.add(m); return m }
        try {
            val g = mat()
            val region = sheet.submat(Rect(cx0, cy0, cw, ch))
            region.copyTo(g)
            region.release()

            // 2. contrast gate
            val px = ByteArray(cw * ch)
            g.get(0, 0, px)
            val sorted = IntArray(px.size) { px[it].toInt() and 0xFF }
            sorted.sort()
            val darkN = max(1, sorted.size / 33)
            var darkSum = 0L
            for (k in 0 until darkN) darkSum += sorted[k]
            val median = sorted[sorted.size / 2]
            if (median - darkSum.toDouble() / darkN < MIN_CONTRAST) return BoxResult(null, "NO_CONTRAST")

            // 3. Otsu, ink = white
            val binary = mat()
            Imgproc.threshold(g, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

            // 4a. horizontal printed lines near the top/bottom edge. The 1x3
            //     dilate first so a slightly tilted line still opens as one run.
            val hLen = max(5, (H_LINE_LEN * bw).toInt())
            val hDil = mat()
            Imgproc.dilate(binary, hDil, mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 3.0))))
            val hLines = mat()
            Imgproc.morphologyEx(hDil, hLines, Imgproc.MORPH_OPEN,
                mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(hLen.toDouble(), 1.0))))
            val hBand = mat(Mat.zeros(ch, cw, CvType.CV_8UC1))
            val bandY = round(H_BAND * bh).toInt()
            for (ey in intArrayOf(ny0, ny1)) {
                Imgproc.rectangle(
                    hBand,
                    Point(0.0, max(0, ey - bandY).toDouble()),
                    Point((cw - 1).toDouble(), min(ch - 1, ey + bandY).toDouble()),
                    Scalar(255.0), -1
                )
            }
            Core.bitwise_and(hLines, hBand, hLines)

            // 4b. vertical printed lines near the left/right edge
            val vLen = max(5, (V_LINE_LEN * bh).toInt())
            val vDil = mat()
            Imgproc.dilate(binary, vDil, mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 1.0))))
            val vLines = mat()
            Imgproc.morphologyEx(vDil, vLines, Imgproc.MORPH_OPEN,
                mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, vLen.toDouble()))))
            val vBand = mat(Mat.zeros(ch, cw, CvType.CV_8UC1))
            val bandX = round(V_BAND * bw).toInt()
            for (ex in intArrayOf(nx0, nx1)) {
                Imgproc.rectangle(
                    vBand,
                    Point(max(0, ex - bandX).toDouble(), 0.0),
                    Point(min(cw - 1, ex + bandX).toDouble(), (ch - 1).toDouble()),
                    Scalar(255.0), -1
                )
            }
            Core.bitwise_and(vLines, vBand, vLines)

            // 4c. cut the lines out, then bridge the gaps they leave in strokes that crossed them
            val lines = mat()
            Core.bitwise_or(hLines, vLines, lines)
            val notLines = mat()
            Core.bitwise_not(lines, notLines)
            val ink = mat()
            Core.bitwise_and(binary, notLines, ink)
            val closeV = mat()
            Imgproc.morphologyEx(ink, closeV, Imgproc.MORPH_CLOSE,
                mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 5.0))))
            val closeH = mat()
            Imgproc.morphologyEx(ink, closeH, Imgproc.MORPH_CLOSE,
                mat(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 1.0))))
            val bridge = mat()
            Core.bitwise_or(closeV, closeH, bridge)
            Core.bitwise_and(bridge, lines, bridge)
            Core.bitwise_or(ink, bridge, ink)

            // 5. which blobs belong to THIS box
            val labels = mat()
            val stats = mat()
            val centroids = mat()
            val n = Imgproc.connectedComponentsWithStats(ink, labels, stats, centroids, 8, CvType.CV_32S)
            val boxArea = bw.toDouble() * bh
            val minArea = MIN_COMPONENT_FRACTION * boxArea
            val tolX = CENTROID_TOLERANCE * bw
            val tolY = CENTROID_TOLERANCE * bh
            val st = IntArray(5)       // LEFT, TOP, WIDTH, HEIGHT, AREA
            val cen = DoubleArray(2)
            val candidate = BooleanArray(n)
            var largest = 0
            for (i in 1 until n) {
                stats.get(i, 0, st)
                centroids.get(i, 0, cen)
                val inside = cen[0] >= nx0 - tolX && cen[0] <= nx1 + tolX &&
                    cen[1] >= ny0 - tolY && cen[1] <= ny1 + tolY
                if (inside && st[4] >= minArea) {
                    candidate[i] = true
                    if (st[4] > largest) largest = st[4]
                }
            }
            if (largest == 0) return BoxResult(null, "NO_BLOB")
            val keepLabel = BooleanArray(n)
            for (i in 1 until n) {
                if (!candidate[i]) continue
                stats.get(i, 0, st)
                if (st[4] >= RELATIVE_PART_FRACTION * largest) keepLabel[i] = true
            }

            val labelData = IntArray(ch * cw)
            labels.get(0, 0, labelData)

            var minX = cw; var minY = ch; var maxX = -1; var maxY = -1
            var inkCount = 0
            val inkBits = BooleanArray(ch * cw)
            for (y in 0 until ch) {
                for (x in 0 until cw) {
                    val lab = labelData[y * cw + x]
                    if (lab > 0 && keepLabel[lab]) {
                        inkBits[y * cw + x] = true
                        inkCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            // 6. too little, or too small to be a digit -> blank
            if (maxX < 0 || inkCount < BLANK_INK_FRACTION * boxArea) return BoxResult(null, "LITTLE_INK")
            if (max(maxY - minY + 1, maxX - minX + 1) < BLANK_MIN_EXTENT * bh) return BoxResult(null, "TOO_SMALL")

            val upright = deslant(inkBits, cw, minX, minY, maxX, maxY)
            return BoxResult(featuresFromInk(upright.bits, upright.stride, upright.minX, upright.minY, upright.maxX, upright.maxY, featureKind), null)
        } finally {
            owned.forEach { it.release() }
        }
    }

    private class Ink(val bits: BooleanArray, val stride: Int, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    /** Step 7a: undo handwriting slant. The slant is the ink's own measure —
     *  mu11 / mu02, the horizontal drift per row — and each row is shifted back
     *  by that much (whole pixels, no resampling). Only corrects clear slant
     *  (MIN_SKEW..MAX_SKEW); anything else is returned untouched. */
    private fun deslant(bits: BooleanArray, stride: Int, minX: Int, minY: Int, maxX: Int, maxY: Int): Ink {
        val untouched = Ink(bits, stride, minX, minY, maxX, maxY)
        var n = 0
        var sx = 0.0
        var sy = 0.0
        for (y in minY..maxY) for (x in minX..maxX) if (bits[y * stride + x]) { n++; sx += x; sy += y }
        if (n < 10) return untouched
        val xc = sx / n
        val yc = sy / n
        var mu02 = 0.0
        var mu11 = 0.0
        for (y in minY..maxY) for (x in minX..maxX) if (bits[y * stride + x]) {
            val dy = y - yc
            mu02 += dy * dy
            mu11 += (x - xc) * dy
        }
        if (mu02 < 1e-6) return untouched
        val skew = (mu11 / mu02).coerceIn(-MAX_SKEW, MAX_SKEW)
        if (abs(skew) < MIN_SKEW) return untouched

        val hgt = maxY - minY + 1
        val wid = maxX - minX + 1
        val pad = ceil(abs(skew) * hgt).toInt() + 1
        val newStride = wid + 2 * pad
        val out = BooleanArray(hgt * newStride)
        var nMinX = newStride
        var nMaxX = -1
        for (y in minY..maxY) {
            val shift = round(-skew * (y - yc)).toInt()
            for (x in minX..maxX) {
                if (!bits[y * stride + x]) continue
                val nx = pad + (x - minX) + shift
                if (nx < 0 || nx >= newStride) continue
                out[(y - minY) * newStride + nx] = true
                if (nx < nMinX) nMinX = nx
                if (nx > nMaxX) nMaxX = nx
            }
        }
        if (nMaxX < 0) return untouched
        return Ink(out, newStride, nMinX, 0, nMaxX, hgt - 1)
    }

    /** Step 7: tight bbox of [ink] (row-major, [stride] wide) -> 32x32 frame
     *  -> thicken -> 64 block sums. Unchanged from v1.2.0 — the model was
     *  trained on exactly this representation. */
    private fun featuresFromInk(ink: BooleanArray, stride: Int, minX: Int, minY: Int, maxX: Int, maxY: Int, featureKind: String): FloatArray {
        val cw = stride
        // Tight crop of the kept ink as an 8-bit 0/255 Mat, then area-resize.
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val tight = ByteArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            if (ink[(minY + y) * cw + (minX + x)]) tight[y * bw + x] = 255.toByte()
        }
        val tightMat = Mat(bh, bw, CvType.CV_8UC1)
        tightMat.put(0, 0, tight)
        val s = FRAME.toDouble() / max(bh, bw)
        val nw = max(1, round(bw * s).toInt())
        val nh = max(1, round(bh * s).toInt())
        val resized = Mat()
        Imgproc.resize(tightMat, resized, Size(nw.toDouble(), nh.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        tightMat.release()
        val resizedData = ByteArray(nw * nh)
        resized.get(0, 0, resizedData)
        resized.release()

        var frame = Array(FRAME) { IntArray(FRAME) }
        val oy = (FRAME - nh) / 2
        val ox = (FRAME - nw) / 2
        for (y in 0 until nh) for (x in 0 until nw) {
            if ((resizedData[y * nw + x].toInt() and 0xFF) >= 128) frame[oy + y][ox + x] = 1
        }

        // Thicken thin pen strokes toward the training data's stroke weight.
        repeat(MAX_THICKEN) {
            var boxInk = 0
            for (y in oy until oy + nh) for (x in ox until ox + nw) boxInk += frame[y][x]
            if (boxInk.toFloat() / (nw * nh) >= TARGET_INK_FRACTION) return@repeat
            frame = dilate3x3(frame)
        }

        if (featureKind == IdDigitClassifier.FEATURES_HOG) return hogFeatures(frame)

        val features = FloatArray(64)
        for (by in 0 until 8) for (bx in 0 until 8) {
            var sum = 0
            for (y in 0 until 4) for (x in 0 until 4) sum += frame[by * 4 + y][bx * 4 + x]
            features[by * 8 + bx] = sum.toFloat()
        }
        return features
    }

    /** v1.2.3 "hog-v1": stroke-direction histograms of the 32x32 frame — 9 orientation
     *  bins per 8x8 cell, normalised over 16x16 blocks, 324 numbers, x HOG_GAIN.
     *  Parameters and gain MUST match tools/id_model/id_features.py (HOG, HOG_GAIN). */
    private val hogDescriptor: HOGDescriptor by lazy {
        // created on first use, i.e. after OpenCvSupport has loaded the native libraries
        HOGDescriptor(Size(32.0, 32.0), Size(16.0, 16.0), Size(8.0, 8.0), Size(8.0, 8.0), 9)
    }

    private fun hogFeatures(frame: Array<IntArray>): FloatArray {
        val pixels = ByteArray(FRAME * FRAME)
        for (y in 0 until FRAME) for (x in 0 until FRAME) {
            if (frame[y][x] != 0) pixels[y * FRAME + x] = 255.toByte()
        }
        val img = Mat(FRAME, FRAME, CvType.CV_8UC1)
        img.put(0, 0, pixels)
        val out = MatOfFloat()
        try {
            hogDescriptor.compute(img, out)
            val values = out.toArray()
            for (i in values.indices) values[i] *= HOG_GAIN
            return values
        } finally {
            img.release()
            out.release()
        }
    }

    private fun dilate3x3(src: Array<IntArray>): Array<IntArray> {
        val out = Array(FRAME) { IntArray(FRAME) }
        for (y in 0 until FRAME) for (x in 0 until FRAME) {
            var v = 0
            for (dy in -1..1) for (dx in -1..1) {
                val yy = y + dy
                val xx = x + dx
                if (yy in 0 until FRAME && xx in 0 until FRAME && src[yy][xx] != 0) v = 1
            }
            out[y][x] = v
        }
        return out
    }
}
