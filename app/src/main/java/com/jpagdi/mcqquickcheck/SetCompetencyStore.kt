package com.jpagdi.mcqquickcheck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.2.5 (Test sets): per-set competency item numbers.
 *
 * A test's competency NAMES live once, on the test itself (SavedTest.competencies,
 * which is also set 0's mapping). Sets 1..3 can place those same competencies on
 * different item numbers (shuffled sets). Only sets whose mapping the teacher
 * actually edited are stored here, keyed "testName|index"; every competency not
 * stored for a set falls back to the test's own item numbers, so a test whose
 * sets share one numbering needs nothing stored at all.
 *
 * Like SetKeyStore this lives in its own preferences file so no existing
 * storage format changes, and TestStorage / backup / restore carry it along.
 */
object SetCompetencyStore {
    private const val PREFS = "mcq_set_competencies"

    private fun k(testName: String, index: Int) = "$testName|$index"

    /** Stored (explicitly edited) competencies of one set, or empty. */
    fun get(context: Context, testName: String, setIndex: Int): List<Competency> {
        if (setIndex <= 0) return emptyList()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(k(testName, setIndex), null) ?: return emptyList()
        return decode(raw)
    }

    fun all(context: Context, testName: String): Map<Int, List<Competency>> {
        val out = linkedMapOf<Int, List<Competency>>()
        for (i in 1 until SheetTemplate.SET_MAX) {
            val list = get(context, testName, i)
            if (list.isNotEmpty()) out[i] = list
        }
        return out
    }

    fun replaceAll(context: Context, testName: String, byIndex: Map<Int, List<Competency>>) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (i in 1 until SheetTemplate.SET_MAX) e.remove(k(testName, i))
        byIndex.forEach { (i, list) -> if (i > 0 && list.isNotEmpty()) e.putString(k(testName, i), encode(list)) }
        e.apply()
    }

    fun delete(context: Context, testName: String) = replaceAll(context, testName, emptyMap())

    fun rename(context: Context, oldName: String, newName: String) {
        val data = all(context, oldName)
        delete(context, oldName)
        replaceAll(context, newName, data)
    }

    fun copy(context: Context, fromName: String, toName: String) =
        replaceAll(context, toName, all(context, fromName))

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** The competencies as set [setIndex] sees them: the test's own list, with
     *  any competency the teacher placed differently for this set moved onto
     *  that set's item numbers. Set 0 (or a sheet without sets) is the test's list. */
    fun resolve(context: Context, test: SavedTest, setIndex: Int): List<Competency> {
        if (setIndex <= 0 || test.competencies.isEmpty()) return test.competencies
        val stored = get(context, test.template.testName, setIndex).associateBy { it.name }
        return test.competencies.map { c ->
            val own = stored[c.name]
            if (own != null) Competency(c.name, own.itemNumbers) else c
        }
    }

    /** Set index -> resolved competencies, for every set the sheet carries. */
    fun resolveAll(context: Context, test: SavedTest): Map<Int, List<Competency>> =
        (0 until test.template.setCount).associateWith { resolve(context, test, it) }

    fun encode(list: List<Competency>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().put("name", c.name).put("items", JSONArray(c.itemNumbers.sorted())))
        }
        return arr.toString()
    }

    fun decode(raw: String): List<Competency> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            Competency(o.getString("name"), (0 until items.length()).map { items.getInt(it) }.toSet())
        }
    } catch (e: Exception) {
        emptyList()
    }
}
