package com.jpagdi.mcqquickcheck

import kotlin.math.roundToInt

/**
 * Classical item analysis: difficulty (P) and discrimination (D) indices
 * per question, using Kelley's 27% high/low group split. Rubric and
 * recommendation text follow tlconestoga.ca's "Understanding Multiple
 * Choice Test Item Analysis Report from DataLink" (confirmed source, not
 * guessed) — a different, cleaner rubric than an earlier reference image
 * used for this feature; this one is authoritative per explicit direction.
 *
 * Needs SavedScan.perQuestion (added in v2.7 specifically for this
 * feature) — scans saved before v2.7 have this as null and are silently
 * excluded, same as any other backward-compat gap in this app. There's no
 * way to retroactively compute item-level stats for a scan that never
 * recorded per-question correctness.
 */
object ItemAnalysis {

    data class ItemStats(
        val questionNumber: Int, // 1-based, matches how questions are numbered on the sheet
        val correctHigh: Int,
        val correctLow: Int,
        val correctTotal: Int,
        val p: Double,
        val pLabel: String,
        val pRecommendation: String,
        val d: Double,
        val dLabel: String,
        val dRecommendation: String,
        // v1.1.0: test-result analysis — computed against ALL usable
        // takers (Result.totalUsable), NOT just the high/low 27% groups
        // above (correctHigh/correctLow/correctTotal/p/d are Kelley's-rule
        // figures and deliberately stay scoped to those two groups only —
        // see compute()'s own doc comment on why P/D use that subset).
        // This block is a separate, whole-class view of the same items.
        val correctAllTakers: Int,
        val percentCorrectAllTakers: Double,
        val masteryLevel: String,
        // 1 = least learned (lowest % correct across all takers), n = most
        // learned — set in a second pass once every item's percentage is
        // known, see compute() below. UNCHANGED by the v1.1.1 competency
        // work below — per explicit instruction, this convention stays
        // "least learned first" even though the new competency rank below
        // deliberately uses the opposite ("most learned first") convention.
        val leastLearnedRank: Int,
        // v1.1.1: which competency (if any) this item was assigned to via
        // "Set Up Competencies" — see Competency's own doc comment.
        // Empty string means the test has no competencies configured at
        // all (the feature is simply unused for this test — NOT the same
        // as "Uncategorized", which only appears once competencies exist
        // but this particular item wasn't assigned to any of them).
        val competencyName: String = "",
        val competencyMastery: String = "",
        // 1 = MOST learned/mastered competency, n = least — opposite
        // direction from leastLearnedRank above, per explicit instruction.
        // 0 when competencyName is "" (not applicable).
        val competencyRank: Int = 0
    )

    /** One row of the "Most/Least Learned Competencies" summary tables —
     *  see ItemAnalysisXlsxExporter for how these get laid out on the
     *  page. Built once per compute() call, independent of item order. */
    data class CompetencySummary(
        val name: String,
        val itemNumbers: List<Int>, // sorted ascending
        val percentCorrect: Double,
        val masteryLevel: String,
        val rank: Int // 1 = most learned/mastered
    )

    data class Result(
        val totalUsable: Int,      // scans that had perQuestion data and were actually used
        val totalExcluded: Int,    // scans skipped because they predate v2.7 (or lack scores)
        val highGroupSize: Int,
        val lowGroupSize: Int,
        val items: List<ItemStats>,
        // v1.1.1: empty when the test has no competencies configured —
        // callers (ItemAnalysisActivity, ItemAnalysisXlsxExporter) should
        // skip rendering anything competency-related in that case rather
        // than showing an empty/useless section.
        val competencySummaries: List<CompetencySummary> = emptyList()
    )

