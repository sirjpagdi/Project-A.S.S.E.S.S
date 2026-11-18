package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Grades a warped (un-skewed) bubble sheet image. No character recognition
 * anywhere — pure OMR.
 *
 * Uses COVERAGE-based fill detection: what fraction of each bubble's true
 * printed area reads as ink, rather than the darkness at one small sample
 * point. A point sample can read dark enough even for a tiny partial dot
 * (high peak darkness, low actual coverage) and is more sensitive to the
 * sample landing slightly off-bubble from warp imprecision. Coverage over
 * the bubble's real radius is more robust to both.
 *
 * The per-pixel ink/paper threshold is computed adaptively per sheet (see
 * OtsuThreshold) rather than using a fixed brightness value — a fixed value
 * either missed real marks in dim light or false-positived on faint paper
 * texture in bright light, since a photo's overall brightness shifts
 * everything, blank bubbles included. FILLED_THRESHOLD below is now only a
 * fallback for the rare case the adaptive value looks implausible.
 */
object BubbleGrader {

    /** Fallback average-darkness threshold (0..255) if the adaptive one looks implausible. */
    var FILLED_THRESHOLD = 150.0

    /** Minimum fraction of a bubble's own area that must read as ink for it to
     *  count as filled. Chosen well below what a genuine full pencil fill
     *  reaches (typically 60-90%+), but high enough to reject a small stray
     *  dot or a very light partial mark. */
    var MIN_COVERAGE = 0.46

    /** Absolute floor, checked against a FIXED mid-gray threshold (not the
     *  per-sheet adaptive one). A bubble under this is always blank, full
     *  stop — no adaptive-threshold miscalibration on a single noisy photo
     *  (glare, shadow, uneven exposure) can push it into "filled" by
     *  inflating every coverage number at once, since this check doesn't
     *  use that same threshold. Exists because MIN_COVERAGE alone measures
     *  coverage *relative to* the adaptive threshold — if that threshold
     *  itself reads too dark, a nearly-empty bubble can still clear it. */
    var HARD_MIN_COVERAGE = 0.15
    private const val HARD_INK_THRESHOLD = 90.0

    /** If a SECOND choice in the same question also reaches this much
     *  coverage, more than one bubble looks genuinely marked — flagged
     *  ambiguous (double-marked) rather than picking a "winner". */
    var MIN_SECONDARY_COVERAGE = 0.38

    /** How close a second bubble's coverage has to be to the winner's for it
     *  to count as a real competing mark (see secondHighEnough in grade()).
     *  A winner beating the runner-up by more than this margin is treated
     *  as decisive regardless of the runner-up's absolute coverage — e.g. a
     *  95%-covered bubble isn't "competing" with a 40%-covered one nearby,
     *  that's one clear mark plus noise, not a double-mark situation. */
    var MAX_WINNER_MARGIN = 0.30

    /** If the winning choice's coverage is within this margin of MIN_COVERAGE,
     *  it's a marginal/borderline fill — flagged ambiguous rather than
     *  confidently accepted, even with no real competing second choice.
     *
     *  REGRESSION HISTORY: this was widened to 0.15 in an earlier session
     *  to catch a specific thin/partial stray mark (reported as "item 25").
     *  That turned out to be an overcorrection — 0.15 means anything under
     *  MIN_COVERAGE + 0.15 (currently 61%) gets flagged ambiguous, and a
     *  normal, fully-intentional pencil fill very often does NOT cover
     *  100% of the bubble's exact printed circle (uneven pressure, slightly
     *  outside the geometric edge, etc.) — so this was catching real clean
     *  marks across the board, on every paper layout, not just the thin
     *  stray-mark case it was meant for. Reverted to the original 0.08.
     *  The thin-mark case is now handled separately by centerCoverage in
     *  grade() below, which checks darkness AT the bubble's center
     *  specifically — a real fill is dark at its center almost by
     *  definition, while a thin/stray mark that clips a bubble's edge
     *  often isn't, even at similar overall coverage %. That's a more
     *  specific signal than "raw coverage % is a bit low," which is what
     *  kept causing this same tradeoff to reappear. */
    var MARGINAL_COVERAGE_BAND = 0.08

