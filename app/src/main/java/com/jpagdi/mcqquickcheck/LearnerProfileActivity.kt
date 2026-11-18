package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v1.2.4 — one learner's profile: every test saved under their ID (newest
 * first) with score and percent, a small trend line, and the saved scan of
 * each sheet one tap away. Read-only; built from LearnerData.
 */
class LearnerProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEARNER_ID = "learner_id"
    }

    private val dynamicViews = ArrayList<View>()
    private lateinit var learnerId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        learnerId = intent.getStringExtra(EXTRA_LEARNER_ID) ?: run { finish(); return }
        setContentView(R.layout.activity_placeholder)
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
        if (!::learnerId.isInitialized) return
        FontScaleController.apply(this)
        rebuild()
    }

    private fun rebuild() {
        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        dynamicViews.forEach { content.removeView(it) }
        dynamicViews.clear()
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        // Who this is (may be enrolled in more than one class).
        val classes = ClassStorage.listClasses(this)
        val enrolled = classes.mapNotNull { c -> c.learners.firstOrNull { it.learnerId == learnerId }?.let { c to it } }
        val learner = enrolled.firstOrNull()?.second
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = learner?.name ?: "Learner $learnerId"
        val classNames = enrolled.joinToString(", ") { it.first.name }
        val gender = learner?.gender.orEmpty()
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "ID $learnerId" + (if (classNames.isNotEmpty()) " · $classNames" else " · no longer enrolled") +
                (if (gender.isNotEmpty()) " · $gender" else "")

        val rows = LearnerData.byLearner(this)[learnerId].orEmpty()
        if (rows.isEmpty()) {
            add(ClassUi.note(this, "No tests saved under this ID yet. Scan this learner's sheet and it will appear here."))
            return
        }

        // Summary
        val percents = rows.map { LearnerData.percent(it.result) }
        add(ClassUi.sectionLabel(this, "SUMMARY"))
        add(ClassUi.card(
            this,
            "Average ${percents.average().roundToInt()}%",
            "${rows.size} test${if (rows.size == 1) "" else "s"} · best ${percents.max().roundToInt()}% · lowest ${percents.min().roundToInt()}%"
        ))
        if (rows.size >= 2) {
            val textColor = if (ThemePrefs.getTheme(this) == AppTheme.LIGHT) Color.parseColor("#221111") else Color.WHITE
            add(SparklineView(this, percents.reversed(), AccentColorPrefs.getGroupB(this), textColor))
            add(ClassUi.note(this, "Percent per test, oldest to newest.", alpha = 0.6f))
        }

        // Every test
        add(ClassUi.sectionLabel(this, "TESTS"))
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        rows.forEach { row ->
            val r = row.result
            val className = ClassStorage.getClass(this, r.classId)?.name ?: ""
            val subtitle = dateFormat.format(Date(r.savedAtMillis)) + if (className.isNotEmpty()) " · $className" else ""
            add(ClassUi.card(
                this, row.testName, subtitle,
                "${r.earnedPoints} / ${r.totalPoints}\n${LearnerData.percentText(r)}"
            ) { openScan(r) })
        }
        AccentColorPrefs.applyAccents(this)
    }

    private fun openScan(r: LearnerResult) {
        val id = r.scanId
        val bmp = if (id != null) ScannedSheetStorage.loadScan(this, id) else null
        if (bmp == null) {
            Toast.makeText(this, "This sheet's image wasn't saved.", Toast.LENGTH_SHORT).show()
            return
        }
        FullscreenImageViewer.show(this, bmp)
    }

    /** Percent-per-test trend: a line with a dot per test and guides at 50% and 100%. */
    private class SparklineView(
        activity: Activity, private val values: List<Double>, lineColor: Int, private val textColor: Int
    ) : View(activity) {
        private val density = activity.resources.displayMetrics.density
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor; strokeWidth = 2.5f * density; style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; style = Paint.Style.FILL }
        private val guide = Paint().apply { color = textColor; alpha = 45; strokeWidth = 1f * density }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; alpha = 150; textSize = 11f * density }

        init {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (120 * density).toInt()
            ).apply { topMargin = (6 * density).toInt() }
        }

        override fun onDraw(canvas: Canvas) {
            val left = 38f * density
            val right = width - 12f * density
            val top = 12f * density
            val bottom = height - 12f * density
            fun y(p: Double) = (top + (1 - p.coerceIn(0.0, 100.0) / 100.0) * (bottom - top)).toFloat()
            for (p in intArrayOf(50, 100)) {
                canvas.drawLine(left, y(p.toDouble()), right, y(p.toDouble()), guide)
                canvas.drawText("$p%", 4f * density, y(p.toDouble()) + 4f * density, label)
            }
            val n = values.size
            fun x(i: Int) = if (n == 1) (left + right) / 2f else left + i * (right - left) / (n - 1)
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            canvas.drawPath(path, line)
            values.forEachIndexed { i, v -> canvas.drawCircle(x(i), y(v), 4f * density, dot) }
        }
    }
}
