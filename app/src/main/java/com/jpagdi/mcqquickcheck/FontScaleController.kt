package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * v3.9.9: applies FontSizePrefs' current scale to every TextView/Button
 * text size, and to every rounded-card's vertical padding, across an
 * entire view tree — live, with no activity recreate. Call apply(activity)
 * once from onCreate() (after setContentView), same spot
 * AccentColorPrefs.applyAccents() is already called from, and call it again
 * any time the font-scale preference changes while the activity is visible
 * — see FontScaleLiveUpdates for the listener that does that.
 *
 * HOW BASE VALUES ARE CAPTURED: the first time apply() touches a view, it
 * reads that view's CURRENT text size / padding (i.e. whatever the XML
 * declared, unscaled) and stores it as a tag on the view itself before
 * changing anything. Every later apply() call rescales from that stored
 * base rather than from the view's current (possibly already-scaled) value
 * — this is what stops repeated scale changes from compounding (e.g.
 * Large -> Small -> Large landing bigger than the first Large).
 *
 * ROUNDED-CARD PADDING: every bg_card_rounded element across every layout
 * was checked directly (not assumed) — all of them use
 * layout_height="wrap_content", so nothing has a fixed height to rescale;
 * wrap_content already regrows once the text inside it scales up. The one
 * dimension that does NOT auto-adjust is each card's own vertical padding
 * (paddingTop/paddingBottom or shorthand padding=), a fixed dp value in
 * XML. A card opts in by its view ID being listed in CARD_VIEW_IDS below —
 * android:tag isn't used for this because most cards already use their
 * only tag slot for an AccentColorPrefs accent_* color tag, and a view can
 * carry just one android:tag value. Cards that had no id in their original
 * XML (plain grouping boxes like the DEFAULTS/LAYOUT/SCAN BEHAVIOR/
 * STORAGE/ACCENT COLORS section wrappers) had one added specifically so
 * they could be listed here.
 */
object FontScaleController {

    private fun rescaleText(view: TextView, scale: Float) {
        val baseSizePx = (view.getTag(R.id.tag_key_base_text_size) as? Float)
            ?: view.textSize.also { view.setTag(R.id.tag_key_base_text_size, it) }
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, baseSizePx * scale)
    }

    private fun rescaleCardPadding(view: View, scale: Float) {
        val baseTop = (view.getTag(R.id.tag_key_base_pad_top) as? Int) ?: view.paddingTop
        val baseBottom = (view.getTag(R.id.tag_key_base_pad_bottom) as? Int) ?: view.paddingBottom
        if (view.getTag(R.id.tag_key_base_pad_top) == null) {
            view.setTag(R.id.tag_key_base_pad_top, baseTop)
            view.setTag(R.id.tag_key_base_pad_bottom, baseBottom)
        }
        view.setPadding(
            view.paddingLeft,
            (baseTop * scale).toInt(),
            view.paddingRight,
            (baseBottom * scale).toInt()
        )
    }

    fun apply(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content)
        val scale = FontSizePrefs.getFontScale(activity).scale
        walk(root, scale)
    }

    private fun walk(view: View?, scale: Float) {
        if (view == null) return

        if (view is TextView) {
            rescaleText(view, scale)
        }

        if (view.id != View.NO_ID && view.id in CARD_VIEW_IDS) {
            rescaleCardPadding(view, scale)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), scale)
            }
        }
    }

    // Every view ID that carries a bg_card_rounded background across every
    // layout in the app, gathered by direct inspection of every layout XML
    // (not guessed) — see each layout file's own v3.9.9 diff for exactly
    // which elements gained a new id to support this.
    private val CARD_VIEW_IDS: Set<Int> by lazy {
        setOf(
            // activity_generate_sheet.xml
            R.id.cardQuestionCountStepper, R.id.rgChoiceCount, R.id.cardLayoutSection,
            R.id.btnCreateKey, R.id.btnPreview, R.id.btnUseTemplate, R.id.btnGeneratePdf,
            // activity_item_analysis.xml
            R.id.btnExportItemAnalysis,
            // activity_placeholder.xml
            R.id.btnPlaceholderBack, R.id.btnPlaceholderTertiary, R.id.btnPlaceholderSecondary,
            // activity_saved_scans.xml
            R.id.btnTestSummary, R.id.btnScanIntoFolder,
            // activity_scan.xml
            R.id.btnCapture, R.id.btnGrantPermission, R.id.btnUseAsKey, R.id.btnDeleteUnclear,
            R.id.btnSaveUnclear, R.id.btnScanNext, R.id.btnDone,
            // activity_settings.xml
            R.id.cardDefaults, R.id.rgDefaultChoiceCount, R.id.cardQuestionCountStepperSettings,
            R.id.cardColumnsStepperSettings, R.id.cardRowsStepperSettings, R.id.cardScanBehavior,
            R.id.rgStorageResolution, R.id.cardStorage, R.id.cardFontSize, R.id.btnAccentGroupA,
            R.id.btnAccentGroupB, R.id.btnSettingsSave, R.id.cardAccentColors,
            // activity_test_detail.xml
            R.id.btnAnswerSheetTemplate, R.id.btnScannedSheets, R.id.btnItemAnalysis,
            R.id.btnScanFilledSheets,
            // row_saved_scan_card.xml
            R.id.rowSavedScanCard
        )
    }
}
