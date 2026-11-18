package com.jpagdi.mcqquickcheck

/**
 * How many copies of a sheet are tiled onto one physical page, and in what
 * orientation.
 *
 * v3.4: HALF_CROSS (2-up landscape) and HALF_LENGTH (2-up portrait) removed
 * entirely, per explicit request — QUARTER is now the only value. This is a
 * genuine removal, not a UI-only hide: fromStorageName() below can no
 * longer resolve either old name to a real PaperLayout value, so
 * TestStorage explicitly checks for and REJECTS (rather than silently
 * migrates) any saved test whose stored paperLayout string was one of the
 * removed names — see TestStorage.kt's loadTest()/listTestNames() and
 * LEGACY_LAYOUT_NAMES below. A teacher with an existing HALF_CROSS or
 * HALF_LENGTH test needs to recreate it; auto-migrating to QUARTER was
 * considered and explicitly rejected, since any sheets already scanned
 * for that test were captured against the OLD layout's geometry, and
 * silently reinterpreting them against QUARTER's different tile shape
 * could corrupt grading without any warning.
 *
 * - QUARTER: four copies in a 2x2 grid.
 */
enum class PaperLayout(val tileCount: Int, val label: String) {
    QUARTER(4, "¼ page");

    companion object {
        /** Names PaperLayout used to have, kept ONLY so TestStorage can
         *  recognize and reject an old saved test that used one of them,
         *  with a clear message, instead of fromStorageName() silently
         *  falling back to QUARTER for a layout that test was never
         *  actually created with. */
        val LEGACY_LAYOUT_NAMES = setOf("HALF_CROSS", "HALF_LENGTH")

        fun fromStorageName(name: String?): PaperLayout =
            entries.firstOrNull { it.name == name } ?: QUARTER
    }
}
