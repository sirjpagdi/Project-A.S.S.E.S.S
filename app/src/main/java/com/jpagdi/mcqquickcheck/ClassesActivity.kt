package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.2.0 — "Classes" screen (drawer entry): the list of enrolled classes.
 * Tapping a class opens ClassDetailActivity (its roster). Adding a class
 * needs the feature to be active (Semester/Full-Year plan AND the Settings
 * switch on); otherwise the screen is a read-only view of whatever is already
 * stored — a lapsed plan never hides a teacher's own records.
 *
 * Reuses activity_placeholder.xml; rows are added in code before the Back
 * button (see ClassUi).
 */
class ClassesActivity : AppCompatActivity() {

    private val dynamicViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Classes"
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
        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        dynamicViews.forEach { content.removeView(it) }
        dynamicViews.clear()
        val body = findViewById<TextView>(R.id.tvPlaceholderBody)
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        val active = ClassesAccess.isActive(this)
        body.text = "Enroll your classes here, assign them to an exam from the exam's page, and each scan's score goes to the right learner automatically."

        if (!active) {
            add(ClassUi.note(
                this,
                if (!ClassesAccess.hasAccess(this))
                    "🔒 ${ClassesAccess.LOCKED_HINT}. Your existing classes stay viewable; adding or changing them needs one of those plans."
                else
                    "Classes is switched off in Settings. Turn it on to add or change classes.",
                alpha = 0.85f
            ))
        }

        val allClasses = ClassStorage.listClasses(this)
        val classes = allClasses.filter { !ArchiveStore.isClassArchived(this, it.id) }   // v1.2.5
        if (allClasses.any { it.learners.isNotEmpty() }) {
            // v1.2.4: every learner in one searchable list, each with their tests
            add(ClassUi.card(this, "Learners", "Search any learner and see every test saved under their ID") {
                startActivity(Intent(this, LearnersActivity::class.java))
            })
        }
        if (classes.isEmpty()) {
            add(ClassUi.note(this, "No classes yet."))
        }
        classes.forEach { cls ->
            val n = cls.learners.size
            val card = ClassUi.card(this, cls.name, "$n learner${if (n == 1) "" else "s"}") {
                startActivity(Intent(this, ClassDetailActivity::class.java).putExtra(ClassDetailActivity.EXTRA_CLASS_ID, cls.id))
            }
            card.setOnLongClickListener { confirmArchive(cls); true }   // v1.2.5: press and hold to archive
            add(card)
        }
        val archivedCount = allClasses.size - classes.size
        if (archivedCount > 0) {
            add(ClassUi.card(this, "Archived classes", "$archivedCount class${if (archivedCount == 1) "" else "es"} · restore or delete") {
                startActivity(Intent(this, ArchivedClassesActivity::class.java))
            })
        }
        if (active) {
            add(ClassUi.actionButton(this, "ADD CLASS") { promptAddClass() })
        }
        AccentColorPrefs.applyAccents(this)
    }

    private fun confirmArchive(cls: LearnerClass) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Archive \"${cls.name}\"?")
            .setMessage(
                "It leaves this list and the exam class picker. Nothing is deleted: its learners and every saved score stay in the " +
                    "Learners database, exams already using it keep working, and you can restore it any time from Archived classes."
            )
            .setPositiveButton("Archive") { _, _ ->
                ArchiveStore.setClassArchived(this, cls.id, true)
                Toast.makeText(this, "Archived", Toast.LENGTH_SHORT).show()
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
        DialogColors.fix(this, dialog)
    }

    private fun promptAddClass() {
        val field = ClassUi.inputField(this, "Class name (e.g. STEM 12-Pasteur)")
        ClassUi.formDialog(this, "Add class", "Add", listOf(field)) {
            val name = ClassUi.text(field)
            val created = if (name.isBlank()) null else ClassStorage.createClass(this, name)
            if (created == null) {
                Toast.makeText(this, if (name.isBlank()) "Enter a class name" else "You already have a class with that name", Toast.LENGTH_SHORT).show()
                false
            } else {
                startActivity(Intent(this, ClassDetailActivity::class.java).putExtra(ClassDetailActivity.EXTRA_CLASS_ID, created.id))
                true
            }
        }
    }
}
