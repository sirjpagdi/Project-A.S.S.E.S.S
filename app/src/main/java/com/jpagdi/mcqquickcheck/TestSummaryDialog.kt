package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Extracted from SavedScansListActivity (where it originally lived as a
 * private function) so TestDetailActivity's "all scans" Test Summary
 * button can call the exact same logic instead of a second copy that
 * could drift out of sync — same reasoning as SheetTemplate being the
 * single source of truth for layout math.
 */
object TestSummaryDialog {

    /** [folder] null = every folder combined (population SD, no folder
     *  name shown) — used by Main's own summary and by TestDetailActivity's
     *  "all scanned sheets" button. Non-null = one specific folder (sample
     *  SD, folder name shown). */
    fun show(activity: Activity, testName: String, folder: String?) {
        val body = buildSummaryText(activity, testName, folder)
        if (body == null) {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Test Summary")
                .setMessage("No scored scans yet.")
                .setPositiveButton("Close", null)
                .show()
            fixDialogButtonColors(activity, dialog)
            return
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Test Summary")
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
        fixDialogButtonColors(activity, dialog)
    }

    /** v2.9: pulled the actual stats computation out of show() so
     *  TestDetailActivity can render this same text inline on-screen
     *  instead of only behind a dialog+button (its Test Summary button
     *  was removed in favor of this). Returns null when there are no
     *  scored scans yet — same "no data" case show() turns into its own
     *  message, callers rendering inline decide how to display that
     *  themselves (see TestDetailActivity.refreshInlineSummary). */
    fun buildSummaryText(activity: Activity, testName: String, folder: String?, includeTitle: Boolean = true): String? {
        val isAll = folder == null
        val scans = ScannedSheetStorage.listScansForTest(activity, testName, folder)
            .filter { it.earnedPoints != null && it.totalPoints != null }

        val testTitle = TestStorage.loadTestOrNull(activity, testName)?.template?.testName ?: testName

        if (scans.isEmpty()) return null

        val distinctTotals = scans.map { it.totalPoints }.distinct()
        if (distinctTotals.size > 1) {
            Toast.makeText(
                activity,
                "Note: these scans don't all share the same total points (answer key may have changed between scans) — Mean/Median/Mode below may not be directly comparable.",
                Toast.LENGTH_LONG
            ).show()
        }

        val highest = scans.maxByOrNull { (it.earnedPoints!! * 1000 / it.totalPoints!!) }!!
        val lowest = scans.minByOrNull { (it.earnedPoints!! * 1000 / it.totalPoints!!) }!!

        val earnedList = scans.map { it.earnedPoints!! }
        val mean = earnedList.average()
        val sorted = earnedList.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        val frequency = earnedList.groupingBy { it }.eachCount()
        val maxFreq = frequency.values.maxOrNull() ?: 0
        val modes = frequency.filterValues { it == maxFreq }.keys.sorted()
        val modeText = if (frequency.size == earnedList.size) {
            "None (all scores unique)"
        } else {
            modes.joinToString(", ")
        }

        // Population SD (÷N) when this is the complete set of all scans
        // for the test (folder == null); sample SD (÷(N-1), Bessel's
        // correction) for one specific folder, treated as a subset.
        val n = earnedList.size
        val variance = if (isAll) {
            earnedList.sumOf { (it - mean) * (it - mean) } / n
        } else {
            if (n > 1) earnedList.sumOf { (it - mean) * (it - mean) } / (n - 1) else 0.0
        }
        val stdDev = kotlin.math.sqrt(variance)

        val passers75 = scans.count { (it.earnedPoints!! * 1000 / it.totalPoints!!) / 10.0 >= 75.0 }
        val passers50 = scans.count { (it.earnedPoints!! * 1000 / it.totalPoints!!) / 10.0 >= 50.0 }

        return buildString {
            if (includeTitle) {
                append(testTitle).append("\n")
                if (!isAll) append(folder).append("\n")
            }
            append("\nCount: ").append(scans.size)
            append("\nHighest score: ").append(highest.earnedPoints).append("/").append(highest.totalPoints)
            append("\nLowest score: ").append(lowest.earnedPoints).append("/").append(lowest.totalPoints)
            // No "/total" here — Mean/Median are bare numbers, per explicit
            // request (unlike Highest/Lowest, which are each one specific
            // scan's real score, "/total" is meaningful there; Mean/Median
            // are computed values, not any one scan's actual score).
            append("\nMean: ").append(String.format("%.1f", mean))
            append("\nMedian: ").append(String.format("%.1f", median))
            append("\nMode: ").append(modeText)
            append("\nStandard Deviation: ").append(String.format("%.2f", stdDev))
            append("\nNo. of passers at 75%: ").append(passers75).append("/").append(scans.size)
            append("\nNo. of passers at 50%: ").append(passers50).append("/").append(scans.size)
        }
    }

    /** Same fix used everywhere else in this app — AlertDialog buttons
     *  default to colorPrimary text, unreadable in Dark/Black themes. */
    private fun fixDialogButtonColors(activity: Activity, dialog: AlertDialog) {
        HapticUtils.confirm(dialog.window?.decorView)
        val color = if (ThemePrefs.getTheme(activity) == AppTheme.LIGHT) {
            android.graphics.Color.parseColor("#221111")
        } else {
            android.graphics.Color.WHITE
        }
        for (which in intArrayOf(
            android.content.DialogInterface.BUTTON_POSITIVE,
            android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL
        )) {
            dialog.getButton(which)?.setTextColor(color)
        }
        // v2.8 fold-in fix: the loop above only ever recolored the BUTTONS.
        // The dialog's title/message text still fell back to the theme's
        // own textColorPrimary/textColorSecondary, which is unset/wrong in
        // this app's themes the same way button text was (see this
        // function's own doc comment) — invisible in Light theme
        // specifically for message text, and unreliable in Dark/Black too.
        // android.R.id.message is part of the core Android alert dialog
        // layout AppCompat reuses, so it's a stable id to reach into here.
        // androidx.appcompat.R.id.alertTitle is the equivalent stable,
        // public id for the title in AppCompat's alert layout — not a
        // private/internal resource, safe to reference directly.
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
