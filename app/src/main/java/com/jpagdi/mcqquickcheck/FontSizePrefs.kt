package com.jpagdi.mcqquickcheck

import android.content.Context

/**
 * v3.9.9: system-wide font-size preference. Affects the app's own UI only
 * (text sizes, and rounded-card button height/padding) — explicitly NOT the
 * printed/PDF answer sheets or their preview, which stay governed entirely
 * by SheetTemplate's fixed fractions regardless of this setting. Mixing the
 * two would violate SheetTemplate's single-source-of-truth role for
 * anything the scanner reads.
 *
 * [scale] multiplies every UI text size and every card-button's height/
 * vertical padding. Chosen once here rather than per-screen so every
 * screen's "large" actually means the same physical size as every other
 * screen's "large".
 */
enum class FontScale(val scale: Float, val label: String) {
    VERY_SMALL(0.85f, "Very Small"),
    SMALL(0.92f, "Small"),
    MEDIUM(1.0f, "Medium"),
    LARGE(1.15f, "Large")
}

object FontSizePrefs {
    private const val PREFS = "mcq_scanner_prefs"
    private const val KEY_FONT_SCALE = "font_scale"

    fun getFontScale(context: Context): FontScale {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_FONT_SCALE, FontScale.MEDIUM.name)
        return FontScale.values().firstOrNull { it.name == name } ?: FontScale.MEDIUM
    }

    fun setFontScale(context: Context, scale: FontScale) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT_SCALE, scale.name).apply()
    }
}
