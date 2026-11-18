package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.roundToInt

/**
 * v1.2.4 — "Learners": every learner enrolled in any class, in one searchable
 * list. Tap a learner to open their profile (LearnerProfileActivity): all the
 * tests saved under their ID. Read-only, so it stays available when a plan
 * lapses — a teacher's own records are never hidden.
 *
 * Same placeholder-layout pattern as ClassesActivity; rows are added in code
 * before the Back button (see ClassUi). The search field is added ONCE (not
 * per rebuild) so typing doesn't lose focus.
 */
class LearnersActivity : AppCompatActivity() {

    private val dynamicViews = ArrayList<View>()
    private var searchField: TextInputLayout? = null
    private var query = ""
    private var data: Map<String, List<LearnerData.Row>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Learners"
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "Everyone enrolled in your classes. Tap a learner to see all the tests saved under their ID."
        val back = findViewById<Button>(R.id.btnPlaceholderBack).apply {
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        val field = ClassUi.inputField(this, "Search name or ID")
        content.addView(field, content.indexOfChild(back))
        field.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim().lowercase()
                rebuild()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        searchField = field
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        data = LearnerData.byLearner(this)   // results may have changed while another screen was open
        rebuild()
    }

    private fun rebuild() {
        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        dynamicViews.forEach { content.removeView(it) }
        dynamicViews.clear()
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        val classes = ClassStorage.listClasses(this)
            .sortedWith(compareBy({ ArchiveStore.isClassArchived(this, it.id) }, { it.name.lowercase() }))   // archived last
        if (classes.none { it.learners.isNotEmpty() }) {
            add(ClassUi.note(this, "No learners yet. Enroll a class under Classes first."))
            return
        }

        fun subtitleFor(learner: Learner, classNames: String): String {
            val rows = data[learner.learnerId].orEmpty()
            val tests = if (rows.isEmpty()) "no tests yet" else "${rows.size} test${if (rows.size == 1) "" else "s"}"
            return "ID ${learner.learnerId} · $classNames · $tests"
        }
        fun trailingFor(learner: Learner): String? =
            LearnerData.average(data[learner.learnerId].orEmpty())?.let { "${it.roundToInt()}%" }
        fun open(learner: Learner) {
            startActivity(
                Intent(this, LearnerProfileActivity::class.java)
                    .putExtra(LearnerProfileActivity.EXTRA_LEARNER_ID, learner.learnerId)
            )
        }

        if (query.isEmpty()) {
            // Grouped by class, alphabetical.
            for (cls in classes) {
                if (cls.learners.isEmpty()) continue
                add(ClassUi.sectionLabel(this, "${cls.name.uppercase()} · ${cls.learners.size}" + if (ArchiveStore.isClassArchived(this, cls.id)) " · ARCHIVED" else ""))
                cls.learners.sortedBy { it.name.lowercase() }.forEach { learner ->
                    add(ClassUi.card(this, learner.name, subtitleFor(learner, cls.name), trailingFor(learner)) { open(learner) })
                }
            }
        } else {
            // Searching: one flat list; a learner enrolled in two classes shows once.
            val seen = HashSet<String>()
            val matches = ArrayList<Pair<Learner, String>>()
            for (cls in classes) for (learner in cls.learners) {
                if (!(learner.name.lowercase().contains(query) || learner.learnerId.contains(query))) continue
                if (seen.add(learner.learnerId)) {
                    val names = classes.filter { c -> c.learners.any { it.learnerId == learner.learnerId } }.joinToString(", ") { it.name }
                    matches.add(learner to names)
                }
            }
            if (matches.isEmpty()) add(ClassUi.note(this, "No learner matches that."))
            matches.sortedBy { it.first.name.lowercase() }.forEach { (learner, names) ->
                add(ClassUi.card(this, learner.name, subtitleFor(learner, names), trailingFor(learner)) { open(learner) })
            }
        }
        AccentColorPrefs.applyAccents(this)
    }
}
