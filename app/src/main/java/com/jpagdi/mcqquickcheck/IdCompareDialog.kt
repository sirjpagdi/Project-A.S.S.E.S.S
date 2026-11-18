package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlin.math.ceil
import kotlin.math.floor

/** Lets a teacher compare a scanned ID row with likely roster matches. */
object IdCompareDialog {

    fun makeStrip(sheet: Bitmap, digits: Int): Bitmap {
        require(digits in SheetTemplate.ID_MIN_DIGITS..SheetTemplate.ID_MAX_DIGITS)
        val first = SheetTemplate.idBoxRect(0, digits)
        val last = SheetTemplate.idBoxRect(digits - 1, digits)
        val left = floor((first.left - 0.015f) * sheet.width).toInt().coerceIn(0, sheet.width - 1)
        val top = floor((first.top - 0.02f) * sheet.height).toInt().coerceIn(0, sheet.height - 1)
        val right = ceil((last.right + 0.015f) * sheet.width).toInt().coerceIn(left + 1, sheet.width)
        val bottom = ceil((first.bottom + 0.02f) * sheet.height).toInt().coerceIn(top + 1, sheet.height)
        return Bitmap.createBitmap(sheet, left, top, right - left, bottom - top)
    }

    fun show(
        activity: Activity,
        title: String,
        strip: Bitmap?,
        readDigits: String,
        digits: Int,
        candidates: List<Pair<LearnerClass, Learner>>,
        all: List<Pair<LearnerClass, Learner>>,
        onSelect: (LearnerClass, Learner) -> Unit
    ) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ClassUi.dp(activity, 20), ClassUi.dp(activity, 8), ClassUi.dp(activity, 20), 0)
        }
        if (strip != null) {
            content.addView(ImageView(activity).apply {
                setImageBitmap(strip)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                maxHeight = ClassUi.dp(activity, 110)
                contentDescription = "Cropped handwritten ID"
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ClassUi.dp(activity, 8) })
        }
        content.addView(TextView(activity).apply {
            text = "Read as ${readDigits.ifBlank { "not read" }} ($digits digits)"
            textSize = 14f
            setPadding(0, 0, 0, ClassUi.dp(activity, 12))
        })

        lateinit var dialog: AlertDialog
        val options = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val candidateIds = candidates.map { it.second.learnerId }.toSet()
        if (candidates.isNotEmpty()) {
            options.addView(ClassUi.sectionLabel(activity, "SUGGESTED MATCHES"))
            candidates.forEach { (cls, learner) ->
                options.addView(learnerOption(activity, cls, learner) {
                    dialog.dismiss()
                    onSelect(cls, learner)
                })
            }
        }
        val others = all.filter { it.second.learnerId !in candidateIds }
        if (others.isNotEmpty()) {
            options.addView(ClassUi.sectionLabel(activity, if (candidates.isEmpty()) "LEARNERS" else "OTHER LEARNERS"))
            others.forEach { (cls, learner) ->
                options.addView(learnerOption(activity, cls, learner) {
                    dialog.dismiss()
                    onSelect(cls, learner)
                })
            }
        }
        if (all.isEmpty()) options.addView(ClassUi.note(activity, "No enrolled learners are available."))

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(options)
        }
        content.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        DialogColors.fix(activity, dialog)
    }

    private fun learnerOption(
        activity: Activity,
        cls: LearnerClass,
        learner: Learner,
        onClick: () -> Unit
    ) = ClassUi.card(
        activity,
        learner.name,
        "ID ${learner.learnerId} · ${cls.name}",
        onClick = onClick
    )
}
