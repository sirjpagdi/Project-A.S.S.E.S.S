package com.jpagdi.mcqquickcheck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One enrolled learner. [learnerId] is the school's identification number
 *  for the learner (ID_MIN_DIGITS..ID_MAX_DIGITS digits — a DepEd LRN is 12),
 *  always stored as TEXT, never a number: a leading zero must survive.
 *  [gender] is "M", "F", or "" when the roster didn't say. */
data class Learner(val learnerId: String, val name: String, val gender: String = "")

/** One enrolled class (a section/roster). [id] is a random UUID, NOT the
 *  class name, so renaming a class never orphans the exams and results that
 *  reference it. */
data class LearnerClass(
    val id: String,
    val name: String,
    val learners: List<Learner>
)

/**
 * v1.2.0 (Classes feature) — local storage for enrolled classes and their
 * rosters. Same approach as TestStorage: SharedPreferences with hand-rolled
 * JSON, one entry per class (key = class id). Local only — the roster holds
 * real names and IDs of minors, so it is never sent anywhere: not by the
 * license sync (LicenseApi sends only deviceId + scan counts), not in the
 * diagnostic report. It travels only inside the teacher's own Backup file.
 */
object ClassStorage {
    private const val PREFS = "mcq_scanner_classes"

    /** Strips everything but digits ("1366 5301-5012" -> "136653015012"). */
    fun digitsOnly(raw: String): String = raw.filter { it in '0'..'9' }

    /** A usable ID is ID_MIN_DIGITS..ID_MAX_DIGITS digits (spaces/dashes
     *  ignored). Returns the digits, or null. */
    fun normalizeId(raw: String): String? {
        val digits = digitsOnly(raw)
        return if (digits.length in SheetTemplate.ID_MIN_DIGITS..SheetTemplate.ID_MAX_DIGITS) digits else null
    }

    /** "M"/"F" from the many spellings a roster uses; "" if unrecognized/blank. */
    fun normalizeGender(raw: String): String {
        return when (raw.trim().lowercase().trimEnd('.')) {
            "m", "male", "boy", "lalaki", "man" -> "M"
            "f", "female", "girl", "babae", "woman" -> "F"
            else -> ""
        }
    }

    /** True when [raw] is non-blank but normalizeGender couldn't read it. */
    fun isUnrecognizedGender(raw: String): Boolean = raw.isNotBlank() && normalizeGender(raw).isEmpty()

    /** How many learners in [cls] have an ID that is NOT [digits] long — they
     *  can't be matched to an exam whose sheet has a [digits]-digit ID row. */
    fun countLengthMismatch(cls: LearnerClass, digits: Int): Int = cls.learners.count { it.learnerId.length != digits }

