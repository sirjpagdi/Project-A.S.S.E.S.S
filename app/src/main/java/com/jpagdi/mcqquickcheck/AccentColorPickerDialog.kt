package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Built entirely in code (no new layout XML) — consistent with how other
 * one-off dialogs in this app (e.g. Rename Test) are constructed, and
 * avoids a layout file just for a picker with no reusable structure
 * elsewhere.
 *
 * A fixed palette grid (AccentColorPrefs.PRESET_SWATCHES), not a continuous
 * wheel, per spec. The SeekBar below lightens/darkens whichever swatch is
 * currently selected — it does NOT pick a 17th palette entry; the resulting
 * adjusted color is what actually gets saved on "Save."
 */
object AccentColorPickerDialog {

    fun show(
        activity: Activity,
        title: String,
        currentColor: Int,
        defaultColor: Int,
        onSave: (Int) -> Unit,
        onRevert: () -> Unit
    ) {
        val dp = activity.resources.displayMetrics.density
        var selectedBase = nearestPreset(currentColor)
        var lightnessAdjust = lightnessDeltaFrom(selectedBase, currentColor)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        val preview = View(activity).apply {
            // v2.8 fold-in fix: this used to be hardcoded to
            // activity.resources.displayMetrics.widthPixels — the device's
            // ABSOLUTE screen width, ignoring the container's own 20dp
            // horizontal padding entirely. That made the preview bar wider
            // than the dialog's actual content area, so one side got
            // clipped by the dialog window's edge — which is why only ONE
            // corner showed a rounded radius and the other looked square
            // (it wasn't rounded differently, it was cut off before the
            // curve). MATCH_PARENT respects the container's padding
            // properly, so both corners render identically.
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(currentColor)
            }
        }
        container.addView(preview)

        val grid = GridLayout(activity).apply {
            columnCount = 4
            // v2.8 fold-in fix: was asymmetric (16dp top / 8dp bottom) with
            // no explicit horizontal centering, so the 4x4 grid (fixed
            // natural width from its swatch cells) sat left-aligned with
            // leftover empty space on the right, and unevenly spaced from
            // the preview bar above vs. the slider below. Symmetric 16dp
            // top/bottom centers it within the space between them, and
            // explicit CENTER_HORIZONTAL gravity centers it across the
            // dialog's width instead of hugging the start edge.
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
        }
        grid.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        container.addView(grid)

        val swatchViews = mutableListOf<View>()

        fun refreshPreview() {
            val finalColor = AccentColorPrefs.adjustLightness(selectedBase, lightnessAdjust)
            (preview.background as GradientDrawable).setColor(finalColor)
            swatchViews.forEachIndexed { i, sw ->
                sw.alpha = if (AccentColorPrefs.PRESET_SWATCHES[i] == selectedBase) 1f else 0.55f
            }
        }

        AccentColorPrefs.PRESET_SWATCHES.forEach { swatchColor ->
            val swatch = View(activity).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = (56 * dp).toInt()
                    height = (56 * dp).toInt()
                    setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                }
                background = GradientDrawable().apply {
                    cornerRadius = 6 * dp
                    setColor(swatchColor)
                    setStroke((1.5 * dp).toInt(), Color.argb(80, 0, 0, 0))
                }
                setOnClickListener {
                    selectedBase = swatchColor
                    lightnessAdjust = 0f
                    refreshPreview()
                }
            }
            swatchViews.add(swatch)
            grid.addView(swatch)
        }

        container.addView(TextView(activity).apply {
            text = "Darker \u2194 Lighter"
            textSize = 12f
            alpha = 0.7f
            setPadding(0, (12 * dp).toInt(), 0, 0)
        })

        val slider = SeekBar(activity).apply {
            max = 100
            progress = 50 + (lightnessAdjust * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    lightnessAdjust = (progress - 50) / 100f
                    refreshPreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(slider)

        val revertRow = Button(activity).apply {
            text = "Revert to Default"
            setPadding(0, (8 * dp).toInt(), 0, 0)
            isAllCaps = false
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.START
            val revertTextColor =
                if (ThemePrefs.getTheme(activity) == AppTheme.LIGHT) Color.parseColor("#221111")
                else Color.WHITE
            setTextColor(revertTextColor)
            setCompoundDrawablesWithIntrinsicBounds(
                androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_undo),
                null, null, null
            )
            compoundDrawablePadding = (8 * dp).toInt()
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(revertTextColor)
        }
        container.addView(revertRow)

        val scroll = ScrollView(activity).apply { addView(container) }
        refreshPreview()

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Revert commits immediately and closes the dialog, rather than just
        // staging the default color and still requiring Save — to a
        // non-technical user "Revert to Default" reads as its own complete
        // action, not "preview the default, then remember to also save it."
        revertRow.setOnClickListener {
            onRevert()
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onSave(AccentColorPrefs.adjustLightness(selectedBase, lightnessAdjust))
                dialog.dismiss()
            }
            val color = if (ThemePrefs.getTheme(activity) == AppTheme.LIGHT) Color.parseColor("#221111") else Color.WHITE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
        }
        dialog.show()
    }

    private fun nearestPreset(color: Int): Int =
        AccentColorPrefs.PRESET_SWATCHES.minByOrNull { preset ->
            val dr = Color.red(preset) - Color.red(color)
            val dg = Color.green(preset) - Color.green(color)
            val db = Color.blue(preset) - Color.blue(color)
            dr * dr + dg * dg + db * db
        } ?: AccentColorPrefs.PRESET_SWATCHES.first()

    private fun lightnessDeltaFrom(base: Int, actual: Int): Float {
        val hsvBase = FloatArray(3); Color.colorToHSV(base, hsvBase)
        val hsvActual = FloatArray(3); Color.colorToHSV(actual, hsvActual)
        return (hsvActual[2] - hsvBase[2]).coerceIn(-1f, 1f)
    }
}
