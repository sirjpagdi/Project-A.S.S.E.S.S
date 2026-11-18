package com.jpagdi.mcqquickcheck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One competency group as configured via Item Analysis's "Set Up
 *  Competencies" screen — a name plus the set of 1-based item numbers a
 *  teacher checked off as covering it. Persisted on SavedTest below so
 *  it's remembered per test, same lifecycle as the answer key. Top-level
 *  (not nested in ItemAnalysis) since it's test configuration data, not
 *  an analysis result. */
data class Competency(
    val name: String,
    val itemNumbers: Set<Int>
)

data class SavedTest(
    val template: SheetTemplate,
    val answerKey: List<Int>,
    val paperLayout: PaperLayout = PaperLayout.QUARTER,
    // Points awarded for each question, in order. Defaults to 1 pt each when
    // not customized. Size should match template.questionCount once a key
    // has been created.
    val points: List<Int> = List(answerKey.size) { 1 },
    // v1.1.1: optional — see Competency's own doc comment above. Empty by
    // default; ItemAnalysis.compute() treats an empty list as "this
    // feature was never set up for this test" (see its own doc comment).
    val competencies: List<Competency> = emptyList(),
    // v1.2.0 (Classes feature): ids of the enrolled classes (ClassStorage)
    // this exam was assigned to. Empty = no classes, i.e. the feature is
    // simply not in play for this test. Saved only when non-empty, same
    // optional-key pattern as competencies above.
    val classIds: List<String> = emptyList()
) {
    val totalPoints: Int get() = points.sum()
}

/** Holds the test currently loaded in MainActivity, so ScanActivity knows what to grade against. */
object ActiveTest {
    var current: SavedTest? = null
}

/**
 * Stores/loads full SheetTemplate + answer key together, per named test, using
 * SharedPreferences with hand-rolled JSON (no external serialization library
 * needed for this small amount of data).
 */
object TestStorage {
    private const val PREFS = "mcq_scanner_tests"

    /** v3.4: thrown by loadTest() below for a saved test whose stored
     *  paperLayout string is one of PaperLayout.LEGACY_LAYOUT_NAMES
     *  (HALF_CROSS/HALF_LENGTH, removed entirely — see PaperLayout.kt's
     *  doc comment for why this is a REJECTION, not an auto-migration).
     *  Distinct from loadTest() returning plain null (test genuinely
     *  doesn't exist / SharedPreferences entry missing) so a caller that
     *  actually needs to explain the failure to the teacher (currently
     *  just TestDetailActivity, the "open this specific test" screen) can
     *  tell the two apart and show a real message instead of a silent
     *  finish(). Every OTHER call site in the app uses loadTestOrNull()
     *  below instead, which collapses both cases back to null — those are
     *  background/incidental lookups (backup export, item analysis,
     *  scanned-sheet layout checks, etc.) where surfacing this distinction
     *  isn't actionable for the teacher at that point in the flow. */
    class LegacyLayoutException(val testName: String, val legacyLayoutName: String) :
        Exception("\"$testName\" was created with a paper layout (${legacyLayoutName}) that no longer exists. Please recreate this test.")

