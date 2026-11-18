package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as CvPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Un-skews a captured photo so the 4 corner markers land exactly where
 * SheetTemplate says they should be.
 *
 * Previously used Android's Matrix.setPolyToPoly + Canvas.drawBitmap to
 * avoid pulling in a CV library — but OpenCV is already a hard dependency
 * everywhere else in this app (MarkerDetector, AlignmentAnalyzer), so that
 * tradeoff no longer applies. Switched to OpenCV's getPerspectiveTransform
 * + warpPerspective with cubic interpolation, which is meaningfully sharper
 * than Android Matrix's implicit bilinear filtering — this is the main
 * visible gap versus modern phone "scan document" output.
 */
object PerspectiveWarp {

    /**
     * Warps [source] so that [detectedCorners] (in source-image pixel coords,
     * ordered top-left, top-right, bottom-left, bottom-right) map onto the
     * expected marker positions for [template], producing an output bitmap
     * sized [outputWidth] x [outputHeight] (defaults to template page size).
     */
    fun warp(
        source: Bitmap,
        detectedCorners: List<MarkerDetector.Point>,
        template: SheetTemplate,
        outputWidth: Int = SheetTemplate.PAGE_WIDTH.toInt(),
        outputHeight: Int = SheetTemplate.PAGE_HEIGHT.toInt()
    ): Bitmap {
        require(detectedCorners.size == 4) { "Expected exactly 4 detected corners" }
        if (!OpenCvSupport.ensureInitialized()) {
            throw IllegalStateException("Could not start the image scanner (OpenCV failed to load). Try restarting the app.")
        }

        val expected = template.markerCenters().map { (fx, fy) ->
            fx * outputWidth to fy * outputHeight
        }

        val srcMat = MatOfPoint2f(*detectedCorners.map { CvPoint(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val dstMat = MatOfPoint2f(*expected.map { (ex, ey) -> CvPoint(ex.toDouble(), ey.toDouble()) }.toTypedArray())

        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        srcMat.release()
        dstMat.release()

        val sourceMat = Mat()
        Utils.bitmapToMat(source, sourceMat)

        val outputMat = Mat()
        Imgproc.warpPerspective(
            sourceMat,
            outputMat,
            transform,
            Size(outputWidth.toDouble(), outputHeight.toDouble()),
            Imgproc.INTER_CUBIC,
            Core.BORDER_CONSTANT,
            Scalar(255.0, 255.0, 255.0, 255.0) // white fill for any area outside the source image, matching the old canvas.drawColor(WHITE) background
        )
        sourceMat.release()
        transform.release()

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, output)
        outputMat.release()

        return output
    }
}
