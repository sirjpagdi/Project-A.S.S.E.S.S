package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Lets the teacher build (or edit) the answer key digitally: for every
 * question, tap the correct bubble instead of typing a letter or scanning a
 * physical key sheet. Each question also has an editable "points" box,
 * defaulting to 1 pt, with an "Apply to all" shortcut for uniform scoring.
 *
 * Built entirely from code-created views (no giant generated XML for however
 * many questions the test has) so it works the same for a 10-item quiz or a
 * 100-item exam.
 */
class AnswerKeyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_EXISTING_KEY = "existing_key"
        const val EXTRA_EXISTING_POINTS = "existing_points"
        const val EXTRA_RESULT_KEY = "result_key"
        const val EXTRA_RESULT_POINTS = "result_points"
    }

    private lateinit var template: SheetTemplate
    private lateinit var selected: IntArray // -1 = unanswered, else 0-based choice index
    private lateinit var pointRows: Array<EditText>
    private lateinit var bubbleRowsButtons: Array<Array<TextView>>
    private lateinit var tvSummary: TextView
    private lateinit var hamburgerButton: ImageView

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)

        template = (intent.getSerializableExtra(EXTRA_TEMPLATE) as? SheetTemplate) ?: run {
            Toast.makeText(this, "No test loaded — set up a test on the home screen first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val existingKey = intent.getIntArrayExtra(EXTRA_EXISTING_KEY)
        val existingPoints = intent.getIntArrayExtra(EXTRA_EXISTING_POINTS)

        selected = IntArray(template.questionCount) { i -> existingKey?.getOrNull(i) ?: -1 }
        pointRows = Array(template.questionCount) { EditText(this) }
        bubbleRowsButtons = Array(template.questionCount) { emptyArray() }

        val drawer = buildWashoutBackground(buildLayout(existingPoints))
        setContentView(drawer)
        DrawerHelper.attach(this, drawer, hamburgerButton)
        // No maroon/gold-tagged views are built in this screen's code-only
        // layout today, so this call is currently a no-op for view tinting
        // — kept for consistency and so the status bar still follows
        // Group A here like every other screen.
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    /** Same washed-out exam-day photo background used on every other
     *  screen (see activity_main.xml's original comment) — this screen's
     *  layout is built entirely in code rather than XML, so the same
     *  ImageView+scrim treatment is replicated here in code instead. Also
     *  wraps the whole thing in a DrawerLayout + inflated drawer_content
     *  panel, same reason: no XML file to just wrap normally, so it's
     *  built the same way in code for this one screen. */
    private fun buildWashoutBackground(content: View): DrawerLayout {
        val frame = FrameLayout(this)
        frame.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.exam_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        frame.addView(
            View(this).apply {
                setBackgroundColor(currentWindowBackgroundColor())
                alpha = 0.96f
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        frame.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val drawer = DrawerLayout(this)
        drawer.addView(frame, DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT))
        val drawerPanel = layoutInflater.inflate(R.layout.drawer_content, drawer, false)
        drawer.addView(
            drawerPanel,
            DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.WRAP_CONTENT, DrawerLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.START)
        )
        return drawer
    }

    private fun currentWindowBackgroundColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
        return typedValue.data
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildLayout(existingPoints: IntArray?): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        hamburgerButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            setPadding(0, 0, 0, dp(8))
            background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).let {
                val d = it.getDrawable(0); it.recycle(); d
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            contentDescription = "Open menu"
        }
        root.addView(hamburgerButton)

        val title = TextView(this).apply {
            text = "Create Answer Key\n${template.testName}"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            // Deliberately NOT tinted with colorPrimary here: in the Black
            // theme, colorPrimary is a near-black surface color meant for
            // backgrounds, not text — using it as text color made this
            // invisible. The theme's default text color is always readable
            // against its own window background.
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = "Tap the correct bubble for each item. Points default to 1 each — adjust any item, or set one value and apply it to all."
            textSize = 13f
            alpha = 0.8f
            setPadding(0, dp(6), 0, dp(12))
        }
        root.addView(hint)

        // v3.7.3: Apply to All moved off this row entirely (see bulkApply
        // below) so the points-per-item stepper (label + minus + input +
        // plus) has the full row width to itself and reliably fits on one
        // line instead of getting squeezed by a neighboring button.
        val bulkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bulkLabel = TextView(this).apply {
            text = "Points per item:"
            textSize = 14f
        }
        val bulkInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }
        // Separate stepper from the per-item one above — this is the
        // BULK/default field, floored at 1 with no dialog (0 is simply
        // never reachable here, unlike the per-item stepper where 0 is a
        // deliberate, confirmed choice).
        val bulkMinus = TextView(this).apply {
            text = "−"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(4) }
            setOnClickListener {
                val current = bulkInput.text.toString().toIntOrNull() ?: 1
                bulkInput.setText((current - 1).coerceAtLeast(1).toString())
            }
        }
        val bulkPlus = TextView(this).apply {
            text = "+"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(2) }
            setOnClickListener {
                val current = bulkInput.text.toString().toIntOrNull() ?: 1
                bulkInput.setText((current + 1).toString())
            }
        }
        bulkRow.addView(bulkLabel)
        bulkRow.addView(bulkMinus)
        bulkRow.addView(bulkInput)
        bulkRow.addView(bulkPlus)
        root.addView(bulkRow)

        // v3.7.3: Apply to All is now its own full-width rounded-card row
        // below the stepper (was squeezed onto the end of bulkRow above,
        // where "Apply to All" text was overflowing at the button's
        // available width). One line now that it has the full row width,
        // icon+text centered as a unit via centerIconAndText. Switched to
        // an explicit bg_card_rounded + accent_maroon tag (was a stock
        // un-tagged Material button) so it visually matches every other
        // maroon-group action button in the app, including this same
        // screen's own Save Answer Key below (gold group) — maroon here
        // deliberately distinguishes "apply a bulk value" from "commit the
        // whole key", the same maroon/gold role split used elsewhere.
        val bulkApply = Button(this).apply {
            text = "Apply to All"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            background = androidx.core.content.ContextCompat.getDrawable(this@AnswerKeyActivity, R.drawable.bg_card_rounded)
            backgroundTintList = android.content.res.ColorStateList.valueOf(currentAccentColorGroupA())
            tag = "accent_maroon"
            val contrastColor = AccentColorPrefs.textColorFor(currentAccentColorGroupA())
            setTextColor(contrastColor)
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@AnswerKeyActivity, R.drawable.ic_check),
                contrastColor
            )
            setOnClickListener {
                val pts = bulkInput.text.toString().toIntOrNull()
                if (pts == null || pts <= 0) {
                    Toast.makeText(this@AnswerKeyActivity, "Enter a valid point value", Toast.LENGTH_SHORT).show()
                } else {
                    pointRows.forEach { it.setText(pts.toString()) }
                    updateSummary()
                }
            }
        }
        root.addView(bulkApply)

        tvSummary = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(10), 0, dp(4))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(tvSummary)

        // Scrollable list of one row per question.
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f; topMargin = dp(4) }
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        for (q in 1..template.questionCount) {
            list.addView(buildQuestionRow(q, existingPoints))
        }
        scroll.addView(list)
        root.addView(scroll)

        val saveButton = Button(this).apply {
            text = "Save Answer Key"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            // v3.7.4: was relying on the stock Material button shape with
            // just a tint color swapped in — looked like a plain default
            // button, not the app's rounded-card design used everywhere
            // else (Apply to All above, Capture Photo, Scan Next/Done,
            // etc). Explicit bg_card_rounded background brings it in line;
            // backgroundTintList below still does the actual gold-vs-user-
            // accent coloring, unchanged from before.
            background = androidx.core.content.ContextCompat.getDrawable(this@AnswerKeyActivity, R.drawable.bg_card_rounded)
            backgroundTintList = android.content.res.ColorStateList.valueOf(currentAccentColor())
            // v3.2 FIX: was hardcoded Color.BLACK on the false assumption
            // that the accent is always gold/light — that held for the
            // default accent, but breaks the moment someone picks a dark
            // accent color in Settings (black text on a dark background is
            // unreadable). AccentColorPrefs.textColorFor() already exists
            // and does exactly this luminance-based contrast decision
            // correctly elsewhere in the app; this button just wasn't
            // using it.
            val contrastColor = AccentColorPrefs.textColorFor(currentAccentColor())
            setTextColor(contrastColor)
            // v3.7.3: icon+text now centered as a unit via
            // centerIconAndText (was setCompoundDrawablesWithIntrinsicBounds,
            // which only centers the text within the leftover space next
            // to a fixed-inset icon, not truly centered together).
            // centerIconAndText re-reads this button's already-set text
            // internally, so it must run after setTextColor above, not
            // before.
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@AnswerKeyActivity, R.drawable.ic_check),
                contrastColor
            )
            setOnClickListener { onSaveClicked() }
        }
        root.addView(saveButton)

        updateSummary()
        return root
    }

    // v3.1 (item 7) FIX: this screen builds its views entirely in Kotlin
    // code, so it never goes through AccentColorPrefs' XML-tag walk() that
    // every other screen uses — it has always had its own ad-hoc color
    // function instead, which is exactly where this bug lived.
    // theme.resolveAttribute(colorAccent) resolves the THEME's fixed static
    // gold (@color/tnhs_gold), not the user's actual chosen accent from
    // Settings. Known call sites depending on this, verified after this
    // fix (not assumed to be covered incidentally):
    //   (a) the selected-bubble gold fill in each answer row (below)
    //   (b) the "Save Answer Key" button's backgroundTintList (above) —
    //       v3.2 also fixed this button's TEXT color the same way (was
    //       hardcoded black, assuming the accent would always be light)
    private fun currentAccentColor(): Int = AccentColorPrefs.getGroupB(this)

    // v3.7.3: Apply to All was switched to the maroon group (group A) per
    // explicit request, distinct from Save Answer Key's gold (group B) —
    // this screen's other accent color lookup above only ever needed
    // group B before now, hence the separate helper rather than
    // parameterizing currentAccentColor() itself.
    private fun currentAccentColorGroupA(): Int = AccentColorPrefs.getGroupA(this)

    /** The theme's own default text color — correct against that theme's own
     *  background by design, unlike colorPrimary (which is a near-black
     *  surface color in the Black theme, not meant for text) or colorAccent
     *  (gold, which is too low-contrast against the light theme's cream
     *  background to use for body text). */
    private fun currentTextColor(): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val color = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()
        return color
    }

    private fun buildQuestionRow(question: Int, existingPoints: IntArray?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        val label = TextView(this).apply {
            text = "$question."
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(label)

        val bubbleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }

        val buttons = Array(template.choiceCount) { choiceIndex ->
            makeBubbleButton(question, choiceIndex).also { bubbleRow.addView(it) }
        }
        bubbleRowsButtons[question - 1] = buttons
        row.addView(bubbleRow)

        val ptsMinus = TextView(this).apply {
            text = "−"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(4) }
            setOnClickListener {
                val current = pointRows[question - 1].text.toString().toIntOrNull() ?: 1
                if (current <= 0) return@setOnClickListener // already floored at 0
                val next = current - 1
                if (next == 0) {
                    // Only the exact 1 -> 0 transition is gated — every
                    // other stepper change (2->1, 3->2, etc.) applies
                    // immediately with no dialog. Declining leaves the
                    // field untouched at its current value.
                    val dialog = AlertDialog.Builder(this@AnswerKeyActivity)
                        .setTitle("Set this item's score to 0?")
                        .setMessage("This item will no longer be included in the total test score.")
                        .setNegativeButton("NO", null)
                        .setPositiveButton("YES") { _, _ ->
                            pointRows[question - 1].setText("0")
                            updateSummary()
                        }
                        .create()
                    dialog.show()
                    val color = if (ThemePrefs.getTheme(this@AnswerKeyActivity) == AppTheme.LIGHT) {
                        Color.parseColor("#221111")
                    } else {
                        Color.WHITE
                    }
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(color)
                    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setTextColor(color)
                } else {
                    pointRows[question - 1].setText(next.toString())
                    updateSummary()
                }
            }
        }
        val ptsField = pointRows[question - 1].apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((existingPoints?.getOrNull(question - 1) ?: 1).toString())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(2)
            }
            addTextChangedListener(afterChange = { updateSummary() })
        }
        val ptsPlus = TextView(this).apply {
            text = "+"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(2) }
            setOnClickListener {
                val current = pointRows[question - 1].text.toString().toIntOrNull() ?: 1
                pointRows[question - 1].setText((current + 1).toString())
                updateSummary()
            }
        }
        val ptsLabel = TextView(this).apply {
            text = "pt"
            textSize = 12f
            alpha = 0.7f
            setPadding(dp(4), 0, 0, 0)
        }
        row.addView(ptsMinus)
        row.addView(ptsField)
        row.addView(ptsPlus)
        row.addView(ptsLabel)

        refreshBubbleRow(question)
        return row
    }

    private fun makeBubbleButton(question: Int, choiceIndex: Int): TextView {
        val letter = template.choiceLetters[choiceIndex]
        val size = dp(36)
        return TextView(this).apply {
            text = letter.toString()
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                selected[question - 1] = choiceIndex
                refreshBubbleRow(question)
                updateSummary()
            }
        }
    }

    private fun refreshBubbleRow(question: Int) {
        val accent = currentAccentColor()
        val textColor = currentTextColor()
        val chosen = selected[question - 1]
        bubbleRowsButtons[question - 1].forEachIndexed { i, button ->
            val isChosen = i == chosen
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isChosen) {
                    setStroke(dp(2), textColor)
                    setColor(accent)
                } else {
                    setStroke(dp(2), textColor)
                    setColor(Color.TRANSPARENT)
                }
            }
            button.background = drawable
            // A gold-filled bubble always reads with black text, regardless of
            // theme. An unselected (transparent-fill) bubble uses the theme's
            // own text color, which is guaranteed to read against that theme's
            // own background.
            button.setTextColor(if (isChosen) Color.BLACK else textColor)
        }
    }

    private fun updateSummary() {
        val answered = selected.count { it >= 0 }
        val totalPoints = pointRows.sumOf { it.text.toString().toIntOrNull() ?: 1 }
        tvSummary.text = "$answered / ${template.questionCount} answered • $totalPoints pts total"
    }

    private fun onSaveClicked() {
        val unanswered = selected.indexOfFirst { it < 0 }
        if (unanswered >= 0) {
            Toast.makeText(this, "Question ${unanswered + 1} has no answer selected yet", Toast.LENGTH_SHORT).show()
            return
        }
        // >= 0, not > 0 — a deliberately-set 0-point item (via the
        // confirmed stepper transition below) must survive saving as 0,
        // not get silently rewritten back to 1 here. Only an unparseable
        // field falls back to 1.
        val points = pointRows.map { it.text.toString().toIntOrNull()?.takeIf { p -> p >= 0 } ?: 1 }

        val resultIntent = Intent()
            .putExtra(EXTRA_RESULT_KEY, selected)
            .putExtra(EXTRA_RESULT_POINTS, points.toIntArray())
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Discard answer key?")
            .setMessage("Changes haven't been saved yet.")
            .setPositiveButton("Discard") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Keep Editing", null)
            .create()
        dialog.show()
        val color = if (ThemePrefs.getTheme(this) == AppTheme.LIGHT) {
            Color.parseColor("#221111")
        } else {
            Color.WHITE
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(color)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setTextColor(color)
    }
}

/** Small helper so text-change listeners can be added as a single trailing lambda. */
private fun EditText.addTextChangedListener(afterChange: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = afterChange()
    })
}
