package com.jpagdi.mcqquickcheck

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v1.2.5 — archived tests: restore them to the main list, or delete them for good. */
class ArchivedTestsActivity : AppCompatActivity() {

    private val dynamicViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Archived tests"
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "Hidden from your test list but kept in full — scans, scores and each learner's record stay as they were. Tap a test to restore or delete it."
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

        val summaries = TestStorage.listTestSummaries(this).filter { ArchiveStore.isTestArchived(this, it.name) }
        if (summaries.isEmpty()) {
            add(ClassUi.note(this, "Nothing archived. Press and hold a test on the main screen to archive it."))
            return
        }
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        summaries.sortedBy { it.name.lowercase() }.forEach { s ->
            val scans = ScannedSheetStorage.listScansForTest(this, s.name).size
            val date = if (s.savedAtMillis > 0) dateFormat.format(Date(s.savedAtMillis)) else ""
            add(ClassUi.card(this, s.name, listOf("$scans scan${if (scans == 1) "" else "s"}", date).filter { it.isNotEmpty() }.joinToString(" · ")) {
                choose(s.name)
            })
        }
        AccentColorPrefs.applyAccents(this)
    }

    private fun choose(name: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Restore to my tests", "Delete permanently…")) { _, which ->
                if (which == 0) {
                    ArchiveStore.setTestArchived(this, name, false)
                    Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show()
                    rebuild()
                } else {
                    confirmDelete(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        DialogColors.fix(this, dialog)
    }

    private fun confirmDelete(name: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete \"$name\" permanently?")
            .setMessage("This can't be undone. Every saved scan and every learner score for this test is deleted too.")
            .setPositiveButton("Delete") { _, _ ->
                TestStorage.deleteTest(this, name)
                ScannedSheetStorage.deleteAllScansForTest(this, name)
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
        DialogColors.fix(this, dialog)
    }
}
