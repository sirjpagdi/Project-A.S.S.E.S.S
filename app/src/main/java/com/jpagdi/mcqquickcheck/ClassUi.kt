package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * v1.2.0 — small view-building helpers shared by the Classes screens
 * (ClassesActivity, ClassDetailActivity, ClassSheetActivity). These screens
 * reuse activity_placeholder.xml (drawer + scrolling content column) and add
 * their rows in code, the same rounded-card look as MainActivity's saved-test
 * list: bg_card_rounded (themed, deliberately NOT accent-tagged) for cards,
 * accent_maroon-tagged buttons like the placeholder layout's own.
 */
object ClassUi {

    fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun sectionLabel(activity: Activity, text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 14f
        alpha = 0.7f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(activity, 20); bottomMargin = dp(activity, 8) }
    }

    fun note(activity: Activity, text: String, alpha: Float = 0.7f): TextView = TextView(activity).apply {
        this.text = text
        textSize = 13f
        this.alpha = alpha
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(activity, 8); bottomMargin = dp(activity, 4) }
    }

    /** One rounded card row: title, optional subtitle, optional right-aligned [trailing] text. */
    fun card(
        activity: Activity, title: String, subtitle: String? = null, trailing: String? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
            background = ContextCompat.getDrawable(activity, R.drawable.bg_card_rounded)
            clipToOutline = true
            elevation = dp(activity, 2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 10) }
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        column.addView(TextView(activity).apply { text = title; textSize = 17f })
        if (!subtitle.isNullOrBlank()) {
            column.addView(TextView(activity).apply { text = subtitle; textSize = 13f; alpha = 0.65f })
        }
        row.addView(column)
        if (!trailing.isNullOrBlank()) {
            row.addView(TextView(activity).apply {
                text = trailing; textSize = 15f
                setPadding(dp(activity, 12), 0, 0, 0)
            })
        }
        if (onClick != null) {
            val ripple = TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            if (ripple.resourceId != 0) row.foreground = ContextCompat.getDrawable(activity, ripple.resourceId)
            row.isClickable = true
            row.setOnClickListener { HapticUtils.confirm(it); onClick() }
        }
        return row
    }

    /** Full-width rounded action button in the app's accent_maroon style. Call
     *  AccentColorPrefs.applyAccents(activity) after the screen is built so
     *  the tag picks up the user's chosen accent. */
    fun actionButton(activity: Activity, label: String, onClick: () -> Unit): Button = Button(activity).apply {
        text = label
        setTextColor(Color.WHITE)
        background = ContextCompat.getDrawable(activity, R.drawable.bg_card_rounded)
        val tv = TypedValue()
        activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        backgroundTintList = ColorStateList.valueOf(tv.data)
        tag = "accent_maroon"
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(activity, 10) }
        setOnClickListener { HapticUtils.confirm(it); onClick() }
    }

    /** Text field styled like MainActivity's rename dialog (accent-coloured outline + cursor). */
    fun inputField(
        activity: Activity, hint: String, initial: String = "", inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
        maxLength: Int = 0
    ): TextInputLayout {
        val density = activity.resources.displayMetrics.density
        val layout = TextInputLayout(
            android.view.ContextThemeWrapper(activity, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox)
        ).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).toInt() }
        }
        val input = TextInputEditText(layout.context).apply {
            setText(initial)
            setSelection(text?.length ?: 0)
            this.inputType = inputType
            if (maxLength > 0) filters = arrayOf(InputFilter.LengthFilter(maxLength))
        }
        layout.addView(input)
        val accent = AccentColorPrefs.getGroupA(activity)
        layout.boxStrokeColor = accent
        layout.hintTextColor = ColorStateList.valueOf(accent)
        AccentColorPrefs.tintTextInputLayoutCursor(layout, accent)
        AccentColorPrefs.tintTextSelectHandles(input, accent)
        return layout
    }

    /** A small M / F / not-specified radio row. [selected] is "M", "F" or "".
     *  Read the choice back with [genderOf]. */
    fun genderPicker(activity: Activity, selected: String): android.widget.RadioGroup {
        val group = android.widget.RadioGroup(activity).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            setPadding(0, dp(activity, 12), 0, 0)
        }
        fun rb(idValue: Int, label: String, checked: Boolean) = android.widget.RadioButton(activity).apply {
            id = idValue; text = label; isChecked = checked; tag = "accent_gold"
            layoutParams = android.widget.RadioGroup.LayoutParams(
                android.widget.RadioGroup.LayoutParams.WRAP_CONTENT, android.widget.RadioGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(activity, 16) }
        }
        group.addView(rb(GENDER_ID_M, "Male", selected == "M"))
        group.addView(rb(GENDER_ID_F, "Female", selected == "F"))
        group.addView(rb(GENDER_ID_NONE, "Not specified", selected != "M" && selected != "F"))
        return group
    }

    fun genderOf(group: android.widget.RadioGroup): String = when (group.checkedRadioButtonId) {
        GENDER_ID_M -> "M"
        GENDER_ID_F -> "F"
        else -> ""
    }

    private const val GENDER_ID_M = 0x6D01
    private const val GENDER_ID_F = 0x6D02
    private const val GENDER_ID_NONE = 0x6D03

    fun genderLabel(gender: String): String = when (gender) { "M" -> "Male"; "F" -> "Female"; else -> "" }

    fun text(layout: TextInputLayout): String = layout.editText?.text?.toString().orEmpty().trim()

    /** A dialog containing the given [fields]; [onOk] runs on the positive
     *  button and returns true to dismiss (false keeps the dialog open after
     *  showing its own validation message). */
    fun formDialog(
        activity: Activity, title: String, positive: String, fields: List<TextInputLayout>,
        extraMessage: String? = null, extraViews: List<View> = emptyList(), onOk: () -> Boolean
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0)
        }
        if (extraMessage != null) container.addView(TextView(activity).apply { text = extraMessage; textSize = 13f; alpha = 0.75f })
        fields.forEach { container.addView(it) }
        extraViews.forEach { container.addView(it) }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive, null)
            .create()
        dialog.show()
        DialogColors.fix(activity, dialog)
        // Set after show() so a failed validation can keep the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (onOk()) dialog.dismiss() }
    }

    fun confirm(activity: Activity, title: String, message: String, positive: String, onYes: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .create()
        dialog.show()
        DialogColors.fix(activity, dialog)
    }

    fun percentText(earned: Int, total: Int): String =
        if (total <= 0) "" else "${(earned * 1000 / total) / 10.0}%"

    fun hide(vararg views: View?) = views.forEach { it?.visibility = View.GONE }
}