    /** Below this coverage, a bubble is treated as genuinely blank — no
     *  mark at all, nothing worth flagging. This is deliberately much lower
     *  than MIN_COVERAGE: the gap between BLANK_FLOOR and MIN_COVERAGE is
     *  exactly the "there's something here, but it doesn't clearly resolve"
     *  zone — a real mark that's too small/light to confidently score, but
     *  not nothing either. That zone gets flagged ambiguous (see
     *  belowFillThreshold in grade() below) instead of silently resolving
     *  as blank, which is what let a case like a faint partial mark on one
     *  item get neither scored NOR flagged for a human to double-check. */
    var BLANK_FLOOR = 0.08

    /** Radius of the "center" sample used by centerCoverage, as a fraction
     *  of the bubble's own full radius. Smaller = stricter (only the very
     *  middle of the bubble has to be dark); this value asks for the inner
     *  half of the bubble, which a genuine fill almost always covers even
     *  with imperfect pencil work, but a thin mark clipping just the edge
     *  usually does not reach. */
    var CENTER_RADIUS_FRACTION = 0.5

    /** A winning choice whose overall coverage clears MIN_COVERAGE but whose
     *  CENTER darkness is below this is treated as suspicious — likely a
     *  thin or off-center mark rather than a real fill — and flagged
     *  ambiguous even though the raw coverage % alone looked acceptable. */
    var MIN_CENTER_COVERAGE = 0.35

    /** Radius (px) sampled around each bubble center for the cheap first-pass
     *  point sample used only to find the sheet's adaptive ink threshold. */
    var SAMPLE_RADIUS_FRACTION = 0.016f

    data class QuestionResult(
        val questionNumber: Int,
        val chosenIndex: Int?, // null = left blank
        val isAmbiguous: Boolean,
        val darknessScores: List<Double>, // coverage 0..1 per choice, *255 for rough backward-compat scale
        // True only for a genuinely-blank bubble (essentially zero ink,
        // below BLANK_FLOOR) — as opposed to a real confidence problem
        // (faint mark, double-mark, thin/off-center mark, hard-floor
        // failure). Exists so requireConfident()'s scan-rejection gate can
        // stay narrow (only reject for REAL problems) even though
        // isAmbiguous itself is now broader (flags every blank too, for
        // the on-sheet visual strikethrough, per explicit user request) —
        // a student legitimately skipping several questions should still
        // scan and grade normally, not get the whole sheet rejected as
        // "unclear."
        val isGenuineBlank: Boolean = false,

        // ---- Diagnostics-only fields below (v2.4) ----
        // These do NOT feed into isAmbiguous/chosenIndex/isGenuineBlank —
        // those are still computed exactly as before, above. These just
        // expose the intermediate values that already existed inside
        // grade() so ScanDiagnostics can log WHY a question was flagged,
        // without requireConfident()'s caller having to recompute or guess
        // at internals. All default to a neutral value so nothing outside
        // this file (none of QuestionResult's construction sites live
        // elsewhere) needs to change.
        val maxCoverage: Double = 0.0,
        val hardCoverage: Double = 0.0,
        val centerCoverage: Double = 0.0,
        val secondCoverage: Double = 0.0,
        val secondHighEnough: Boolean = false,
        val marginal: Boolean = false,
        val thinOrOffCenter: Boolean = false,
        val failsHardFloor: Boolean = false
    )

