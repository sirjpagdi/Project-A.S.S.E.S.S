package com.jpagdi.mcqquickcheck

import android.content.Context

/**
 * Default values used to pre-fill a new answer sheet template, plus the
 * autosave-scanned-sheet toggle. Separate object from ThemePrefs/TestStorage
 * (different concern, different SharedPreferences file) but follows the
 * same simple get/set pattern already used throughout the app.
 */
object SettingsPrefs {
    private const val PREFS = "mcq_scanner_settings"

    private const val KEY_PAPER_LAYOUT = "default_paper_layout"
    private const val KEY_CHOICE_COUNT = "default_choice_count"
    private const val KEY_QUESTION_COUNT = "default_question_count"
    private const val KEY_COLUMNS = "default_columns"
    private const val KEY_QUESTIONS_PER_COLUMN = "default_questions_per_column"
    private const val KEY_AUTOSAVE_SCAN = "autosave_scanned_sheet"
    // v3.2: audible shutter sound on capture. Defaults to true (on) —
    // this doesn't change any existing scan/grading behavior, just adds
    // expected camera-like audio feedback, so it's on by default with an
    // off switch for anyone scanning somewhere a shutter sound would be
    // disruptive.
    private const val KEY_CAMERA_SOUND = "camera_sound_enabled"

    data class Defaults(
        val paperLayout: PaperLayout,
        val choiceCount: Int,
        val questionCount: Int,
        val columns: Int,
        val questionsPerColumn: Int,
        val autosaveScannedSheet: Boolean,
        val cameraSoundEnabled: Boolean
    )

    /** Matches the values GenerateSheetActivity already used as hardcoded
     *  defaults before this existed, so turning this on doesn't change
     *  anyone's current default sheet shape.
     *
     *  v3.4: QUARTER is now the only PaperLayout value. A teacher whose
     *  stored KEY_PAPER_LAYOUT is still "HALF_CROSS"/"HALF_LENGTH" from
     *  before this change gets QUARTER here via fromStorageName's own
     *  fallback — silently, unlike TestStorage.loadTest's explicit
     *  reject-and-warn for a saved TEST using a legacy layout. That
     *  distinction is intentional: this is just a default-preference
     *  value for what a brand NEW sheet starts as, not an existing test's
     *  committed geometry with real scanned data riding on it, so there's
     *  nothing here that a silent fallback could corrupt. */
    fun getDefaults(context: Context): Defaults {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val layoutName = prefs.getString(KEY_PAPER_LAYOUT, PaperLayout.QUARTER.name)
        return Defaults(
            paperLayout = PaperLayout.fromStorageName(layoutName ?: PaperLayout.QUARTER.name),
            choiceCount = prefs.getInt(KEY_CHOICE_COUNT, 4),
            questionCount = prefs.getInt(KEY_QUESTION_COUNT, 25),
            columns = prefs.getInt(KEY_COLUMNS, 3),
            questionsPerColumn = prefs.getInt(KEY_QUESTIONS_PER_COLUMN, 10),
            // v3.7.8: default flipped to true (was false) per explicit
            // request. Existing installs that already stored an explicit
            // false (having left it at its old default, or having turned
            // it off deliberately) keep reading false here — this only
            // changes what a value NEVER written to prefs resolves to,
            // i.e. fresh installs and anyone who's never touched this
            // toggle before.
            autosaveScannedSheet = prefs.getBoolean(KEY_AUTOSAVE_SCAN, true),
            cameraSoundEnabled = prefs.getBoolean(KEY_CAMERA_SOUND, true)
        )
    }

