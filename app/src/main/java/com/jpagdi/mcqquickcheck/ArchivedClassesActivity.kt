package com.jpagdi.mcqquickcheck

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** v1.2.5 — archived classes: restore them to the Classes list, or delete them for good. */
class ArchivedClassesActivity : AppCompatActivity() {

    private val dynamicViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Archived classes"
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "Hidden from your Classes list and from the exam class picker, but kept in full — learners and their scores stay in the Learners database. Tap a class to restore or delete it."
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
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        val archived = ClassStorage.listClasses(this).filter { ArchiveStore.isClassArchived(this, it.id) }
        if (archived.isEmpty()) {
            add(ClassUi.note(this, "Nothing archived. Press and hold a class on the Classes screen to archive it."))
            return
        }
        archived.sortedBy { it.name.lowercase() }.forEach { cls ->
            val n = cls.learners.size
            add(ClassUi.card(this, cls.name, "$n learner${if (n == 1) "" else "s"}") { choose(cls) })
        }
        AccentColorPrefs.applyAccents(this)
    }

    private fun choose(cls: LearnerClass) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(cls.name)
            .setItems(arrayOf("Restore to my classes", "Delete permanently…")) { _, which ->
                if (which == 0) {
                    ArchiveStore.setClassArchived(this, cls.id, false)
                    Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show()
                    rebuild()
                } else {
                    confirmDelete(cls)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        DialogColors.fix(this, dialog)
    }

    private fun confirmDelete(cls: LearnerClass) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete \"${cls.name}\" permanently?")
            .setMessage("This can't be undone. The class, its learners and every score saved against them are deleted.")
            .setPositiveButton("Delete") { _, _ ->
                ClassStorage.deleteClass(this, cls.id)
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
        DialogColors.fix(this, dialog)
    }
}
