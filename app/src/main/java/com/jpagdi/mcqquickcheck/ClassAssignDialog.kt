package com.jpagdi.mcqquickcheck

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/**
 * v1.2.0 — the "which classes take this exam?" multi-select, shared by the
 * exam-setup screen (GenerateSheetActivity) and the exam's page
 * (TestDetailActivity). Only enrolled classes are offered; to add one the
 * teacher uses the Classes screen first (roster before exam, as designed).
 *
 * [digits] is the sheet's ID row length (0 = this sheet has no ID row yet —
 * the caller decides what that means). After OK, any chosen class with
 * learners whose ID is NOT [digits] long gets a heads-up: those learners
 * can't be matched by a [digits]-digit ID row and will always need manual
 * linking.
 */
object ClassAssignDialog {

    fun show(activity: Activity, selectedIds: List<String>, digits: Int, onDone: (List<String>) -> Unit) {
        // Archived classes aren't offered — but one already chosen for this exam stays
        // listed, so saving the dialog can never silently drop it.
        val classes = ClassStorage.listClasses(activity)
            .filter { !ArchiveStore.isClassArchived(activity, it.id) || it.id in selectedIds }
        if (classes.isEmpty()) {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("No classes yet")
                .setMessage("Enroll a class first (Classes in the menu), then assign it to this exam.")
                .setPositiveButton("OK", null)
                .create()
            dialog.show()
            DialogColors.fix(activity, dialog)
            return
        }
        val checked = BooleanArray(classes.size) { classes[it].id in selectedIds }
        val labels = classes.map { c ->
            val n = c.learners.size
            "${c.name} ($n learner${if (n == 1) "" else "s"})"
        }.toTypedArray()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Classes taking this exam")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                val chosen = classes.filterIndexed { i, _ -> checked[i] }
                onDone(chosen.map { it.id })
                if (digits > 0) warnLengthMismatch(activity, chosen, digits)
            }
            .create()
        dialog.show()
        DialogColors.fix(activity, dialog)
    }

    private fun warnLengthMismatch(activity: Activity, chosen: List<LearnerClass>, digits: Int) {
        val lines = chosen.mapNotNull { c ->
            val bad = ClassStorage.countLengthMismatch(c, digits)
            if (bad > 0) "• ${c.name}: $bad learner${if (bad == 1) "" else "s"}" else null
        }
        if (lines.isEmpty()) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Some IDs aren't $digits digits")
            .setMessage(
                "This exam's sheet has a $digits-digit ID row. These learners have IDs of a different length, " +
                "so scans can't be matched to them automatically:\n\n${lines.joinToString("\n")}\n\n" +
                "Fix their IDs on the Classes screen, or change the ID digits in Settings before generating a new sheet."
            )
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
        DialogColors.fix(activity, dialog)
    }

    /** One-line summary for a card subtitle. */
    fun summary(activity: Activity, classIds: List<String>): String {
        val names = ClassStorage.getClasses(activity, classIds).map { it.name }
        return if (names.isEmpty()) "None assigned" else names.joinToString(", ")
    }
}
