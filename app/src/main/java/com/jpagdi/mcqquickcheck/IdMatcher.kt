package com.jpagdi.mcqquickcheck

import kotlin.math.ln
import kotlin.math.pow

/**
 * v1.2.0 — picks which enrolled learner a scan belongs to from a (possibly
 * imperfect) ID read, by scoring EVERY ID in the assigned classes' rosters
 * (only those exactly as long as the sheet's ID row) against the per-digit
 * probabilities and keeping the best.
 *
 * Why match against the roster instead of trusting the raw digits: even a
 * 97%-per-digit reader gets a 12-digit free-form ID right only ~70% of the
 * time, but the candidates here are ~44 known IDs, so a misread digit or two
 * rarely changes which one is closest.
 *
 * Auto-link ("silent") only when ALL hold:
 *  - at least minReadable(digits) boxes had a readable digit (2/3, min 3)
 *  - the best candidate explains the read well (mean log-prob >= MIN_MEAN_LOGP)
 *  - it beats the runner-up by MARGIN nats (~55:1 odds)
 * Otherwise the scan is flagged "Needs ID check" with the top candidates.
 *
 * IMPORTANT LIMIT (found in simulation, tools/id_model/simulate_matcher.py):
 * when learners' IDs are numbered SEQUENTIALLY (neighbors differ by only the
 * last digit or two), a single clearly-read digit can't clear MARGIN, so
 * nearly every scan is flagged for a one-tap confirmation instead of linking
 * silently. That is deliberate — loosening MARGIN to ~2 links 90%+ silently
 * but, in simulation, mislinks 1-3% (a score on the wrong learner). Random-
 * looking IDs (like DepEd LRNs) link silently almost always. Re-tune against
 * real scans before loosening.
 */
object IdMatcher {

    private const val TEMPER = 0.5     // p -> p^TEMPER, renormalized: softens overconfident reads
    private const val EPS = 1e-3
    private const val MIN_MEAN_LOGP = -1.4
    private const val MARGIN = 4.0
    private const val ALREADY_SCORED_PENALTY = 2.0
    private const val MAX_CANDIDATES = 3

    /** At least 2/3 of the boxes readable, never fewer than 3. */
    fun minReadable(digits: Int): Int = maxOf(3, kotlin.math.ceil(digits * 2.0 / 3.0).toInt())

    data class Match(
        /** Best roster ID, or null if there was nothing to match against / nothing read. */
        val bestId: String?,
        /** True only when it is safe to link without asking. */
        val confident: Boolean,
        /** Up to MAX_CANDIDATES best roster IDs, best first. */
        val candidates: List<String>
    )

    fun match(read: IdReader.Read, rosterIds: List<String>, alreadyScored: Set<String> = emptySet()): Match {
        val digits = read.probs.size
        val roster = rosterIds.filter { it.length == digits }.distinct()
        if (roster.isEmpty() || read.readableCount == 0) return Match(null, false, emptyList())

        // Tempered, renormalized probabilities per readable position.
        val tempered: List<DoubleArray?> = read.probs.map { p ->
            if (p == null) null else {
                val q = DoubleArray(10) { p[it].toDouble().pow(TEMPER) }
                val sum = q.sum()
                for (i in q.indices) q[i] /= sum
                q
            }
        }

        val scored = roster.map { learnerId ->
            var total = 0.0
            for (i in tempered.indices) {
                val q = tempered[i] ?: continue
                val d = learnerId[i] - '0'
                total += ln(maxOf(q[d], EPS))
            }
            learnerId to (total - if (learnerId in alreadyScored) ALREADY_SCORED_PENALTY else 0.0)
        }.sortedByDescending { it.second }

        val best = scored[0]
        val secondScore = if (scored.size > 1) scored[1].second else Double.NEGATIVE_INFINITY
        val readable = read.readableCount
        val meanLogP = best.second / readable
        val confident = readable >= minReadable(digits) &&
            meanLogP >= MIN_MEAN_LOGP &&
            (best.second - secondScore) >= MARGIN

        return Match(best.first, confident, scored.take(MAX_CANDIDATES).map { it.first })
    }
}
