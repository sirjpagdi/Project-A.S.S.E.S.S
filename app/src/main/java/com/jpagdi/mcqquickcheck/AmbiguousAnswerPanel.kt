package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * v3.3: builds the "tap A-D (or A-E) to resolve this question" rows shown
 * below a graded/scanned sheet for every question still flagged ambiguous.
 * Used by both ScanActivity's live result screen and the Scanned Sheets
 * folder's saved-scan review (FullscreenImageViewer) — the two screens hold
 * very different data (ScanActivity has the full in-memory QuestionResult
 * list; the folder screen only has a saved scan's id + testName), so each
 * caller supplies its own onAssign callback that knows how to persist the
 * choice in whatever way makes sense for that screen. This object only
 * builds the tappable rows themselves.
 */
object AmbiguousAnswerPanel {

    /** Rebuilds [container] from scratch with one row per entry in
     *  [questionNumbers] (already-resolved questions should simply be left
     *  out of that list by the caller). [resolved] marks which of those
     *  rows should show their assigned letter highlighted (a question can
     *  be re-tapped to change the assignment). [onAssign] fires with
     *  (questionNumber, choiceIndex) whenever a bubble is tapped; the
     *  caller is responsible for calling populate() again afterward with
     *  updated state — this function never mutates its own inputs.
     *  v3.6.3: [readOnly], when true, shows each question number struck
     *  through with no tappable bubbles at all instead of the interactive
     *  A-D/A-E row — [onAssign] and [resolved] are unused in this mode.
     *  For a scan that's already been saved: resolving an ambiguous
     *  question is only ever available on the live result screen, right
     *  after capture, before Save — this view just needs to show which
     *  questions went uncounted, not offer another chance to assign them. */
    fun populate(
        context: Context,
        container: LinearLayout,
        questionNumbers: List<Int>,
        choiceLetters: List<Char>,
        resolved: Map<Int, Int>,
        accentColor: Int,
        textColor: Int,
        readOnly: Boolean = false,
        onAssign: (questionNumber: Int, choiceIndex: Int) -> Unit = { _, _ -> }
    ) {
        container.removeAllViews()
        if (questionNumbers.isEmpty()) {
            container.visibility = android.view.View.GONE
            return
        }
        container.visibility = android.view.View.VISIBLE

        fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

        val header = TextView(context).apply {
            text = if (readOnly) {
                "Left unresolved — no answer counted:"
            } else {
                "Ambiguous — tap to assign the student's answer:"
            }
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, dp(4), 0, dp(4))
        }
        container.addView(header)

        if (readOnly) {
            val numbers = questionNumbers.sorted().joinToString(", ")
            val row = TextView(context).apply {
                text = numbers
                textSize = 14f
                setTextColor(textColor)
                alpha = 0.7f
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                setPadding(0, dp(4), 0, dp(4))
            }
            container.addView(row)
            return
        }

        questionNumbers.sorted().forEach { qNum ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(context).apply {
                text = "$qNum."
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(label)

            val chosen = resolved[qNum]
            choiceLetters.forEachIndexed { choiceIndex, letter ->
                val isChosen = choiceIndex == chosen
                val bubble = TextView(context).apply {
                    text = letter.toString()
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(if (isChosen) Color.BLACK else textColor)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(6) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(dp(2), textColor)
                        setColor(if (isChosen) accentColor else Color.TRANSPARENT)
                    }
                    setOnClickListener { onAssign(qNum, choiceIndex) }
                }
                row.addView(bubble)
            }
            container.addView(row)
        }
    }
}
