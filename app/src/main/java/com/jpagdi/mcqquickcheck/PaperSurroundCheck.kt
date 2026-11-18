package com.jpagdi.mcqquickcheck

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * A real printed answer sheet is a small dark marker sitting in the middle of
 * a large plain white/cream sheet of paper. Template matching alone only
 * checks "is there something roughly this shape here" — it doesn't know
 * anything about what's AROUND that shape, so a sufficiently square-ish dark
 * patch in a random scene (a window frame, a tile grout line, a shadow) could
 * still score well on shape alone. This adds a second, independent check:
 * the area immediately around a matched marker must actually be bright
 * (paper-like), not just the marker-sized patch itself.
 */
object PaperSurroundCheck {

    /** Minimum average brightness (0..255) the area around a matched marker
     *  must have to count as "sitting on a plain sheet of paper.
     *
     *  v3.1 FIX (item 8, done WITH real data, not guessed): lowered from
     *  140.0 to 90.0. Sir Julius's uploaded scan_diagnostics_log.txt showed
     *  repeated MARKERS_NOT_FOUND failures in low-light sessions that ALSO
     *  contained successful ACCEPTED scans (from BubbleGrader, separately)
     *  with raw adaptive ink thresholds as low as 8.0–30.0 — meaning the
     *  whole photo, paper included, was genuinely dark on the raw grayscale
     *  scale in those sessions. A fixed 140.0 floor rejects real paper
     *  outright under exactly that kind of ordinary (non-flash, indoor)
     *  lighting — this is the SAME class of bug as BubbleGrader's pre-v3.0
     *  fixed-threshold issue (see BubbleGrader's blendedInkThreshold
     *  history), just one pipeline stage earlier: corner detection instead
     *  of bubble grading.
     *
     *  90.0 reuses BubbleGrader.HARD_INK_THRESHOLD's value as its anchor —
     *  an already-vetted "how dark is too dark" constant in this exact
     *  codebase — rather than inventing a new number from nothing. It's a
     *  meaningful loosening (real paper under normal indoor light commonly
     *  reads well under 140 but still clearly brighter than the dark
     *  non-paper clutter this check exists to reject — wood, fabric,
     *  shadow), not a removal of the guard.
     *
     *  This is a best estimate from real but indirect evidence — the old
     *  log format couldn't isolate this specific gate as the failure cause
     *  (see CornerDiagnostic.rejectedByPaperGate, added this same pass).
     *  Once the new v3.1 logging captures real rejectedByPaperGate=true
     *  cases with the actual surround brightness value at rejection, a
     *  future pass can tune this exact number with direct data instead of
     *  this reasoned estimate — worth revisiting then, not before. */
    var WHITE_THRESHOLD = 90.0

    /**
     * Returns true if the region around ([centerX], [centerY]) — roughly
     * [markerSizePx] wide — looks like it's sitting on plain light paper.
     * Uses the mean brightness of a box a bit larger than the marker itself;
     * since the dark marker only occupies a modest fraction of that box's
     * area, a genuinely white surround still pulls the average high, while a
     * random dark/textured scene (no actual white paper around it) stays low.
     */
    fun isOnWhitePaper(gray: Mat, centerX: Int, centerY: Int, markerSizePx: Int): Boolean {
        val outerRadius = (markerSizePx * 1.8).toInt().coerceAtLeast(markerSizePx + 4)
        val left = (centerX - outerRadius).coerceIn(0, gray.cols() - 1)
        val top = (centerY - outerRadius).coerceIn(0, gray.rows() - 1)
        val right = (centerX + outerRadius).coerceIn(left + 1, gray.cols())
        val bottom = (centerY + outerRadius).coerceIn(top + 1, gray.rows())
        if (right - left <= 2 || bottom - top <= 2) return false

        val region = Mat(gray, Rect(left, top, right - left, bottom - top))
        return try {
            Core.mean(region).`val`[0] >= WHITE_THRESHOLD
        } finally {
            region.release()
        }
    }
}