    /** Sheet-level diagnostics (v2.4) captured alongside the per-question
     *  results in [lastSheetDiagnostics] — the adaptive-threshold values
     *  computed inside grade() weren't previously visible outside this
     *  file. Read-only side channel, not a return-value change, so no
     *  existing call site of grade() needs to change. */
    data class SheetDiagnostics(
        val rawAdaptiveThreshold: Double?,
        val blendedInkThreshold: Double,
        val usedFallbackThreshold: Boolean,
        val bubbleRadiusPx: Int,
        // v2.8 additions for the confidence-weighted blend — appended, not
        // inserted, so any positional-order assumption elsewhere (there
        // shouldn't be one, this is a data class used by name, but stated
        // for a future reader) stays safe.
        val otsuSeparability: Double? = null,
        val confidenceTier: String = "N/A"
    )

    /** Set at the end of every grade() call. Diagnostics-only — see
     *  [SheetDiagnostics]. Not thread-safe by design; grade() is only ever
     *  called from ScanActivity's single-threaded photo-processing path. */
    var lastSheetDiagnostics: SheetDiagnostics? = null
        private set

    class LowConfidenceException(message: String) : Exception(message)

    /**
     * Final safety net after grade(): even when MarkerDetector accepts a photo,
     * this catches the case where what's under the markers isn't actually a
     * clean bubble grid, OR the sheet wasn't flat enough for the perspective
     * warp to land cleanly (real testing showed a curled/non-flat sheet can
     * warp correctly at the 4 corners while still smearing ink across
     * bubbles in the middle, since a simple 4-point homography assumes a
     * flat surface). A real filled sheet should have most bubbles clearly
     * one way or the other; if a large chunk of the "answers" come out
     * ambiguous or blank, that's a strong sign to reject rather than report
     * a misleading score.
     */
    fun requireConfident(results: List<QuestionResult>) {
        if (results.isEmpty()) return
        // Deliberately NOT using isAmbiguous here — that field is now
        // broader (flags every blank too, for the on-sheet visual
        // strikethrough, per explicit user request) and would make a
        // sheet with several legitimately-skipped questions get rejected
        // as "unclear" even though nothing actually went wrong with the
        // scan. This gate only cares about REAL problems: a genuinely
        // uncertain mark (competing marks, thin/off-center marks, hard
        // floor failures) — not an ordinary skipped question.
        val realProblemCount = results.count { it.isAmbiguous && !it.isGenuineBlank }
        val blankCount = results.count { it.isGenuineBlank }
        // "Unclear" = a real problem OR blank, counted as a UNION (a
        // question can't be both — isGenuineBlank and a real-problem
        // ambiguity are mutually exclusive by construction), so a plain
        // sum is safe here, unlike the broader isAmbiguous field.
        val unclearCount = realProblemCount + blankCount
        val ambiguousRate = realProblemCount.toDouble() / results.size
        val unclearRate = unclearCount.toDouble() / results.size

        // Tightened from 0.30/0.55 now that coverage-based ambiguity
        // detection is more sensitive to real problems (double-marks,
        // cross-contaminated ink from a non-flat scan) than the old
        // point-sample gap check was — a badly-warped sheet should now more
        // reliably get rejected with a retake prompt instead of silently
        // producing a wrong score.
        if (ambiguousRate > 0.25 || unclearRate > 0.45) {
            throw LowConfidenceException(
                "This doesn't look like a clearly filled answer sheet " +
                    "(${(ambiguousRate * 100).toInt()}% ambiguous, ${(unclearRate * 100).toInt()}% unclear). " +
                    "Retake the photo with the sheet flat, well-lit, and fully in frame."
            )
        }
    }

