package com.jpagdi.mcqquickcheck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One learner's score on one exam. At most one per (testName, learnerId). */
data class LearnerResult(
    val testName: String,
    val classId: String,
    val learnerId: String,
    val earnedPoints: Int,
    val totalPoints: Int,
    /** ScannedSheetStorage id of the saved scan, or null when the scan wasn't saved. */
    val scanId: String?,
    val savedAtMillis: Long
)

/** A graded scan whose ID couldn't be matched confidently — kept so the
 *  teacher can pick the learner later from the class sheet. [candidateIds]
 *  are the best roster matches in order; [readDigits] is the raw 12-char
 *  read ('?' where a digit was unreadable), shown to help the teacher. */
data class PendingLink(
    val id: String,
    val testName: String,
    val earnedPoints: Int,
    val totalPoints: Int,
    val scanId: String?,
    val savedAtMillis: Long,
    val candidateIds: List<String>,
    val readDigits: String,
    /** IdReader.Read.encode() of this scan's ID read, or "" (older records). */
    val readProbs: String = ""
)

/**
 * v1.2.0 (Classes feature) — per-exam learner scores and not-yet-linked
 * scans. SharedPreferences + JSON like the rest of the app: two keys per
 * test, "results:<testName>" and "pending:<testName>", each a JSON array.
 *
 * Kept deliberately separate from ScannedSheetStorage's SavedScan: autosave
 * is a Settings toggle, but a learner's score must be recorded whether or not
 * the scan image is saved. [LearnerResult.scanId] links to the image when one
 * exists.
 *
 * Local only — never part of the license sync or the diagnostic report.
 */
object LearnerResultStorage {
    private const val PREFS = "mcq_scanner_learner_results"
    private const val RESULTS_PREFIX = "results:"
    private const val PENDING_PREFIX = "pending:"

    // --- results -----------------------------------------------------------

    fun listResults(context: Context, testName: String): List<LearnerResult> =
        readResults(context, testName)

    fun getResult(context: Context, testName: String, learnerId: String): LearnerResult? =
        readResults(context, testName).firstOrNull { it.learnerId == learnerId }

    /** Inserts or REPLACES the learner's result for that test. Callers that
     *  could overwrite a real score (a rescan) must ask the teacher first —
     *  see ClassLinker. */
    fun upsertResult(context: Context, result: LearnerResult) {
        val others = readResults(context, result.testName).filter { it.learnerId != result.learnerId }
        writeResults(context, result.testName, others + result)
    }

    fun removeResult(context: Context, testName: String, learnerId: String) {
        writeResults(context, testName, readResults(context, testName).filter { it.learnerId != learnerId })
    }

    // --- pending -----------------------------------------------------------

    fun listPending(context: Context, testName: String): List<PendingLink> =
        readPending(context, testName)

    fun addPending(
        context: Context, testName: String, earned: Int, total: Int, scanId: String?,
        candidateIds: List<String>, readDigits: String, readProbs: String = ""
    ): PendingLink {
        val p = PendingLink(
            UUID.randomUUID().toString(), testName, earned, total, scanId,
            System.currentTimeMillis(), candidateIds, readDigits, readProbs
        )
        writePending(context, testName, readPending(context, testName) + p)
        return p
    }

    fun removePending(context: Context, testName: String, id: String) {
        writePending(context, testName, readPending(context, testName).filter { it.id != id })
    }

    /** Updates the score of a not-yet-linked scan (the teacher resolved an
     *  ambiguous bubble after the scan was filed). */
    fun updatePendingScore(context: Context, testName: String, id: String, earned: Int, total: Int) {
        val list = readPending(context, testName)
        if (list.none { it.id == id }) return
        writePending(context, testName, list.map {
            if (it.id == id) it.copy(earnedPoints = earned, totalPoints = total) else it
        })
    }

    /** A re-check re-read this pending scan's ID: refresh what the "Needs ID
     *  check" card shows (the digits as read, and the best-guess learners). */
    fun updatePendingRead(context: Context, testName: String, id: String, candidateIds: List<String>, readDigits: String, readProbs: String = "") {
        val list = readPending(context, testName)
        if (list.none { it.id == id }) return
        writePending(context, testName, list.map {
            if (it.id == id) it.copy(candidateIds = candidateIds, readDigits = readDigits, readProbs = readProbs.ifEmpty { it.readProbs }) else it
        })
    }

    /** A scan image was deleted: drop the score recorded from it, and any
     *  pending link that pointed at it, so a deleted scan never leaves a
     *  stray score behind. Called from ScannedSheetStorage.deleteScan. */
    fun removeByScanId(context: Context, scanId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (key in prefs.all.keys.toList()) {
            when {
                key.startsWith(RESULTS_PREFIX) -> {
                    val name = key.removePrefix(RESULTS_PREFIX)
                    val list = readResults(context, name)
                    if (list.any { it.scanId == scanId }) writeResults(context, name, list.filter { it.scanId != scanId })
                }
                key.startsWith(PENDING_PREFIX) -> {
                    val name = key.removePrefix(PENDING_PREFIX)
                    val list = readPending(context, name)
                    if (list.any { it.scanId == scanId }) writePending(context, name, list.filter { it.scanId != scanId })
                }
            }
        }
    }