    fun saveTest(context: Context, test: SavedTest) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = toJson(test)
        json.put("savedAtMillis", System.currentTimeMillis())
        prefs.edit().putString(test.template.testName, json.toString()).apply()
    }

    /** v1.1.1: used by Item Analysis's "Set Up Competencies" screen —
     *  loads the test, replaces just its competencies list, saves it
     *  back. Silently does nothing if the test can't be loaded (e.g. it
     *  was deleted from another screen while this one was open). */
    fun saveCompetencies(context: Context, testName: String, competencies: List<Competency>) {
        val current = loadTestOrNull(context, testName) ?: return
        saveTest(context, current.copy(competencies = competencies))
    }

    /** @throws LegacyLayoutException if this test was saved with a removed
     *  PaperLayout value — see that class's doc comment. Most callers
     *  should use loadTestOrNull() instead unless they specifically need
     *  to distinguish "removed layout" from "doesn't exist" for the
     *  teacher. */
    fun loadTest(context: Context, name: String): SavedTest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(name, null) ?: return null
        val obj = JSONObject(raw)
        val storedLayoutName = obj.optString("paperLayout", null)
        if (storedLayoutName != null && storedLayoutName in PaperLayout.LEGACY_LAYOUT_NAMES) {
            throw LegacyLayoutException(name, storedLayoutName)
        }
        return fromJson(obj)
    }

    /** Same as loadTest(), but collapses LegacyLayoutException back to
     *  null instead of throwing — for the many call sites across the app
     *  that already treat a null result as "nothing to show here" and
     *  don't have a place to surface a specific message to the teacher. */
    fun loadTestOrNull(context: Context, name: String): SavedTest? =
        try {
            loadTest(context, name)
        } catch (e: LegacyLayoutException) {
            null
        }

    data class TestSummary(val name: String, val savedAtMillis: Long, val preferencesLabel: String)

    /** Test names with their last-saved time, newest first. v3.4: a test
     *  whose stored paperLayout is one of PaperLayout.LEGACY_LAYOUT_NAMES
     *  shows a clear "(unavailable — old layout removed)" label here
     *  instead of silently resolving to QUARTER's label and then failing
     *  with no explanation when actually opened via loadTest(). */
    fun listTestSummaries(context: Context): List<TestSummary> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.mapNotNull { (name, value) ->
            val raw = value as? String ?: return@mapNotNull null
            val obj = try { JSONObject(raw) } catch (e: Exception) { return@mapNotNull null }
            val savedAt = obj.optLong("savedAtMillis", 0L)
            val count = obj.optInt("questionCount", 0)
            val choices = obj.optInt("choiceCount", 4)
            val storedLayoutName = obj.optString("paperLayout", null)
            val label = if (storedLayoutName != null && storedLayoutName in PaperLayout.LEGACY_LAYOUT_NAMES) {
                "$count questions, A–${('A' + choices - 1)} — unavailable, old layout removed (tap to see options)"
            } else {
                val layout = if (obj.has("paperLayout")) {
                    PaperLayout.fromStorageName(obj.optString("paperLayout"))
                } else {
                    when (obj.optInt("sheetsPerPage", 2)) {
                        4 -> PaperLayout.QUARTER
                        else -> PaperLayout.QUARTER
                    }
                }
                "$count questions, A–${('A' + choices - 1)}, ${layout.label}"
            }
            TestSummary(name, savedAt, label)
        }.sortedByDescending { it.savedAtMillis }
    }

    fun listTestNames(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.sorted()
    }

    fun testExists(context: Context, name: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(name)
    }

    fun deleteTest(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(name).apply()
        // v1.2.0: learner scores belong to the exam — deleting the test
        // removes them too (callers already confirm the delete with a dialog).
        // NOTE: TestDetailActivity's template-edit flow also calls this when
        // an edit RENAMES a test, immediately before re-saving under the new
        // name; it calls LearnerResultStorage.renameTestReferences first so
        // the results are moved off the old name before this runs.
        LearnerResultStorage.deleteForTest(context, name)
        ArchiveStore.forgetTest(context, name)
        SetKeyStore.delete(context, name)
        SetCompetencyStore.delete(context, name)
    }

    /** v1.1.0: wipes every saved test in one shot — used by
     *  RestoreDataActivity's "Clear Data and Continue" prompt before a
     *  fresh import, so a restore never has to merge against (or silently
     *  collide with) whatever was already saved. Unlike looping
     *  deleteTest() per name, this also removes any stray keys that
     *  aren't a current test (there shouldn't be any, but a plain
     *  SharedPreferences .clear() is the only way to be certain nothing
     *  is left behind) — same reasoning as
     *  ScannedSheetStorage.clearAll()'s own doc comment. */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        LearnerResultStorage.clearAll(context)
        SetKeyStore.clearAll(context)
        SetCompetencyStore.clearAll(context)
    }

    /** Renames a saved test. testName is used as a lookup key in THREE
     *  places across this app, not just here — TestStorage's own
     *  SharedPreferences key (the test name string itself), each scan's
     *  "testName" field in ScannedSheetStorage's meta JSON (used to filter
     *  which scans belong to this test), and ScannedSheetStorage's
     *  explicit-folders key (FOLDERS_KEY_PREFIX + testName). Renaming here
     *  alone would silently orphan every existing scan and folder for this
     *  test — see ScannedSheetStorage.renameTestReferences, which the
     *  caller (MainActivity's promptRenameTest) is expected to call alongside this.
     *  Returns false without changing anything if newName is blank, already
     *  taken, or identical to the old name. */
    fun renameTest(context: Context, oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(trimmed)) return false // name already taken
        val raw = prefs.getString(oldName, null) ?: return false
        val obj = JSONObject(raw)
        obj.put("testName", trimmed) // the SheetTemplate's own serialized name field
        prefs.edit()
            .remove(oldName)
            .putString(trimmed, obj.toString())
            .apply()
        LearnerResultStorage.renameTestReferences(context, oldName, trimmed)
        ArchiveStore.renameTest(context, oldName, trimmed)
        SetKeyStore.rename(context, oldName, trimmed)
        SetCompetencyStore.rename(context, oldName, trimmed)
        return true
    }

    private fun toJson(test: SavedTest): JSONObject {
        val t = test.template
        val obj = JSONObject()
        obj.put("testName", t.testName)
        obj.put("questionCount", t.questionCount)
        obj.put("choiceCount", t.choiceCount)
        obj.put("numbering", t.numbering.name)
        obj.put("rowsPerBlock", t.rowsPerBlock)
        obj.put("maxBlocksPerRowGroup", t.maxBlocksPerRowGroup)
        obj.put("bubbleSizeMm", t.bubbleSizeMm.toDouble())
        // v1.2.0: number of ID digit boxes in the sheet's optional ID row.
        // Only written when > 0 — absent on every older test and on any test
        // made without the row, which fromJson() reads as 0 (no row).
        if (t.idDigits > 0) obj.put("idDigits", t.idDigits)
        if (t.setCount > 1) obj.put("setCount", t.setCount)
        if (test.classIds.isNotEmpty()) obj.put("classIds", JSONArray(test.classIds))
        obj.put("paperLayout", test.paperLayout.name)
        obj.put("answerKey", JSONArray(test.answerKey))
        obj.put("points", JSONArray(test.points))
        // v1.1.1: only written when actually configured, matching the
        // optional-field pattern used elsewhere in this file (e.g.
        // bubbleSizeMm) — an absent key, not an empty array, is what
        // fromJson()/BackupDataActivity/RestoreDataActivity all treat as
        // "never set up," consistent with ItemAnalysis.compute()'s own
        // empty-vs-configured distinction.
        if (test.competencies.isNotEmpty()) {
            val compArray = JSONArray()
            test.competencies.forEach { comp ->
                val compObj = JSONObject()
                compObj.put("name", comp.name)
                compObj.put("items", JSONArray(comp.itemNumbers.sorted()))
                compArray.put(compObj)
            }
            obj.put("competencies", compArray)
        }
        return obj
    }

    private fun fromJson(obj: JSONObject): SavedTest {
        val template = SheetTemplate(
            testName = obj.getString("testName"),
            questionCount = obj.getInt("questionCount"),
            choiceCount = obj.getInt("choiceCount"),
            numbering = NumberingOrder.valueOf(obj.getString("numbering")),
            rowsPerBlock = obj.optInt("rowsPerBlock", 10),
            maxBlocksPerRowGroup = obj.optInt("maxBlocksPerRowGroup", 3),
            // Older saved tests (pre-v3.3) won't have this — falls back to
            // the same default BubbleSizePrefs.DEFAULT_MM new templates use.
            bubbleSizeMm = obj.optDouble("bubbleSizeMm", BubbleSizePrefs.DEFAULT_MM.toDouble()).toFloat(),
            idDigits = obj.optInt("idDigits", 0).let { if (it == 0) 0 else SheetTemplate.clampIdDigits(it) },
            setCount = SheetTemplate.clampSetCount(obj.optInt("setCount", 1))
        )
        val keyArray = obj.getJSONArray("answerKey")
        val key = (0 until keyArray.length()).map { keyArray.getInt(it) }
        // Older saved tests won't have a "points" array — default to 1 pt each.
        val points = if (obj.has("points")) {
            val pointsArray = obj.getJSONArray("points")
            (0 until pointsArray.length()).map { pointsArray.getInt(it) }
        } else {
            List(key.size) { 1 }
        }
        // Older saved tests have "sheetsPerPage" (an Int: 1/2/4) instead of the
        // newer "paperLayout" enum name — falls back to QUARTER, the only
        // PaperLayout value remaining as of v3.4 (was HALF_CROSS before
        // HALF_CROSS/HALF_LENGTH were removed — see PaperLayout.kt).
        // A LEGACY name (HALF_CROSS/HALF_LENGTH stored explicitly) is
        // already caught and thrown as LegacyLayoutException by loadTest()
        // BEFORE this function is ever called, so PaperLayout.fromStorageName
        // below only ever sees either "QUARTER" or a genuinely missing/
        // unrecognized value at this point, both of which correctly
        // resolve to QUARTER via its own ?: fallback.
        val layout = if (obj.has("paperLayout")) {
            PaperLayout.fromStorageName(obj.optString("paperLayout"))
        } else {
            PaperLayout.QUARTER
        }
        // v1.1.1: absent on any test saved before this feature, or any
        // test where "Set Up Competencies" was simply never used.
        val competencies = if (obj.has("competencies")) {
            val compArray = obj.getJSONArray("competencies")
            (0 until compArray.length()).map { i ->
                val compObj = compArray.getJSONObject(i)
                val itemsArray = compObj.getJSONArray("items")
                Competency(
                    name = compObj.getString("name"),
                    itemNumbers = (0 until itemsArray.length()).map { itemsArray.getInt(it) }.toSet()
                )
            }
        } else {
            emptyList()
        }
        val classIds = if (obj.has("classIds")) {
            val idArray = obj.getJSONArray("classIds")
            (0 until idArray.length()).map { idArray.getString(it) }
        } else {
            emptyList()
        }
        return SavedTest(template, key, layout, points, competencies, classIds)
    }
}
