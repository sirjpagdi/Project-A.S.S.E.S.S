package com.jpagdi.mcqquickcheck

import android.content.Context
import kotlin.math.roundToInt

/**
 * v1.2.4 — read-only view over the results the app already saves, grouped by
 * learner ID instead of by test. Nothing new is stored: LearnerResultStorage
 * keeps one result per (test, learner), and this just walks every test once.
 * Powers the Learners list and each learner's profile.
 */
object LearnerData {

    data class Row(val testName: String, val result: LearnerResult)

    /** learnerId -> every saved result for that ID across all tests, newest first. */
    fun byLearner(context: Context): Map<String, List<Row>> {
        val out = HashMap<String, MutableList<Row>>()
        for (name in TestStorage.listTestNames(context)) {
            for (r in LearnerResultStorage.listResults(context, name)) {
                out.getOrPut(r.learnerId) { ArrayList() }.add(Row(name, r))
            }
        }
        out.values.forEach { rows -> rows.sortByDescending { it.result.savedAtMillis } }
        return out
    }

    fun percent(r: LearnerResult): Double =
        if (r.totalPoints > 0) r.earnedPoints * 100.0 / r.totalPoints else 0.0

    fun percentText(r: LearnerResult): String = "${percent(r).roundToInt()}%"

    /** Mean of the per-test percentages (each test counts equally), or null with no results. */
    fun average(rows: List<Row>): Double? =
        if (rows.isEmpty()) null else rows.map { percent(it.result) }.average()
}