    /** Returns null if there isn't enough usable data to form meaningful
     *  high/low groups (need at least 2 usable scans — 1 high, 1 low).
     *  @param competencies optional — from SavedTest.competencies. Left
     *  empty by TestSheetBatchGenerator's stress-test call (that tool has
     *  no real test/competencies concept), which is intentional: passing
     *  nothing here means every item's competencyName stays "" and no
     *  competencySummaries get built, exactly as if the feature were
     *  never touched for that synthetic test. */
    fun compute(
        scans: List<ScannedSheetStorage.SavedScan>,
        questionCount: Int,
        competencies: List<Competency> = emptyList(),
        // v1.2.5 (Test sets): when given (the pooled "all sets" view), each
        // competency's figure is pooled across every scan using THAT scan's own
        // set's item numbers: set index -> that set's competencies. Item rows
        // are still numbered by [competencies] (the first set's numbering).
        competenciesBySet: Map<Int, List<Competency>>? = null
    ): Result? {
        val usable = scans.filter { it.perQuestion != null && it.earnedPoints != null }
        val excluded = scans.size - usable.size
        if (usable.size < 2 || questionCount <= 0) return null

        val sortedByScore = usable.sortedByDescending { it.earnedPoints!! }

        // Kelley's 27% rule. Coerced so high+low groups never overlap or
        // exceed the usable set, which matters at small N (a single
        // classroom test is nowhere near the 93-student example this
        // rubric is usually shown with) — 27% of a small N can round to
        // more than half the set if not guarded.
        val rawGroupSize = (usable.size * 0.27).roundToInt().coerceAtLeast(1)
        val groupSize = rawGroupSize.coerceAtMost(usable.size / 2)

        val highGroup = sortedByScore.take(groupSize)
        val lowGroup = sortedByScore.takeLast(groupSize)

        val items = (0 until questionCount).map { qIndex ->
            val correctHigh = highGroup.count { it.perQuestion!!.getOrNull(qIndex) == true }
            val correctLow = lowGroup.count { it.perQuestion!!.getOrNull(qIndex) == true }
            val correctTotal = correctHigh + correctLow

            val p = correctTotal.toDouble() / (highGroup.size + lowGroup.size)
            val d = (correctHigh.toDouble() / highGroup.size) - (correctLow.toDouble() / lowGroup.size)

            val (pLabel, pRec) = interpretDifficulty(p)
            val (dLabel, dRec) = interpretDiscrimination(d)

            // v1.1.0: whole-class figures — every usable taker, not just
            // the high/low groups used for p/d above.
            val correctAllTakers = usable.count { it.perQuestion!!.getOrNull(qIndex) == true }
            val percentCorrectAllTakers = correctAllTakers.toDouble() / usable.size

            ItemStats(
                questionNumber = qIndex + 1,
                correctHigh = correctHigh,
                correctLow = correctLow,
                correctTotal = correctTotal,
                p = p,
                pLabel = pLabel,
                pRecommendation = pRec,
                d = d,
                dLabel = dLabel,
                dRecommendation = dRec,
                correctAllTakers = correctAllTakers,
                percentCorrectAllTakers = percentCorrectAllTakers,
                masteryLevel = interpretMastery(percentCorrectAllTakers),
                leastLearnedRank = 0 // placeholder — filled in below, once every item's % is known
            )
        }

        // v1.1.0: Least Learned (Rank) — 1 = least learned (lowest % correct
        // across all takers) up to n = most learned. Ties (equal %) broken
        // by question number so the ranking is deterministic and every
        // question still gets a distinct rank 1..n, not a shared one.
        val rankByQuestionNumber = items
            .sortedWith(compareBy({ it.percentCorrectAllTakers }, { it.questionNumber }))
            .mapIndexed { index, item -> item.questionNumber to (index + 1) }
            .toMap()
        val rankedItems = items.map { it.copy(leastLearnedRank = rankByQuestionNumber.getValue(it.questionNumber)) }

        // v1.1.1: competency assignment + grouping. Every item not
        // covered by any configured competency falls into an implicit
        // "Uncategorized" bucket — but ONLY once at least one competency
        // has been configured; with none configured at all, competencyName
        // stays "" (see ItemStats' own doc comment on that distinction).
        val competencyNameByItem: Map<Int, String> = if (competencies.isEmpty()) {
            emptyMap()
        } else {
            val map = mutableMapOf<Int, String>()
            // First competency to claim an item number wins — matches the
            // "Set Up Competencies" UI disabling already-claimed items
            // when assigning a later competency, so this should never
            // actually need to arbitrate a real conflict in practice.
            competencies.forEach { comp -> comp.itemNumbers.forEach { itemNo -> map.putIfAbsent(itemNo, comp.name) } }
            (1..questionCount).forEach { itemNo -> map.putIfAbsent(itemNo, "Uncategorized") }
            map
        }

        val itemsWithCompetencyName = rankedItems.map { item ->
            item.copy(competencyName = competencyNameByItem[item.questionNumber] ?: "")
        }

        val competencySummariesBase: List<CompetencySummary> = if (competencies.isEmpty()) {
            emptyList()
        } else {
            val unranked = itemsWithCompetencyName
                .groupBy { it.competencyName }
                .map { (name, groupItems) ->
                    val totalCorrect = groupItems.sumOf { it.correctAllTakers }
                    val totalPossible = groupItems.size * usable.size
                    val percent = if (totalPossible > 0) totalCorrect.toDouble() / totalPossible else 0.0
                    CompetencySummary(
                        name = name,
                        itemNumbers = groupItems.map { it.questionNumber }.sorted(),
                        percentCorrect = percent,
                        masteryLevel = interpretMastery(percent),
                        rank = 0 // placeholder, filled below
                    )
                }
            // Rank 1 = MOST learned/mastered (highest % correct) — the
            // opposite convention from leastLearnedRank above, per
            // explicit instruction ("YES let us follow Most Learned
            // instead" for this column specifically). Ties broken by
            // name for determinism.
            val rankByName = unranked
                .sortedWith(compareByDescending<CompetencySummary> { it.percentCorrect }.thenBy { it.name })
                .mapIndexed { index, summary -> summary.name to (index + 1) }
                .toMap()
            unranked.map { it.copy(rank = rankByName.getValue(it.name)) }
        }

        // v1.2.5: pooled across sets. Each scan contributes its own set's
        // item numbers; names come from the test's own competencies.
        val competencySummaries: List<CompetencySummary> =
            if (competenciesBySet != null && competencySummariesBase.isNotEmpty()) {
                val correctByName = mutableMapOf<String, Int>()
                val attemptsByName = mutableMapOf<String, Int>()
                usable.forEach { scan ->
                    val comps = competenciesBySet[scan.setIndex] ?: competencies
                    val claimed = mutableSetOf<Int>()
                    comps.forEach { comp ->
                        comp.itemNumbers.forEach { itemNo ->
                            if (itemNo in 1..questionCount && claimed.add(itemNo)) {
                                attemptsByName[comp.name] = (attemptsByName[comp.name] ?: 0) + 1
                                if (scan.perQuestion!!.getOrNull(itemNo - 1) == true) {
                                    correctByName[comp.name] = (correctByName[comp.name] ?: 0) + 1
                                }
                            }
                        }
                    }
                    (1..questionCount).filter { it !in claimed }.forEach { itemNo ->
                        attemptsByName["Uncategorized"] = (attemptsByName["Uncategorized"] ?: 0) + 1
                        if (scan.perQuestion!!.getOrNull(itemNo - 1) == true) {
                            correctByName["Uncategorized"] = (correctByName["Uncategorized"] ?: 0) + 1
                        }
                    }
                }
                val unrankedPooled = competencySummariesBase.map { base ->
                    val attempts = attemptsByName[base.name] ?: 0
                    val percent = if (attempts > 0) (correctByName[base.name] ?: 0).toDouble() / attempts else 0.0
                    base.copy(percentCorrect = percent, masteryLevel = interpretMastery(percent))
                }
                val rankPooled = unrankedPooled
                    .sortedWith(compareByDescending<CompetencySummary> { it.percentCorrect }.thenBy { it.name })
                    .mapIndexed { index, summary -> summary.name to (index + 1) }
                    .toMap()
                unrankedPooled.map { it.copy(rank = rankPooled.getValue(it.name)) }
            } else {
                competencySummariesBase
            }

        val summaryByCompetencyName = competencySummaries.associateBy { it.name }
        val finalItems = itemsWithCompetencyName.map { item ->
            val summary = summaryByCompetencyName[item.competencyName]
            item.copy(
                competencyMastery = summary?.masteryLevel ?: "",
                competencyRank = summary?.rank ?: 0
            )
        }

        return Result(
            totalUsable = usable.size,
            totalExcluded = excluded,
            highGroupSize = highGroup.size,
            lowGroupSize = lowGroup.size,
            items = finalItems,
            competencySummaries = competencySummaries
        )
    }