    fun grade(
        warped: Bitmap,
        template: SheetTemplate,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO
    ): List<QuestionResult> {
        val sampleRadiusPx = (warped.width * SAMPLE_RADIUS_FRACTION).toInt().coerceAtLeast(3)

        // The bubble's TRUE printed radius (same formula SheetGenerator and
        // ResultAnnotator use) — coverage is measured over this full area,
        // not the smaller fixed sample radius. bubbleToCellRatio comes from
        // the "Bubble size" Settings slider (BubbleSizePrefs) — SheetGenerator,
        // BubbleGrader, and ResultAnnotator must always be called with the
        // SAME ratio for a given scan, or the detection radius won't match
        // what was actually printed. See BubbleSizePrefs.kt for why.
        val rowHeightPx = template.rowHeightFraction * warped.height
        val choiceSpacingPx = template.minChoiceSpacingFraction() * warped.width
        val bubbleRadiusPx = (minOf(rowHeightPx, choiceSpacingPx) * bubbleToCellRatio)
            .coerceAtLeast(sampleRadiusPx.toFloat()).toInt()

        // First pass: cheap point-sample darkness, used only to derive this
        // photo's own adaptive ink/paper threshold via Otsu.
        val pointScores = (1..template.questionCount).map { q ->
            template.choiceLetters.indices.map { choiceIndex ->
                val (fx, fy) = template.bubbleCenter(q, choiceIndex)
                val cx = (fx * warped.width).toInt()
                val cy = (fy * warped.height).toInt()
                averageDarkness(warped, cx, cy, sampleRadiusPx)
            }
        }
        val otsuResult = OtsuThreshold.compute(pointScores.flatten())
        val rawAdaptiveThreshold = otsuResult?.threshold

        // --- REGRESSION HISTORY: confidence-weighted blend (v2.8) ---
        // Previously a fixed 75% Otsu / 25% fixed-anchor blend regardless
        // of how trustworthy THIS photo's Otsu split actually was. Real
        // device logs showed the same physical sheet, rescanned seconds
        // apart, swinging rawAdaptiveThreshold from ~16 to ~123 — enough to
        // flip a scan between ACCEPTED and REJECTED_UNCLEAR with nothing
        // about the sheet itself having changed.
        //
        // CODE-LEVEL FINDING (checked against ScanActivity.kt before
        // writing this fix): the bitmap passed into grade() here is
        // already ImagePreprocessor's CLAHE-contrast-enhanced output, not
        // the raw camera photo — that pipeline change happened in v2.7,
        // before this fix. So "run this on a contrast-enhanced image
        // instead of the raw one" was already true going into this fix,
        // and the swings still happened on top of it. That doesn't rule
        // out exposure/noise jitter as A cause, but it does mean CLAHE
        // alone isn't sufficient, and raises a second plausible mechanism
        // worth testing on a future diagnostic pull: CLAHE's local
        // contrast boost is computed per-tile (8x8 grid, see
        // ImagePreprocessor.CLAHE_TILE_GRID) and reacts to whatever noise/
        // focus/framing is in that tile on THIS shot — two photos of the
        // same sheet can get non-identical local contrast boosts near a
        // given bubble, which would itself feed instability into Otsu even
        // though the underlying ink didn't change. Neither mechanism was
        // confirmed against a real pulled histogram (no diagnostic log
        // export with raw point-sample dumps was available when this was
        // written) — see the separability score below, which is designed
        // to be robust to EITHER cause: both a skewed/unimodal histogram
        // and a CLAHE-noise-unstable one should show up as a low
        // between/total variance ratio, since both mean "no clean two-
        // class split," regardless of why.
        //
        // Cutoffs and weights below are REASONED STARTING POINTS, same
        // status as HARD_INK_THRESHOLD=90.0 — not verified against a real
        // separability distribution yet. 0.5/0.25 as HIGH/LOW boundaries
        // split the 0..1 range into thirds-ish, biased so "confident" is a
        // deliberately high bar (a split has to genuinely dominate the
        // variance, not just edge out a coin flip). If the next diagnostic
        // pull shows most real scans clustering just below 0.5, that
        // boundary should move down rather than the weights being
        // re-guessed independently of it.
        val confidenceTier = when {
            otsuResult == null -> "N/A"
            otsuResult.separability >= 0.5 -> "HIGH"
            otsuResult.separability >= 0.25 -> "LOW"
            else -> "VERY_LOW"
        }
        // v3.3 FIX — diagnosed from a real diagnostics log (Huawei
        // MNA-LX9, 8 consecutive REJECTED_UNCLEAR attempts on the same
        // physical sheet). That sheet's Otsu separability sat at 0.75-0.80
        // every attempt: comfortably inside the HIGH tier (>=0.5), but far
        // below the 0.99-1.00 the v3.0 fix above was diagnosing (a
        // DIFFERENT problem — v3.0 fixed the plausible-range sanity check
        // discarding a confidently-bimodal-but-low-ABSOLUTE-darkness
        // reading; this fixes the blend WEIGHT itself, which stayed flat
        // 80/20 across the entire HIGH tier regardless of where in it a
        // sheet's separability actually falls). At a flat 80/20 blend,
        // this sheet's raw adaptive threshold — itself jittery shot-to-
        // shot (69.0 to 85.0 across the 8 attempts, same physical sheet,
        // consistent with the swing this file's own v2.8 history already
        // documented) — still drove ~80% of the final threshold. That
        // shared per-sheet number moving by ~15pt shifts EVERY bubble's
        // coverage% at once, which is why the flagged items didn't
        // cluster geometrically near any one corner (ruling out a
        // perspective-warp/lens-distortion explanation that was
        // separately investigated and rejected for this same report, by
        // checking whether flagged items clustered near the sheet's
        // corner markers — they didn't) but did fall into two mirror-
        // image patterns (some bubbles center-strong, others edge-
        // strong) that flipped which items tripped MARGINAL_COVERAGE_BAND
        // from one attempt to the next.
        //
        // Fix: scale the raw-vs-anchor blend weight WITHIN the HIGH tier,
        // proportional to how far separability sits above the 0.5 floor,
        // instead of jumping straight to 80% raw the instant a sheet
        // crosses 0.5. A separability of 0.99-1.00 still lands at ~80%
        // raw, unchanged — the v3.0 fix above still applies to it exactly
        // as before. A borderline 0.75-0.80 sheet like this one now
        // trusts its own reading a bit more (~62-68% raw) than the old
        // flat 80% did — not because 0.75-0.80 deserves full trust, but
        // because the old code gave a 0.51 sheet and a 1.00 sheet the
        // identical blend, which never made sense once separability is
        // read as a genuine confidence signal rather than a pass/fail
        // gate.
        //
        // REASONED FIX, not yet confirmed by rescanning this exact sheet
        // multiple times post-fix — same status as the cutoffs/weights
        // documented in the v2.8 comment above. Worth revisiting once a
        // fresh diagnostic pull from this device (or a similarly
        // borderline-contrast sheet) is available.
        val highTierRawWeight = if (confidenceTier == "HIGH") {
            val t = ((otsuResult!!.separability - 0.5) / 0.5).coerceIn(0.0, 1.0)
            0.55 + t * 0.35 // 0.55 at separability=0.5 ramping to 0.90 at separability=1.0
        } else 0.0
        val adaptiveThreshold = when (confidenceTier) {
            "HIGH" -> rawAdaptiveThreshold!! * highTierRawWeight + HARD_INK_THRESHOLD * (1.0 - highTierRawWeight)
            "LOW" -> rawAdaptiveThreshold!! * 0.30 + HARD_INK_THRESHOLD * 0.70
            // VERY_LOW: don't blend a number we don't trust into the
            // anchor at all — use the anchor outright. This is distinct
            // from usedFallbackThreshold below (which is FILLED_THRESHOLD,
            // the wider panic fallback for when even THIS looks
            // implausible).
            "VERY_LOW" -> HARD_INK_THRESHOLD
            else -> null // otsuResult was null — too few point samples
        }
        // v3.0 REAL FIX — diagnosed from a real diagnostics log a teacher
        // sent in, not guessed. The bug: this "plausible value" sanity
        // check used ONE fixed window (40..220) for every confidence tier,
        // including HIGH. But a washed-out or low-absolute-contrast photo
        // (overexposed shot, thin pencil, heavy JPEG compression, or the
        // app's own CLAHE step flattening things further) can be
        // GENUINELY, CONFIDENTLY bimodal — Otsu's separability score was
        // 0.99-1.00 (about as high as it gets) — while both clusters still
        // sit at a low ABSOLUTE darkness (raw threshold 4-24 in the log).
        // That's real signal, not noise: the photo just doesn't have much
        // absolute contrast, but blank-vs-filled is still cleanly
        // separated within it. The old code distrusted that low number
        // purely because of its absolute size, threw it away, and
        // substituted the fixed FILLED_THRESHOLD=150 — which is calibrated
        // for a normally-exposed photo. Under 150, marks that only ever
        // reached ~10-30 raw darkness never registered ANY meaningful
        // coverage, producing "100% unclear" on sheets a human reads fine
        // by eye. This exactly matches the reported pattern: pencil (lower
        // absolute contrast) failing far worse than ballpen, and "washed
        // out" photos failing hardest of all.
        //
        // Fix: trust a HIGH-confidence split across a much wider window —
        // separability is already the "is this real" check, a second
        // absolute-value gate on top of it was fighting the first one.
        // LOW/VERY_LOW keep the original stricter 40..220 window, since
        // those tiers are already heavily blended toward the HARD_INK_
        // THRESHOLD anchor and a wildly-off number there is more likely to
        // be genuine garbage (motion blur, glare) than real low-contrast
        // signal. failsHardFloor (fixed threshold=90, independent of this
        // value) is untouched and still catches true over-lenient false
        // positives, so this doesn't remove that safety net — it only
        // stops throwing away good HIGH-confidence low readings before
        // they ever get a chance to be checked against it.
        val plausibleRange = if (confidenceTier == "HIGH") 12.0..220.0 else 40.0..220.0
        val usedFallbackThreshold = adaptiveThreshold == null || adaptiveThreshold !in plausibleRange
        val inkThreshold = adaptiveThreshold?.takeIf { it in plausibleRange } ?: FILLED_THRESHOLD

        // Second pass: coverage over the bubble's real area, using that
        // per-sheet ink threshold to classify each pixel.
        val coverageScores = (1..template.questionCount).map { q ->
            template.choiceLetters.indices.map { choiceIndex ->
                val (fx, fy) = template.bubbleCenter(q, choiceIndex)
                val cx = (fx * warped.width).toInt()
                val cy = (fy * warped.height).toInt()
                bubbleCoverage(warped, cx, cy, bubbleRadiusPx, inkThreshold)
            }
        }

        // Third pass: same coverage measurement but against a FIXED threshold,
        // used only as a sanity floor — see HARD_MIN_COVERAGE doc above.
        val hardCoverageScores = (1..template.questionCount).map { q ->
            template.choiceLetters.indices.map { choiceIndex ->
                val (fx, fy) = template.bubbleCenter(q, choiceIndex)
                val cx = (fx * warped.width).toInt()
                val cy = (fy * warped.height).toInt()
                bubbleCoverage(warped, cx, cy, bubbleRadiusPx, HARD_INK_THRESHOLD)
            }
        }

        // Fourth pass: coverage measured ONLY in the bubble's center region
        // (a smaller inner circle, see CENTER_RADIUS_FRACTION) — see
        // MIN_CENTER_COVERAGE doc above for why this exists. Uses the same
        // adaptive per-sheet threshold as the main coverage pass, since
        // this is about WHERE the ink is, not a different darkness
        // standard.
        val centerRadiusPx = (bubbleRadiusPx * CENTER_RADIUS_FRACTION).toInt().coerceAtLeast(2)
        val centerCoverageScores = (1..template.questionCount).map { q ->
            template.choiceLetters.indices.map { choiceIndex ->
                val (fx, fy) = template.bubbleCenter(q, choiceIndex)
                val cx = (fx * warped.width).toInt()
                val cy = (fy * warped.height).toInt()
                bubbleCoverage(warped, cx, cy, centerRadiusPx, inkThreshold)
            }
        }

        return coverageScores.mapIndexed { i, coverages ->
            val maxCoverage = coverages.max()
            val chosenIndex = coverages.indexOf(maxCoverage)
            val sorted = coverages.sortedDescending()

            // A second bubble only counts as a real competing mark if it's
            // BOTH above the flat floor (MIN_SECONDARY_COVERAGE) AND
            // reasonably close to the winner (within MAX_WINNER_MARGIN).
            // Without the margin check, a clearly dominant mark (e.g. 95%
            // coverage) could get flagged ambiguous just because some other
            // bubble in the same row happened to read a faint 40% —
            // comparing only the runner-up's absolute value ignores how
            // decisively the winner actually won, which is what let an
            // obviously solid, single-marked bubble get struck through as
            // ambiguous.
            val secondHighEnough = sorted.size > 1 &&
                sorted[1] >= MIN_SECONDARY_COVERAGE &&
                (maxCoverage - sorted[1]) <= MAX_WINNER_MARGIN
            val marginal = maxCoverage < MIN_COVERAGE + MARGINAL_COVERAGE_BAND
            val filled = maxCoverage >= MIN_COVERAGE

            // Hard floor: even if the adaptive-threshold pass says "filled",
            // a bubble that barely has any ink under a fixed mid-gray
            // threshold is not a real mark — demote straight to blank rather
            // than trusting a possibly-miscalibrated adaptive threshold.
            val hardCoverage = hardCoverageScores[i][chosenIndex]
            val failsHardFloor = filled && hardCoverage < HARD_MIN_COVERAGE

            // Thin/off-center mark check: overall coverage looked OK, but
            // the bubble's actual center isn't dark enough — likely a small
            // stray mark that happens to clip enough of the bubble's edge
            // to pass the raw coverage %, not a genuine fill.
            val centerCoverage = centerCoverageScores[i][chosenIndex]
            val thinOrOffCenter = filled && !failsHardFloor && centerCoverage < MIN_CENTER_COVERAGE

            // isAmbiguous now flags EVERY not-confidently-filled case —
            // both a genuinely blank bubble (zero ink) and a faint-but-real
            // mark — per explicit user confirmation, reversing an earlier
            // version's narrower behavior (blank = silent, faint-mark =
            // flagged). Scoring is unchanged either way: chosenIndex stays
            // null for both cases, so AnswerParser still excludes both from
            // the score exactly as before — only whether this gets a
            // visual flag on the sheet is different.
            val notConfidentlyFilled = !filled || failsHardFloor

            // Narrower signal, used only by requireConfident()'s
            // scan-rejection gate: true only when there's essentially no
            // ink at all (below BLANK_FLOOR). A sheet where a student
            // legitimately skipped several questions should still scan and
            // grade normally — it's only a REAL problem (faint marks,
            // double-marks, hard-floor failures) that should risk getting
            // the whole scan rejected as "unclear."
            val isGenuineBlank = !filled && maxCoverage < BLANK_FLOOR

            val ambiguous = (filled && !failsHardFloor && (secondHighEnough || marginal || thinOrOffCenter)) ||
                notConfidentlyFilled

            QuestionResult(
                questionNumber = i + 1,
                chosenIndex = if (filled && !failsHardFloor) chosenIndex else null,
                isAmbiguous = ambiguous,
                darknessScores = coverages.map { it * 255.0 },
                isGenuineBlank = isGenuineBlank,
                maxCoverage = maxCoverage,
                hardCoverage = hardCoverage,
                centerCoverage = centerCoverage,
                secondCoverage = sorted.getOrElse(1) { 0.0 },
                secondHighEnough = secondHighEnough,
                marginal = marginal,
                thinOrOffCenter = thinOrOffCenter,
                failsHardFloor = failsHardFloor
            )
        }.also {
            lastSheetDiagnostics = SheetDiagnostics(
                rawAdaptiveThreshold = rawAdaptiveThreshold,
                blendedInkThreshold = inkThreshold,
                usedFallbackThreshold = usedFallbackThreshold,
                bubbleRadiusPx = bubbleRadiusPx,
                otsuSeparability = otsuResult?.separability,
                confidenceTier = confidenceTier
            )
        }
    }

