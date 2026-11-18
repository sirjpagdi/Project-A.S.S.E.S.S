package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Cleans up a dewarped scan before grading/preview: removes uneven shadow
 * and overhead-light glare, boosts local contrast, and sharpens edges — so
 * both BubbleGrader and the saved/previewed image see the same clean
 * version, closer to what a phone's built-in "scan document" mode
 * produces.
 *
 * DELIBERATELY NOT HARD BINARIZATION. The original ask for this feature
 * described full black/white thresholding (Otsu binarization). That was
 * reviewed and rejected before implementation: BubbleGrader.kt's grading
 * is not a black/white check — it reads GRAYSCALE coverage percentages
 * per bubble and computes its own per-photo adaptive Otsu threshold from
 * them (see BubbleGrader.kt's pointScores/adaptiveThreshold blend), tuned
 * specifically to tell full/partial/faint/double marks apart from each
 * other. Thresholding this preprocessor's output to pure 0/255 would
 * collapse that gradient before BubbleGrader ever saw it, breaking the
 * exact ambiguity detection the diagnostics system (added a few versions
 * back) exists to characterize. So every step below stays in continuous
 * grayscale — illumination correction and CLAHE contrast both operate on
 * tone, not a single global cutoff. Confirmed with the user against
 * reference photos from their phone's own document-scanner app: the
 * "clean, crisp, shadow-free" look they wanted comes from local contrast
 * and sharpening, not from flattening to two colors.
 *
 * If this ever needs revisiting: don't reach for Imgproc.threshold with
 * THRESH_BINARY or THRESH_OTSU in this file. That's the rejected approach.
 */
object ImagePreprocessor {

    // Starting point, not a proven value — this needs on-device validation
    // against real scans before it's trusted. Higher = more aggressive
    // local contrast boost.
    private const val CLAHE_CLIP_LIMIT = 2.5

    // CLAHE operates on 8x8 tiles by default in OpenCV; matches typical
    // guidance for document-style images and is a reasonable starting grid
    // size for a sheet roughly this aspect range.
    private val CLAHE_TILE_GRID = Size(8.0, 8.0)

    fun process(source: Bitmap): Bitmap {
        if (!OpenCvSupport.ensureInitialized()) {
            throw IllegalStateException("Could not start the image scanner (OpenCV failed to load). Try restarting the app.")
        }

        val rgba = Mat()
        Utils.bitmapToMat(source, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        // --- Illumination correction (shadow / glare removal) ---
        // Estimate the background lighting field with a large-kernel
        // median blur, then divide it out. This is a standard
        // division-based shading-correction technique: a big enough
        // kernel blurs away the actual text/bubbles (small, localized
        // dark marks) while still tracking the slow, broad brightness
        // gradient caused by uneven lighting or a shadow falling across
        // the page. Dividing the original by that estimate normalizes the
        // background toward a consistent tone without needing to assume
        // in advance what "white" should look like, and without
        // thresholding anything.
        val kernelSize = (minOf(gray.width(), gray.height()) / 15).let {
            if (it % 2 == 0) it + 1 else it
        }.coerceAtLeast(21)
        val background = Mat()
        Imgproc.medianBlur(gray, background, kernelSize)
        val normalized = Mat()
        Core.divide(gray, background, normalized, 255.0)
        background.release()
        gray.release()

        // --- Local contrast (CLAHE) ---
        // Contrast Limited Adaptive Histogram Equalization boosts LOCAL
        // contrast per-tile instead of one global stretch — this is what
        // makes faint or shadow-dimmed marks read as crisp without a
        // single cutoff value that would treat the whole page the same
        // way regardless of where a shadow actually fell.
        val clahe = Imgproc.createCLAHE(CLAHE_CLIP_LIMIT, CLAHE_TILE_GRID)
        val contrasted = Mat()
        clahe.apply(normalized, contrasted)
        normalized.release()

        // --- Sharpening (unsharp mask) ---
        // Classic technique: blur a copy, then push the original AWAY
        // from that blurred version to accentuate edges — sharpens the
        // boundary between ink and paper without any thresholding.
        val blurred = Mat()
        Imgproc.GaussianBlur(contrasted, blurred, Size(0.0, 0.0), 3.0)
        val sharpened = Mat()
        Core.addWeighted(contrasted, 1.5, blurred, -0.5, 0.0, sharpened)
        contrasted.release()
        blurred.release()

        val outRgba = Mat()
        Imgproc.cvtColor(sharpened, outRgba, Imgproc.COLOR_GRAY2RGBA)
        sharpened.release()

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, output)
        outRgba.release()

        return output
    }
}