    fun listClasses(context: Context): List<LearnerClass> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.mapNotNull { (id, value) ->
            val raw = value as? String ?: return@mapNotNull null
            try { fromJson(id, JSONObject(raw)) } catch (e: Exception) { null }
        }.sortedBy { it.name.lowercase() }
    }

    fun getClass(context: Context, id: String): LearnerClass? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(id, null) ?: return null
        return try { fromJson(id, JSONObject(raw)) } catch (e: Exception) { null }
    }

    fun getClasses(context: Context, ids: List<String>): List<LearnerClass> =
        ids.mapNotNull { getClass(context, it) }

    fun classNameExists(context: Context, name: String, exceptId: String? = null): Boolean =
        listClasses(context).any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }

    /** Creates an empty class. Returns null if [name] is blank or already used. */
    fun createClass(context: Context, name: String): LearnerClass? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || classNameExists(context, trimmed)) return null
        val created = LearnerClass(UUID.randomUUID().toString(), trimmed, emptyList())
        saveClass(context, created)
        return created
    }

    fun saveClass(context: Context, cls: LearnerClass) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(cls.id, toJson(cls).toString()).apply()
    }

    /** Renames in place (id unchanged). False if blank or the name is taken. */
    fun renameClass(context: Context, id: String, newName: String): Boolean {
        val trimmed = newName.trim()
        val cls = getClass(context, id) ?: return false
        if (trimmed.isEmpty() || classNameExists(context, trimmed, exceptId = id)) return false
        saveClass(context, cls.copy(name = trimmed))
        return true
    }

    /** Deletes the class AND every learner score recorded against it, and
     *  drops it from any exam's assigned-class list (callers confirm first). */
    fun deleteClass(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(id).apply()
        LearnerResultStorage.deleteForClass(context, id)
        ArchiveStore.forgetClass(context, id)
        // Unassign from exams so no test keeps pointing at a missing class.
        for (name in TestStorage.listTestNames(context)) {
            val test = TestStorage.loadTestOrNull(context, name) ?: continue
            if (id in test.classIds) {
                TestStorage.saveTest(context, test.copy(classIds = test.classIds - id))
            }
        }
    }

    /** Adds or replaces (matched by ID) one learner in a class. */
    fun upsertLearner(context: Context, classId: String, learner: Learner) {
        val cls = getClass(context, classId) ?: return
        val others = cls.learners.filter { it.learnerId != learner.learnerId }
        saveClass(context, cls.copy(learners = (others + learner).sortedBy { it.name.lowercase() }))
    }

    /** Replaces a learner whose ID may itself have changed (fixing a typo). */
    fun replaceLearner(context: Context, classId: String, oldId: String, learner: Learner) {
        val cls = getClass(context, classId) ?: return
        val others = cls.learners.filter { it.learnerId != oldId && it.learnerId != learner.learnerId }
        saveClass(context, cls.copy(learners = (others + learner).sortedBy { it.name.lowercase() }))
        if (oldId != learner.learnerId) LearnerResultStorage.changeId(context, classId, oldId, learner.learnerId)
    }

    /** Removes a learner and their recorded scores (callers confirm first). */
    fun removeLearner(context: Context, classId: String, learnerId: String) {
        val cls = getClass(context, classId) ?: return
        saveClass(context, cls.copy(learners = cls.learners.filter { it.learnerId != learnerId }))
        LearnerResultStorage.deleteForLearner(context, classId, learnerId)
    }

    /** Merges parsed rows into a class. A learner whose ID already exists is
     *  updated (name replaced); everything else is appended. Returns
     *  (added, updated). */
    fun mergeLearners(context: Context, classId: String, incoming: List<Learner>): Pair<Int, Int> {
        val cls = getClass(context, classId) ?: return 0 to 0
        val byId = cls.learners.associateBy { it.learnerId }.toMutableMap()
        var added = 0
        var updated = 0
        for (l in incoming) {
            val existing = byId[l.learnerId]
            if (existing == null) added++ else if (existing.name != l.name || (l.gender.isNotEmpty() && existing.gender != l.gender)) updated++
            // A file without a gender column must not wipe a gender already on file.
            byId[l.learnerId] = if (l.gender.isEmpty() && existing != null) l.copy(gender = existing.gender) else l
        }
        saveClass(context, cls.copy(learners = byId.values.sortedBy { it.name.lowercase() }))
        return added to updated
    }

    /** Finds the learner (and their class) for [learnerId] among [classes]. */
    fun findLearner(classes: List<LearnerClass>, learnerId: String): Pair<LearnerClass, Learner>? {
        for (c in classes) {
            val l = c.learners.firstOrNull { it.learnerId == learnerId }
            if (l != null) return c to l
        }
        return null
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // --- Backup / Restore support -----------------------------------------

    fun exportAll(context: Context): JSONArray {
        val arr = JSONArray()
        listClasses(context).forEach { arr.put(toJson(it).put("id", it.id)) }
        return arr
    }

    /** Imports classes exported by [exportAll]. Existing ids are overwritten. */
    fun importAll(context: Context, arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            if (id.isBlank()) continue
            try { saveClass(context, fromJson(id, obj)) } catch (e: Exception) { /* skip a malformed entry */ }
        }
    }

    private fun toJson(cls: LearnerClass): JSONObject {
        val obj = JSONObject()
        obj.put("name", cls.name)
        val arr = JSONArray()
        cls.learners.forEach {
            val o = JSONObject().put("learnerId", it.learnerId).put("name", it.name)
            if (it.gender.isNotEmpty()) o.put("gender", it.gender)
            arr.put(o)
        }
        obj.put("learners", arr)
        return obj
    }

    private fun fromJson(id: String, obj: JSONObject): LearnerClass {
        val arr = obj.optJSONArray("learners") ?: JSONArray()
        val learners = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val learnerId = o.optString("learnerId", "")
            if (learnerId.isBlank()) null else Learner(learnerId, o.optString("name", ""), o.optString("gender", ""))
        }
        return LearnerClass(id, obj.optString("name", "Class"), learners)
    }
}
