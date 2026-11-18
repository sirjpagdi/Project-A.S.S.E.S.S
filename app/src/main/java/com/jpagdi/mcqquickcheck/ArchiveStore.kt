package com.jpagdi.mcqquickcheck

import android.content.Context

/**
 * v1.2.5 — archive flags for tests and classes. Archiving only HIDES: the test
 * (with its scans and scores) or the class (with its learners) stays exactly as
 * it was, keeps feeding the Learners database, and comes back with Restore.
 *
 * Kept in its own small preferences file instead of inside the test / class
 * JSON so no stored record changes shape. A test is identified by its name, a
 * class by its id; TestStorage / ClassStorage tell this store when a test is
 * renamed or deleted so a flag never points at nothing.
 */
object ArchiveStore {
    private const val PREFS = "mcq_scanner_archive"
    private const val KEY_TESTS = "tests"
    private const val KEY_CLASSES = "classes"

    private fun read(context: Context, key: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(key, emptySet()) ?: emptySet()

    private fun write(context: Context, key: String, value: Set<String>) {
        // a fresh HashSet: a StringSet handed back by SharedPreferences must never be mutated
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(key, HashSet(value)).apply()
    }

    // ---- tests (by name) ----
    fun isTestArchived(context: Context, name: String) = name in read(context, KEY_TESTS)
    fun archivedTests(context: Context): Set<String> = read(context, KEY_TESTS)
    fun setTestArchived(context: Context, name: String, archived: Boolean) {
        val now = read(context, KEY_TESTS)
        write(context, KEY_TESTS, if (archived) now + name else now - name)
    }
    fun renameTest(context: Context, old: String, new: String) {
        if (isTestArchived(context, old)) write(context, KEY_TESTS, read(context, KEY_TESTS) - old + new)
    }
    fun forgetTest(context: Context, name: String) = setTestArchived(context, name, false)

    // ---- classes (by id) ----
    fun isClassArchived(context: Context, id: String) = id in read(context, KEY_CLASSES)
    fun setClassArchived(context: Context, id: String, archived: Boolean) {
        val now = read(context, KEY_CLASSES)
        write(context, KEY_CLASSES, if (archived) now + id else now - id)
    }
    fun forgetClass(context: Context, id: String) = setClassArchived(context, id, false)

    /** Used when everything is wiped for a restore. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
