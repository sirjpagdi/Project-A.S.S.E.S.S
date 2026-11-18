package com.jpagdi.mcqquickcheck

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs

/**
 * Fullscreen result-sheet viewer, opened by double-tapping the small inline
 * preview on the scan result screen, or from the saved-scans list screen.
 * Reuses ZoomableImageView so pinch/pan/double-tap-to-zoom all work
 * identically to the inline preview, just at full screen size with no
 * ScrollView to fight for touch priority.
 *
 * A plain Dialog rather than a new Activity — no back-stack entry, no
 * manifest registration needed, and it's the right weight for "temporarily
 * inspect this image full screen" rather than a real navigation destination.
 */
object FullscreenImageViewer {

    /** Single-image usage (unchanged from before) — no swipe navigation. */
    fun show(context: Context, bitmap: Bitmap) {
        show(context, listOf(bitmap), startIndex = 0, labels = null)
    }

    /** List usage: swipe left/right moves to the next/previous bitmap in
     *  [bitmaps]. [labels], if provided, are shown as a small caption (e.g.
     *  a date/time) so the person can tell which scan they're looking at
     *  while swiping. Swipe only navigates while NOT zoomed in — at 1x it's
     *  unambiguous, but while zoomed a horizontal drag should pan the image
     *  instead, matching how pinch-zoom-then-pan already works elsewhere in
     *  the app.
     *
     *  v3.3: [resolvableScans], if provided, must be the SAME SIZE and
     *  ORDER as [bitmaps] (index-correlated) — enables the ambiguous-answer
     *  panel for whichever scan is currently on screen, listing which
     *  questions were left unresolved.
     *  v3.6.3: this panel is now READ-ONLY here — resolving a question is
     *  only available on the live result screen right after capture
     *  (ScanActivity), before Save. This just displays [resolvableScans]'
     *  ambiguousQuestions, struck through; there's nothing left to persist,
     *  so the onResolved callback this used to take is gone too.
     *  v3.6.4: a trash icon (top-left, mirroring the close button's
     *  top-right position) lets a scan be deleted right from this preview
     *  — only shown when [resolvableScans] is provided, since the simple
     *  single-bitmap overload has no scan identity to delete. [onDeleted]
     *  fires immediately after each successful delete (not just on final
     *  dismiss), so the caller's own list/thumbnails can stay in sync while
     *  the person keeps swiping through what's left. */
    fun show(
        context: Context,
        bitmaps: List<Bitmap>,
        startIndex: Int,
        labels: List<String>? = null,
        resolvableScans: List<ScannedSheetStorage.SavedScan>? = null,
        onDeleted: (() -> Unit)? = null
    ) {
        if (bitmaps.isEmpty()) return
        var currentIndex = startIndex.coerceIn(0, bitmaps.size - 1)
        val mutableBitmaps = bitmaps.toMutableList()
        val mutableLabels = labels?.toMutableList()
        var scans = resolvableScans?.toMutableList()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val zoomView = ZoomableImageView(context).apply {
            fullscreenMode = true
            setImageBitmap(mutableBitmaps[currentIndex])
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val captionView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#80000000"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL }
            visibility = if (mutableLabels != null && mutableBitmaps.size > 1) android.view.View.VISIBLE else android.view.View.GONE
        }

        fun updateCaption() {
            if (mutableLabels != null && currentIndex < mutableLabels.size) {
                val position = "${currentIndex + 1} / ${mutableBitmaps.size}"
                captionView.text = "${mutableLabels[currentIndex]}   ·   $position"
                captionView.visibility = if (mutableBitmaps.size > 1) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        updateCaption()

        // v3.3: ambiguous-answer resolution panel — sits in its own bar
        // ABOVE the caption bar (not merged into it), since it can grow to
        // several rows tall on a scan with multiple unresolved questions,
        // and the caption bar's fixed single-line height shouldn't stretch
        // to accommodate that.
        // v3.6.3: no longer interactive here — resolving an ambiguous
        // question is only available on the live result screen right after
        // capture (ScanActivity), before Save. Once a scan is saved, this
        // panel is read-only: it just lists which questions were left
        // unresolved (struck through), so it can no longer be re-opened for
        // editing from the Scanned Sheets folder.
        val ambiguousPanelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#B0000000"))
            visibility = android.view.View.GONE
        }
        val ambiguousBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            // Sits just above the caption bar's own ~24dp+text height —
            // approximate but the caption bar is only ever one line tall,
            // so a fixed offset is safe here (unlike the panel itself,
            // whose own height is genuinely variable).
            bottomMargin = (56 * context.resources.displayMetrics.density).toInt()
        }
        ambiguousPanelContainer.layoutParams = ambiguousBarParams

        fun refreshAmbiguousPanel() {
            val scan = scans?.getOrNull(currentIndex)
            val ambiguousQs = scan?.ambiguousQuestions
            if (scan == null || ambiguousQs.isNullOrEmpty()) {
                ambiguousPanelContainer.visibility = android.view.View.GONE
                return
            }
            val test = TestStorage.loadTestOrNull(context, scan.testName)
            if (test == null) {
                ambiguousPanelContainer.visibility = android.view.View.GONE
                return
            }
            AmbiguousAnswerPanel.populate(
                context, ambiguousPanelContainer, ambiguousQs.toList(), test.template.choiceLetters,
                emptyMap(), Color.parseColor("#FFC107"), Color.WHITE, readOnly = true
            )
            ambiguousPanelContainer.visibility = android.view.View.VISIBLE
        }
        refreshAmbiguousPanel()

        val closeButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            val sizePx = (40 * context.resources.displayMetrics.density).toInt()
            val marginPx = (16 * context.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = marginPx
                marginEnd = marginPx
            }
            setPadding(sizePx / 4, sizePx / 4, sizePx / 4, sizePx / 4)
            setOnClickListener { dialog.dismiss() }
        }

