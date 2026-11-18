package com.jpagdi.mcqquickcheck

import android.content.Context

/** Project A.S.S.E.S.S.: classes are available without licensing. */
object ClassesAccess {
    const val LOCKED_HINT = "Enable Classes in Settings"
    const val SCORE_EXPORT_LOCKED_HINT = ""
    fun hasAccess(context: Context): Boolean = true
    fun hasScoreExportAccess(context: Context): Boolean = true
    fun isEnabled(context: Context): Boolean = SettingsPrefs.isClassesEnabled(context)
    fun isActive(context: Context): Boolean = isEnabled(context)
}
