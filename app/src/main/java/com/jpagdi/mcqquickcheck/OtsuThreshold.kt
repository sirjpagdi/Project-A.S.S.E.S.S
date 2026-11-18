package com.jpagdi.mcqquickcheck

/**
 * Otsu's method finds the threshold that best separates a set of numeric
 * values into two clusters (by maximizing the variance between them). Used
 * by BubbleGrader to adapt the "filled vs blank" darkness threshold to each
 * captured photo's own lighting/exposure, instead of assuming a fixed
 * absolute brightness — a photo taken in dim light reads darker across the
 * board (including blank bubbles), so a fixed threshold either misses real
 * marks in low light or false-positives on faint paper texture/shadows in
 * bright light. A real answer sheet's own darkness scores are naturally
 * bimodal (mostly-unmarked bubbles vs. the one filled choice per question),
 * so Otsu can find a good split point specific to that photo.
 */
object OtsuThreshold {

    /**
     * Result of [compute]: the split point Otsu found, plus how confident
     * that split actually is.
     *
     * separability = inter-class variance / total variance (Fisher's
     * separation criterion, the standard way to describe how "clean" an
     * Otsu split is). Ranges roughly 0..1:
     *   - near 1.0: the values really do fall into two well-separated
     *     clusters (e.g. mostly-blank bubbles vs. one clearly-filled one) —
     *     Otsu's split point is trustworthy.
     *   - near 0.0: the values are more like one smeared/unimodal blob with
     *     no real two-class structure — Otsu will still return SOME number
     *     (it always picks the best of a bad set of options), but that
     *     number is close to arbitrary and shouldn't be trusted on its own.
     *
     * CHOSEN OVER THE ALTERNATIVES (valley depth, histogram entropy):
     * inter-class variance is already 90% computed by the existing loop
     * below (weightBackground/weightForeground/meanBackground/
     * meanForeground) — valley depth would need a second pass over the
     * histogram to find the actual local minimum near bestBin, and entropy
     * needs its own separate formula. This one is the smallest, most
     * directly-justified addition to an algorithm that already computes
     * the between-class variance as its core operation — the confidence
     * score is genuinely "how strong was the winning split," not a
     * separately bolted-on metric.
     */
    data class Result(val threshold: Double, val separability: Double)

    /**
     * Returns the value that best separates [values] into two clusters
     * (plus a confidence score for that split), or null if there aren't
     * enough values to make that meaningful.
     */
    fun compute(values: List<Double>, rangeMin: Double = 0.0, rangeMax: Double = 255.0, bins: Int = 64): Result? {
        if (values.size < 4) return null
        val binWidth = (rangeMax - rangeMin) / bins
        if (binWidth <= 0) return null

        val histogram = IntArray(bins)
        values.forEach { v ->
            val bin = ((v - rangeMin) / binWidth).toInt().coerceIn(0, bins - 1)
            histogram[bin]++
        }

        val total = values.size
        var sum = 0.0
        for (i in 0 until bins) sum += i * histogram[i]
        val globalMean = sum / total

        // Total variance of the whole (binned) distribution — the
        // denominator for the separability ratio. Computed once, up front,
        // separately from the per-split loop below (which only tracks the
        // BETWEEN-class variance at each candidate split).
        var totalVariance = 0.0
        for (i in 0 until bins) {
            totalVariance += histogram[i] * (i - globalMean) * (i - globalMean)
        }
        totalVariance /= total

        var sumB = 0.0
        var weightBackground = 0
        var maxVariance = -1.0
        var bestBin = -1

        for (i in 0 until bins) {
            weightBackground += histogram[i]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumB += i * histogram[i]
            val meanBackground = sumB / weightBackground
            val meanForeground = (sum - sumB) / weightForeground

            val variance = weightBackground.toDouble() * weightForeground.toDouble() *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)

            if (variance > maxVariance) {
                maxVariance = variance
                bestBin = i
            }
        }

        if (bestBin < 0) return null

        // maxVariance above is weightBackground*weightForeground*(meanDiff)^2
        // (an unnormalized between-class variance sum, not divided by
        // total^2 yet) — normalize it the same way totalVariance was
        // computed (divided by total) so the ratio below is comparable.
        val betweenClassVariance = (maxVariance / total) / total
        val separability = if (totalVariance > 0.0) (betweenClassVariance / totalVariance).coerceIn(0.0, 1.0) else 0.0

        return Result(rangeMin + (bestBin + 1) * binWidth, separability)
    }
}