        // v3.6.6: moved from top-left (mirroring the close button) to
        // bottom-right, just below the image, per explicit accessibility
        // request — top corners are the hardest spot to reach one-handed
        // on a large phone, bottom corners are the easiest. bottomMargin is
        // deliberately larger than a plain "clear the edge" amount (16dp)
        // — it needs to clear the caption bar ("3 / 12" position text),
        // which sits BOTTOM|CENTER at the very bottom edge and would
        // otherwise sit under/overlap this button. Same clearance value
        // ambiguousBarParams already uses below for the same reason.
        val trashButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.WHITE)
            val sizePx = (40 * context.resources.displayMetrics.density).toInt()
            val marginEndPx = (16 * context.resources.displayMetrics.density).toInt()
            val marginBottomPx = (56 * context.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                bottomMargin = marginBottomPx
                marginEnd = marginEndPx
            }
            setPadding(sizePx / 4, sizePx / 4, sizePx / 4, sizePx / 4)
            visibility = if (scans != null) android.view.View.VISIBLE else android.view.View.GONE
        }

        fun deleteCurrentScan() {
            val scan = scans?.getOrNull(currentIndex) ?: return
            val confirmDialog = AlertDialog.Builder(context)
                .setTitle("Delete this scan?")
                .setMessage("This scanned sheet will be permanently deleted. This can't be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    ScannedSheetStorage.deleteScan(context, scan.id)
                    mutableBitmaps.removeAt(currentIndex)
                    mutableLabels?.removeAt(currentIndex)
                    scans?.removeAt(currentIndex)
                    onDeleted?.invoke()

                    if (mutableBitmaps.isEmpty()) {
                        dialog.dismiss()
                        return@setPositiveButton
                    }
                    currentIndex = currentIndex.coerceIn(0, mutableBitmaps.size - 1)
                    zoomView.setImageBitmap(mutableBitmaps[currentIndex])
                    zoomView.resetToFit()
                    updateCaption()
                    refreshAmbiguousPanel()
                }
                .setNegativeButton("Cancel", null)
                .create()
            confirmDialog.show()
            fixDialogButtonColors(context, confirmDialog)
        }
        trashButton.setOnClickListener { HapticUtils.confirm(it); deleteCurrentScan() }

        // Swipe navigation, re-diagnosed from scratch (the previous
        // implementation was never actually reachable — see below).
        //
        // ROOT CAUSE of the original "swipe does nothing" bug: it was
        // wired via `root.setOnTouchListener { ... }`, where root is
        // zoomView's PARENT. A ViewGroup's own onTouchListener/onTouchEvent
        // only fires for a gesture if none of its children claimed it via
        // dispatchTouchEvent returning true on ACTION_DOWN. This session's
        // own zoom fix (ZoomableImageView always returning true for
        // ACTION_DOWN, so it can see the rest of a pinch/tap gesture) means
        // zoomView ALWAYS claims every gesture at ACTION_DOWN — so root's
        // onTouchListener was structurally guaranteed to never receive a
        // single event, on any device, regardless of swipe distance or
        // speed. It wasn't a threshold-tuning problem; the listener was
        // dead code from the moment it was written, made unreachable by
        // the zoom fix. Fixed by overriding dispatchTouchEvent on a small
        // FrameLayout subclass instead of using setOnTouchListener —
        // dispatchTouchEvent on a ViewGroup runs for every event
        // regardless of which child ends up consuming it, so this can
        // observe the gesture for fling detection without needing to win
        // (or even participate in) the child's touch-claim in the first
        // place.
        val swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (mutableBitmaps.size <= 1 || e1 == null || zoomView.isZoomed) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // Require a clearly horizontal, clearly fast gesture —
                // guards against an ordinary diagonal pan/scroll being
                // misread as a swipe.
                if (abs(dx) < 120 || abs(dx) < abs(dy) * 1.5f || abs(velocityX) < 400) return false
                if (dx < 0 && currentIndex < mutableBitmaps.size - 1) {
                    currentIndex++
                } else if (dx > 0 && currentIndex > 0) {
                    currentIndex--
                } else {
                    return false
                }
                HapticUtils.tick(zoomView)
                zoomView.setImageBitmap(mutableBitmaps[currentIndex])
                zoomView.resetToFit()
                updateCaption()
                refreshAmbiguousPanel()
                return true
            }
        })

        val root = object : FrameLayout(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // Feed the detector a look at every event BEFORE normal
                // dispatch runs, using its return value only to decide
                // whether OUR gesture fired — never to block normal
                // dispatch. zoomView still gets the full, unaltered event
                // stream via super.dispatchTouchEvent below regardless of
                // what swipeDetector does with its copy, so pinch/pan/
                // double-tap-to-zoom are completely unaffected.
                swipeDetector.onTouchEvent(event)
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            addView(zoomView)
            addView(captionView)
            addView(ambiguousPanelContainer)
            addView(closeButton)
            addView(trashButton)
        }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    /** Same fix used everywhere else in this app — AlertDialog buttons
     *  default to colorPrimary text, unreadable in Dark/Black themes.
     *  Identical to TestSummaryDialog.kt's copy — both are plain objects,
     *  not Activities, so this can't be shared via a common base class the
     *  way every Activity's own copy is. */
    private fun fixDialogButtonColors(context: Context, dialog: AlertDialog) {
        HapticUtils.confirm(dialog.window?.decorView)
        val color = if (ThemePrefs.getTheme(context) == AppTheme.LIGHT) {
            Color.parseColor("#221111")
        } else {
            Color.WHITE
        }
        for (which in intArrayOf(
            android.content.DialogInterface.BUTTON_POSITIVE,
            android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL
        )) {
            dialog.getButton(which)?.setTextColor(color)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
