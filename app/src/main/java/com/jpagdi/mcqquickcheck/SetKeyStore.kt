package com.jpagdi.mcqquickcheck

import android.content.Context

/**
 * v1.2.5 (Test sets): answer keys for sets 2..4 of a test. Set index 0 always
 * uses the test's own key (SavedTest.answerKey), so it is never stored here.
 * Kept in its own preferences file, keyed "testName|index", so no existing
 * storage format or screen changes. Anything that renames, deletes, copies,
 * backs up or restores a test must carry these along (see TestStorage).
 */
object SetKeyStore {
    private const val PREFS = "mcq_set_keys"

    private fun k(testName: String, index: Int) = "$testName|$index"

    fun get(context: Context, testName: String, setIndex: Int): List<Int>? {
        if (setIndex <= 0) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(k(testName, setIndex), null) ?: return null
        return decodeKey(raw)
    }

    /** All stored keys for a test: set index (1..3) -> key. */
    fun all(context: Context, testName: String): Map<Int, List<Int>> {
        val out = linkedMapOf<Int, List<Int>>()
        for (i in 1 until SheetTemplate.SET_MAX) get(context, testName, i)?.let { out[i] = it }
        return out
    }

    /** Replaces every stored key of [testName] with [keys] (index -> key). */
    fun replaceAll(context: Context, testName: String, keys: Map<Int, List<Int>>) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (i in 1 until SheetTemplate.SET_MAX) e.remove(k(testName, i))
        keys.forEach { (i, key) -> if (i > 0) e.putString(k(testName, i), key.joinToString(",")) }
        e.apply()
    }

    fun delete(context: Context, testName: String) = replaceAll(context, testName, emptyMap())

    fun rename(context: Context, oldName: String, newName: String) {
        val keys = all(context, oldName)
        delete(context, oldName)
        replaceAll(context, newName, keys)
    }

    fun copy(context: Context, fromName: String, toName: String) =
        replaceAll(context, toName, all(context, fromName))

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // Intent transport: "1:0,1,2;2:3,3,1" (the generator hands keys back to
    // whichever screen saves the test).
    fun encode(keys: Map<Int, List<Int>>): String =
        keys.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }

    fun decode(s: String?): Map<Int, List<Int>> {
        if (s.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<Int, List<Int>>()
        s.split(";").forEach { part ->
            val bits = part.split(":")
            val idx = bits.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
            decodeKey(bits.getOrNull(1) ?: "")?.let { out[idx] = it }
        }
        return out
    }

    private fun decodeKey(raw: String): List<Int>? {
        if (raw.isBlank()) return null
        val parts = raw.split(",").map { it.trim().toIntOrNull() }
        return if (parts.any { it == null }) null else parts.filterNotNull()
    }
}
