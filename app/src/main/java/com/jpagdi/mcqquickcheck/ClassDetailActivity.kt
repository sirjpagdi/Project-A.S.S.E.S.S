package com.jpagdi.mcqquickcheck

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.2.0 — one class's roster: add/edit/remove learners, import a CSV or XLSX
 * roster, rename or delete the class. Editing needs ClassesAccess.isActive();
 * otherwise this is a read-only list.
 */
class ClassDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CLASS_ID = "class_id"
    }

    private lateinit var classId: String
    private val dynamicViews = ArrayList<View>()

    private val pickRoster = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }
        if (bytes == null) {
            Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        confirmImport(bytes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        classId = intent.getStringExtra(EXTRA_CLASS_ID) ?: run { finish(); return }
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        rebuild()
    }

    private fun rebuild() {
        val cls = ClassStorage.getClass(this, classId)
        if (cls == null) { finish(); return }
        val active = ClassesAccess.isActive(this)

        findViewById<TextView>(R.id.tvPlaceholderTitle).text = cls.name
        val n = cls.learners.size
        findViewById<TextView>(R.id.tvPlaceholderBody).text = "$n learner${if (n == 1) "" else "s"}"

        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        dynamicViews.forEach { content.removeView(it) }
        dynamicViews.clear()
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        if (!active) {
            add(ClassUi.note(
                this,
                if (!ClassesAccess.hasAccess(this)) "🔒 View only — ${ClassesAccess.LOCKED_HINT.lowercase()}."
                else "View only — Classes is switched off in Settings.",
                alpha = 0.85f
            ))
        }
        if (active) {
            add(ClassUi.actionButton(this, "IMPORT ROSTER (CSV / XLSX)") { pickRoster.launch(arrayOf("*/*")) })
            add(ClassUi.actionButton(this, "ADD LEARNER") { promptLearner(null) })
            add(ClassUi.note(this, "Roster columns: ID, Name (or Last Name and First Name) and Gender (M/F). A DepEd LRN works as the ID. Tip: format the ID column as Text before typing IDs, so Excel doesn't drop a leading zero or switch to scientific notation."))
        }

        add(ClassUi.sectionLabel(this, "LEARNERS"))
        if (cls.learners.isEmpty()) add(ClassUi.note(this, "No learners yet."))
        cls.learners.forEach { learner ->
            add(ClassUi.card(this, learner.name, "ID ${learner.learnerId}" + ClassUi.genderLabel(learner.gender).let { if (it.isEmpty()) "" else " · $it" }, onClick = if (active) ({ learnerActions(learner) }) else null))
        }

        if (active) {
            add(ClassUi.actionButton(this, "RENAME CLASS") { promptRename(cls) })
            add(ClassUi.actionButton(this, "DELETE CLASS") { confirmDeleteClass(cls) })
        }
        AccentColorPrefs.applyAccents(this)
    }

    // --- roster import ------------------------------------------------------------

    private fun confirmImport(bytes: ByteArray) {
        val result = try {
            RosterImporter.parse(bytes)
        } catch (e: RosterImporter.ImportException) {
            showMessage("Couldn't read the roster", e.message ?: "Unknown problem with the file.")
            return
        } catch (e: Exception) {
            showMessage("Couldn't read the roster", "That file couldn't be opened as a CSV or XLSX roster.")
            return
        }
        if (result.learners.isEmpty()) {
            showMessage("No learners found", buildSkippedText(result.skipped).ifBlank { "The file had no usable rows." })
            return
        }
        val skippedNote = (if (result.skipped.isEmpty()) "" else "\n\nSkipped ${result.skipped.size} row${if (result.skipped.size == 1) "" else "s"}:\n" + buildSkippedText(result.skipped)) +
            (if (result.warnings.isEmpty()) "" else "\n\nNote:\n" + buildSkippedText(result.warnings))
        ClassUi.confirm(
            this, "Import ${result.learners.size} learner${if (result.learners.size == 1) "" else "s"}?",
            "Learners whose ID is already in this class are updated; the rest are added.$skippedNote",
            "Import"
        ) {
            val (added, updated) = ClassStorage.mergeLearners(this, classId, result.learners)
            Toast.makeText(this, "Added $added, updated $updated", Toast.LENGTH_LONG).show()
            rebuild()
        }
    }

    private fun buildSkippedText(skipped: List<String>): String {
        val shown = skipped.take(8).joinToString("\n") { "• $it" }
        return if (skipped.size > 8) "$shown\n• …and ${skipped.size - 8} more" else shown
    }

    private fun showMessage(title: String, message: String) {
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).create()
        dialog.show()
        DialogColors.fix(this, dialog)
    }

    // --- learners ----------------------------------------------------------------

    private fun learnerActions(learner: Learner) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(learner.name)
            .setItems(arrayOf("Edit", "Remove from class")) { _, which ->
                if (which == 0) promptLearner(learner) else confirmRemove(learner)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        DialogColors.fix(this, dialog)
    }

    /** [existing] null = add a new learner, otherwise edit that learner. */
    private fun promptLearner(existing: Learner?) {
        val nameField = ClassUi.inputField(this, "Name (Last, First M.)", existing?.name.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val idField = ClassUi.inputField(this, "ID (${SheetTemplate.ID_MIN_DIGITS}-${SheetTemplate.ID_MAX_DIGITS} digits)", existing?.learnerId.orEmpty(),
            InputType.TYPE_CLASS_NUMBER, maxLength = SheetTemplate.ID_MAX_DIGITS)
        val genderPicker = ClassUi.genderPicker(this, existing?.gender.orEmpty())
        ClassUi.formDialog(
            this, if (existing == null) "Add learner" else "Edit learner", "Save",
            fields = listOf(nameField, idField), extraViews = listOf(genderPicker)
        ) {
            val name = ClassUi.text(nameField)
            val learnerId = ClassStorage.normalizeId(ClassUi.text(idField))
            val gender = ClassUi.genderOf(genderPicker)
            when {
                name.isBlank() -> { Toast.makeText(this, "Enter the learner's name", Toast.LENGTH_SHORT).show(); false }
                learnerId == null -> {
                    Toast.makeText(this, "ID must be ${SheetTemplate.ID_MIN_DIGITS}-${SheetTemplate.ID_MAX_DIGITS} digits", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> {
                    val cls = ClassStorage.getClass(this, classId)
                    val clash = cls?.learners?.any { it.learnerId == learnerId && it.learnerId != existing?.learnerId } == true
                    if (clash) {
                        Toast.makeText(this, "Another learner in this class already has that ID", Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        if (existing == null) ClassStorage.upsertLearner(this, classId, Learner(learnerId, name, gender))
                        else ClassStorage.replaceLearner(this, classId, existing.learnerId, Learner(learnerId, name, gender))
                        rebuild()
                        true
                    }
                }
            }
        }
    }

    private fun confirmRemove(learner: Learner) {
        ClassUi.confirm(
            this, "Remove ${learner.name}?",
            "This removes ${learner.name} (ID ${learner.learnerId}) from the class and deletes the scores recorded for them on your exams. Scanned sheet images are not deleted.",
            "Remove"
        ) {
            ClassStorage.removeLearner(this, classId, learner.learnerId)
            rebuild()
        }
    }

    // --- class ---------------------------------------------------------------------

    private fun promptRename(cls: LearnerClass) {
        val field = ClassUi.inputField(this, "Class name", cls.name)
        ClassUi.formDialog(this, "Rename class", "Rename", listOf(field)) {
            val ok = ClassStorage.renameClass(this, classId, ClassUi.text(field))
            if (!ok) Toast.makeText(this, "Enter a name that isn't already used by another class", Toast.LENGTH_SHORT).show()
            else rebuild()
            ok
        }
    }

    private fun confirmDeleteClass(cls: LearnerClass) {
        ClassUi.confirm(
            this, "Delete \"${cls.name}\"?",
            "This permanently deletes the class, its ${cls.learners.size} learner${if (cls.learners.size == 1) "" else "s"}, and every score recorded for them on your exams, and unassigns the class from those exams. Scanned sheet images are not deleted.",
            "Delete"
        ) {
            ClassStorage.deleteClass(this, classId)
            finish()
        }
    }
}