    /** v1.2.5 (Test sets): which set the student shaded. [index] is null when
     *  the row is blank, has several marks, or is otherwise unclear. */
    data class SetReading(val index: Int?, val coverages: List<Double>)

    /** Reads the "Set:" bubble row printed on a sheet with [SheetTemplate.setCount] > 1.
     *  Measures only the inner 60% of each bubble (so the printed ring never
     *  counts as ink), tries +/-2 px offsets and keeps the lowest coverage per
     *  bubble (so a slightly misaligned warp can't turn a ring into a "mark").
     *  Accepts a set only when exactly one bubble is >= 50% shaded and every
     *  other one is below 35%. Call after [grade] so the sheet's own adaptive
     *  ink threshold is used. */
    fun readSet(warped: Bitmap, template: SheetTemplate): SetReading {
        if (!template.hasSets) return SetReading(0, emptyList())
        val ink = lastSheetDiagnostics?.blendedInkThreshold ?: HARD_INK_THRESHOLD
        val innerRadius = (template.setBubbleRadiusFractionOfWidth() * warped.width * 0.6f).toInt().coerceAtLeast(2)
        val offsets = intArrayOf(-2, 0, 2)
        val coverages = (0 until template.setCount).map { i ->
            val (fx, fy) = template.setBubbleCenter(i)
            val cx = (fx * warped.width).toInt()
            val cy = (fy * warped.height).toInt()
            var lowest = 1.0
            for (dx in offsets) for (dy in offsets) {
                lowest = minOf(lowest, bubbleCoverage(warped, cx + dx, cy + dy, innerRadius, ink))
            }
            lowest
        }
        val best = coverages.indices.maxByOrNull { coverages[it] } ?: return SetReading(null, coverages)
        val othersOk = coverages.indices.all { it == best || coverages[it] < 0.35 }
        return SetReading(if (coverages[best] >= 0.50 && othersOk) best else null, coverages)
    }