    /** Mastery Level bands, per explicit request. */
    private fun interpretMastery(percentCorrect: Double): String = when {
        percentCorrect >= 0.75 -> "Mastered"
        percentCorrect >= 0.50 -> "Nearly Mastered"
        else -> "Below Mastery"
    }

    /** Difficulty Index (P) bands — tlconestoga.ca. */
    private fun interpretDifficulty(p: Double): Pair<String, String> = when {
        p <= 0.20 -> "Too difficult" to "Likely too hard or confusing; check for clarity or unfair difficulty."
        p <= 0.39 -> "Difficult" to "May be too challenging; consider revising if too many students struggle."
        p <= 0.59 -> "Ideal difficulty range" to "Best range for assessing student performance effectively."
        p <= 0.80 -> "Moderately easy" to "Generally acceptable but may not effectively differentiate students."
        else -> "Very easy" to "Consider revising or removing if too many questions are this easy."
    }

    /** Discrimination Index (D) bands — tlconestoga.ca. */
    private fun interpretDiscrimination(d: Double): Pair<String, String> = when {
        d < 0.0 -> "Misleading or flawed" to "Something is wrong — strong students are getting it wrong while weaker students get it right. Needs revision or removal."
        d < 0.10 -> "Poor discrimination" to "Should be removed or rewritten."
        d < 0.20 -> "Weak discrimination" to "Likely ineffective; should be improved or replaced."
        d < 0.30 -> "Moderate discrimination" to "Consider revising; may not differentiate well."
        d < 0.40 -> "Acceptable discrimination" to "Acceptable and useful in most tests."
        else -> "Excellent discrimination" to "The item strongly differentiates between high and low performers."
    }
}