    fun saveDefaults(context: Context, defaults: Defaults) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PAPER_LAYOUT, defaults.paperLayout.name)
            .putInt(KEY_CHOICE_COUNT, defaults.choiceCount)
            .putInt(KEY_QUESTION_COUNT, defaults.questionCount)
            .putInt(KEY_COLUMNS, defaults.columns)
            .putInt(KEY_QUESTIONS_PER_COLUMN, defaults.questionsPerColumn)
            .putBoolean(KEY_AUTOSAVE_SCAN, defaults.autosaveScannedSheet)
            .putBoolean(KEY_CAMERA_SOUND, defaults.cameraSoundEnabled)
            .apply()
    }

    /** Convenience read used by ScanActivity's capture sound. */
    fun isCameraSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CAMERA_SOUND, true)
    }

    /** Convenience read used by ScanActivity, which only cares about this
     *  one flag and shouldn't need to know about the rest of Defaults.
     *  v3.7.8: default flipped to true, matching getDefaults() above —
     *  this getter must stay in sync with that one since ScanActivity
     *  reads through here directly rather than through getDefaults(). */
    fun isAutosaveScannedSheetEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTOSAVE_SCAN, true)
    }

    private const val KEY_WELCOME_ANIMATION = "welcome_animation_enabled"

    private const val KEY_SCAN_SORT_ORDER = "scan_sort_order"

    /** Remembered globally (not per-folder/per-test) — last sort order
     *  chosen on the Scanned Sheets screen applies the next time that
     *  screen opens, for any test/folder. Defaults to DATE_DESC, matching
     *  listScansForTest's own existing default sort. */
    fun getScanSortOrder(context: Context): ScanSortOrder {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SCAN_SORT_ORDER, ScanSortOrder.DATE_DESC.name)
        return ScanSortOrder.values().firstOrNull { it.name == name } ?: ScanSortOrder.DATE_DESC
    }

    fun setScanSortOrder(context: Context, order: ScanSortOrder) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SCAN_SORT_ORDER, order.name).apply()
    }

    /** Hidden dev-only toggle (see ChangelogActivity's tap target below the
     *  v0.0 entry) — deliberately kept separate from Defaults/getDefaults,
     *  since it's not a normal visible setting a teacher would ever see or
     *  expect to find. Defaults to true (on) — matches how every other
     *  feature in this app has shipped enabled by default unless it was
     *  specifically an opt-in toggle. */
    fun isWelcomeAnimationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WELCOME_ANIMATION, true)
    }

    fun setWelcomeAnimationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WELCOME_ANIMATION, enabled).apply()
    }

    private const val KEY_PARENT_SIGNATURE_LINE = "parent_signature_line_enabled"

    /** v1.1.0: opt-in "Parent/Guardian Signature" blank line printed just
     *  below the scannable area on generated answer sheets — see
     *  SheetGenerator.drawParentSignatureLine's doc comment for the actual
     *  drawing. Off by default (unlike welcomeAnimationEnabled above) —
     *  this changes what prints on a real physical answer sheet, so it
     *  shouldn't newly appear on already-in-use templates without the
     *  teacher turning it on deliberately. */
    fun isParentSignatureLineEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PARENT_SIGNATURE_LINE, false)
    }

    fun setParentSignatureLineEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PARENT_SIGNATURE_LINE, enabled).apply()
    }

    private const val KEY_CLASSES_ENABLED = "classes_enabled"

    /** v1.2.0: opt-in switch for the Classes feature (enrolled classes,
     *  per-learner scores, ID row on new sheets). Off by default. Only
     *  meaningful on an entitled plan — always go through
     *  ClassesAccess.isActive()/hasAccess() rather than reading this alone. */
    fun isClassesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CLASSES_ENABLED, false)
    }

    fun setClassesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CLASSES_ENABLED, enabled).apply()
    }

    private const val KEY_ID_DIGITS = "id_digits"

    /** v1.2.0: how many ID digit boxes NEWLY generated sheets get (4..12,
     *  default 6). Read once at sheet-generation time and baked into the
     *  template (SheetTemplate.idDigits) — changing it never alters sheets
     *  already made. */
    fun getIdDigits(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SheetTemplate.clampIdDigits(prefs.getInt(KEY_ID_DIGITS, SheetTemplate.ID_DEFAULT_DIGITS))
    }

    fun setIdDigits(context: Context, digits: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ID_DIGITS, SheetTemplate.clampIdDigits(digits)).apply()
    }

    private const val KEY_PAPER_SIZE = "download_paper_size"

    /** v3.3: which physical paper size to render the PDF onto when a sheet
     *  is downloaded/shared — purely a printing preference (see PaperSize.kt),
     *  kept separate from Defaults/getDefaults since it's read at
     *  download-time by GenerateSheetActivity, not baked into a saved test's
     *  own SheetTemplate. */
    fun getPaperSizePreference(context: Context): PaperSize {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PaperSize.fromNameOrDefault(prefs.getString(KEY_PAPER_SIZE, null))
    }

    fun setPaperSizePreference(context: Context, size: PaperSize) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PAPER_SIZE, size.name).apply()
    }

    private const val KEY_STORAGE_RESOLUTION_PX = "storage_resolution_px"

    /** v3.6.1: long-edge cap (px) for scanned-sheet images persisted via
     *  ScannedSheetStorage.saveScan()/downscaleForExport(), per explicit
     *  request. Only these values are ever offered from Settings —
     *  see activity_settings.xml's radio group — but this getter falls back
     *  to the default for any other stored value too (defensive, not
     *  reachable through the UI as shipped).
     *
     *  v3.7.8: default changed to 1800 (was 2000) per explicit request —
     *  this only changes what a value NEVER written to prefs resolves to;
     *  anyone who already has an explicit stored value (including 2000,
     *  if they picked it deliberately or it was written under the old
     *  default before this change) keeps reading their own stored value.
     *
     *  v1.1.0: 1200 added as a 4th option, per explicit request. */
    val STORAGE_RESOLUTION_OPTIONS_PX = listOf(2000, 1800, 1600, 1200)

    fun getStorageResolutionPx(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_STORAGE_RESOLUTION_PX, 1800)
        return if (stored in STORAGE_RESOLUTION_OPTIONS_PX) stored else 1800
    }

    fun setStorageResolutionPx(context: Context, px: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_STORAGE_RESOLUTION_PX, px).apply()
    }
}