    /** Fraction of pixels within [radius] of (cx, cy) that read darker than [inkThreshold]. */
    private fun bubbleCoverage(bitmap: Bitmap, cx: Int, cy: Int, radius: Int, inkThreshold: Double): Double {
        var darkCount = 0
        var total = 0
        val left = (cx - radius).coerceAtLeast(0)
        val right = (cx + radius).coerceAtMost(bitmap.width - 1)
        val top = (cy - radius).coerceAtLeast(0)
        val bottom = (cy + radius).coerceAtMost(bitmap.height - 1)

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy > radius * radius) continue
                val pixel = bitmap.getPixel(x, y)
                val luminance = Color.red(pixel) * 0.299 +
                        Color.green(pixel) * 0.587 +
                        Color.blue(pixel) * 0.114
                total++
                if ((255 - luminance) >= inkThreshold) darkCount++
            }
        }
        return if (total == 0) 0.0 else darkCount.toDouble() / total
    }

    /** Returns "darkness" 0..255 where 255 = fully black (inverted luminance), averaged over a disc. */
    private fun averageDarkness(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Double {
        var sum = 0.0
        var count = 0
        val left = (cx - radius).coerceAtLeast(0)
        val right = (cx + radius).coerceAtMost(bitmap.width - 1)
        val top = (cy - radius).coerceAtLeast(0)
        val bottom = (cy + radius).coerceAtMost(bitmap.height - 1)

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy > radius * radius) continue
                val pixel = bitmap.getPixel(x, y)
                val luminance = Color.red(pixel) * 0.299 +
                        Color.green(pixel) * 0.587 +
                        Color.blue(pixel) * 0.114
                sum += (255 - luminance)
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }
}