    // --- cascading cleanup ---------------------------------------------------

    fun deleteForTest(context: Context, testName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(RESULTS_PREFIX + testName)
            .remove(PENDING_PREFIX + testName)
            .apply()
    }

    /** Moves results/pending from [oldName] to [newName] when a test is renamed. */
    fun renameTestReferences(context: Context, oldName: String, newName: String) {
        if (oldName == newName) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val results = readResults(context, oldName).map { it.copy(testName = newName) }
        val pending = readPending(context, oldName).map { it.copy(testName = newName) }
        prefs.edit().remove(RESULTS_PREFIX + oldName).remove(PENDING_PREFIX + oldName).apply()
        if (results.isNotEmpty()) writeResults(context, newName, results)
        if (pending.isNotEmpty()) writePending(context, newName, pending)
    }

    fun deleteForClass(context: Context, classId: String) {
        for (testName in allTestNames(context)) {
            val kept = readResults(context, testName).filter { it.classId != classId }
            writeResults(context, testName, kept)
        }
    }

    fun deleteForLearner(context: Context, classId: String, learnerId: String) {
        for (testName in allTestNames(context)) {
            val kept = readResults(context, testName).filter { !(it.classId == classId && it.learnerId == learnerId) }
            writeResults(context, testName, kept)
        }
    }

    /** A learner's ID was corrected on the roster — carry their scores over. */
    fun changeId(context: Context, classId: String, oldId: String, newId: String) {
        for (testName in allTestNames(context)) {
            val list = readResults(context, testName)
            if (list.none { it.classId == classId && it.learnerId == oldId }) continue
            val moved = list.map { if (it.classId == classId && it.learnerId == oldId) it.copy(learnerId = newId) else it }
            // If the corrected ID somehow already has a score, keep one per ID (the moved one wins).
            val deduped = moved.filter { it.learnerId != newId || (it.classId == classId) }
                .distinctBy { it.learnerId }
            writeResults(context, testName, deduped)
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // --- Backup / Restore support -------------------------------------------

    fun exportAll(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val obj = JSONObject()
        for ((key, value) in prefs.all) {
            val raw = value as? String ?: continue
            try { obj.put(key, JSONArray(raw)) } catch (e: Exception) { /* skip malformed */ }
        }
        return obj
    }

    fun importAll(context: Context, obj: JSONObject) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith(RESULTS_PREFIX) && !key.startsWith(PENDING_PREFIX)) continue
            val arr = obj.optJSONArray(key) ?: continue
            edit.putString(key, arr.toString())
        }
        edit.apply()
    }

    // --- internals -------------------------------------------------------------

    private fun allTestNames(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.filter { it.startsWith(RESULTS_PREFIX) }.map { it.removePrefix(RESULTS_PREFIX) }
    }

    private fun readResults(context: Context, testName: String): List<LearnerResult> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(RESULTS_PREFIX + testName, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                LearnerResult(
                    testName = testName,
                    classId = o.optString("classId", ""),
                    learnerId = o.optString("learnerId", ""),
                    earnedPoints = o.optInt("earned", 0),
                    totalPoints = o.optInt("total", 0),
                    scanId = if (o.has("scanId") && !o.isNull("scanId")) o.optString("scanId") else null,
                    savedAtMillis = o.optLong("savedAt", 0L)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeResults(context: Context, testName: String, list: List<LearnerResult>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (list.isEmpty()) {
            prefs.edit().remove(RESULTS_PREFIX + testName).apply()
            return
        }
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
                .put("classId", it.classId).put("learnerId", it.learnerId)
                .put("earned", it.earnedPoints).put("total", it.totalPoints)
                .put("savedAt", it.savedAtMillis)
            if (it.scanId != null) o.put("scanId", it.scanId)
            arr.put(o)
        }
        prefs.edit().putString(RESULTS_PREFIX + testName, arr.toString()).apply()
    }

    private fun readPending(context: Context, testName: String): List<PendingLink> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(PENDING_PREFIX + testName, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val cands = o.optJSONArray("candidates") ?: JSONArray()
                PendingLink(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    testName = testName,
                    earnedPoints = o.optInt("earned", 0),
                    totalPoints = o.optInt("total", 0),
                    scanId = if (o.has("scanId") && !o.isNull("scanId")) o.optString("scanId") else null,
                    savedAtMillis = o.optLong("savedAt", 0L),
                    candidateIds = (0 until cands.length()).map { cands.getString(it) },
                    readDigits = o.optString("read", ""),
                    readProbs = o.optString("probs", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun writePending(context: Context, testName: String, list: List<PendingLink>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (list.isEmpty()) {
            prefs.edit().remove(PENDING_PREFIX + testName).apply()
            return
        }
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
                .put("id", it.id).put("earned", it.earnedPoints).put("total", it.totalPoints)
                .put("savedAt", it.savedAtMillis).put("candidates", JSONArray(it.candidateIds))
                .put("read", it.readDigits).put("probs", it.readProbs)
            if (it.scanId != null) o.put("scanId", it.scanId)
            arr.put(o)
        }
        prefs.edit().putString(PENDING_PREFIX + testName, arr.toString()).apply()
    }
}
