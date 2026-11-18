package com.jpagdi.mcqquickcheck

import android.content.Context

/**
 * v3.3: remembers the last bubble-radius value chosen on the Generate Sheet
 * screen. v3.4: the slider itself was removed (bubble size is now fixed —
 * see FIXED_MM below), so getLastUsedMm/setLastUsedMm below have no
 * remaining caller. Left in place rather than deleted: MIN_MM/MAX_MM/
 * DEFAULT_MM are still real, load-bearing constants (DEFAULT_MM is the
 * backward-compat fallback TestStorage/RestoreDataActivity use for an old
 * saved test with no stored bubbleSizeMm at all — see those files —
 * unrelated to and NOT changed by this file's slider removal), so this
 * whole object stays rather than being split apart for one dead function
 * pair.
 *
 * IMPORTANT — this is only ever read at TEMPLATE-CREATION time:
 * The actual bubble size used by a given test is stored directly on that
 * test's own SheetTemplate (see SheetTemplate.bubbleSizeMm), baked in once
 * when the template is built and saved. SheetGenerator (printing),
 * BubbleGrader (scanning), and ResultAnnotator (marking) all read it from
 * the template instance they're given, never from this live preference —
 * if they read it live instead, changing this setting later would silently
 * break scanning of every sheet already printed under the OLD value, since
 * the printed bubble positions/sizes and the scanner's detection radius
 * would no longer agree. This object exists purely to make a new sheet's
 * slider start somewhere reasonable, nothing more.
 */
object BubbleSizePrefs {
    private const val PREFS = "mcq_scanner_settings"
    private const val KEY_BUBBLE_RADIUS_MM = "bubble_radius_mm"

    const val MIN_MM = 1.72f
    const val MAX_MM = 2.68f
    const val DEFAULT_MM = 2.2f

    /** v3.4: what a brand NEW template's bubbleSizeMm is set to, per
     *  explicit product decision — equal to the old slider's MAX_MM, now
     *  the only value offered rather than one end of a range. Does NOT
     *  affect any EXISTING saved test: GenerateSheetActivity only applies
     *  this to templates being created fresh, and explicitly preserves an
     *  existing template's own already-baked-in bubbleSizeMm when editing
     *  one (see that file's onCreate). */
    const val FIXED_MM = MAX_MM

    /** No remaining caller as of v3.4 (the slider this used to prefill is
     *  gone) — kept for now rather than deleted, in case bubble size
     *  becomes adjustable again later. */
    fun getLastUsedMm(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_BUBBLE_RADIUS_MM, DEFAULT_MM).coerceIn(MIN_MM, MAX_MM)
    }

    /** No remaining caller as of v3.4 — see getLastUsedMm above. */
    fun setLastUsedMm(context: Context, mm: Float) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_BUBBLE_RADIUS_MM, mm.coerceIn(MIN_MM, MAX_MM)).apply()
    }
}
