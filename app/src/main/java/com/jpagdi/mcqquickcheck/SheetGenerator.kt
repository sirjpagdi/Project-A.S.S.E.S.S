package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Draws the printable bubble sheet PDF using Android's built-in PdfDocument
 * (no external library). Reads all geometry from SheetTemplate so the printed
 * sheet always matches what MarkerDetector / BubbleGrader expect.
 *
 * To save paper, 2 or 4 independent copies of the same template can be tiled
 * onto one physical page (see PaperLayout). Each copy gets its own
 * 4 corner markers and a dashed cut line, since each is meant to be cut apart
 * and scanned individually.
 *
 * Fonts: Android does not ship the actual Arial font. Typeface.SANS_SERIF
 * (Roboto on most devices) is used here as the closest built-in equivalent —
 * a clean, formal sans-serif rather than a decorative or monospace face.
 */
object SheetGenerator {

    /** v2.4: used only by the stress-test/sample sheet batch (see
     *  generateMultiVariant's defectsPerRegion param below) — every other
     *  caller passes defects=null and gets the exact same clean-fill
     *  behavior as before.
     *  - NONE: normal, correctly filled per the answer key (unchanged).
     *  - BLANK: question is left completely unmarked — genuinely blank,
     *    not just below a threshold.
     *  - PARTIAL: the correct bubble is filled at a visibly reduced radius,
     *    simulating a light/incomplete mark rather than either "clearly
     *    filled" or "clearly blank."
     *  - OVERFLOW: the correct bubble is filled at a radius LARGER than the
     *    printed outline, simulating ink that bled past the bubble's edge.
     *  - MULTI: the correct bubble AND one other bubble both get filled,
     *    simulating a double-marked question.
     */
    enum class MarkDefect { NONE, BLANK, PARTIAL, OVERFLOW, MULTI }

    // v3.0: PDF-generated answer sheets now use the same custom font as
    // the rest of the app, per explicit request ("no system font... which
    // also carried thru answer sheets"). PdfDocument/Canvas text drawing
    // needs an actual Typeface object, not a theme attribute — and
    // loading a res/font resource requires a Context, which this object's
    // functions never took before (generate() etc. only need template/
    // file/layout). Rather than thread a Context through every function
    // signature and every call site across GenerateSheetActivity and
    // TestSheetBatchGenerator, ensureFontLoaded() is called once from
    // MainActivity.onCreate() — MainActivity is this app's home screen
    // and every session starts there before any sheet can be generated,
    // so by the time generate()/generateMultiVariant()/etc. run, the font is
    // already cached in this singleton object for the rest of the
    // process's lifetime. Falls back to the system sans-serif font (same
    // as before v3.0) if that never happened, rather than crashing.
    // v3.6.8: font swapped from Century Gothic to Montserrat — see
    // montserrat_family.xml's doc comment for why.
    private var customTypeface: Typeface? = null
    private var customTypefaceBold: Typeface? = null

    fun ensureFontLoaded(context: android.content.Context) {
        // v3.9.9: always store the ApplicationContext, never whatever
        // Context the caller passed in — MainActivity passes `this` (the
        // Activity itself), and holding a static reference to an Activity
        // is a real memory leak (keeps its whole View tree alive forever
        // after the Activity is destroyed). applicationContext is safe to
        // hold statically for the app's lifetime.
        contextForFontLoad = context.applicationContext
        if (customTypeface != null) return
        customTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.montserrat_regular)
        customTypefaceBold = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.montserrat_bold)
    }

    private val SANS: Typeface get() = customTypeface ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val SANS_BOLD: Typeface get() = customTypefaceBold ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    /**
     * [answerKey], if supplied, shades the correct bubble for each question
     * (same 0-based-index format as renderPreviewBitmap) — defaults to null
     * so every existing caller keeps producing a blank, unmarked sheet
     * exactly as before. Added for the batch-generated prefilled test
     * sheets (AboutActivity's long-press entry point), which need actual
     * marked bubbles rather than a blank template.
     */
    fun generate(
        template: SheetTemplate,
        outputFile: File,
        paperLayout: PaperLayout,
        answerKey: List<Int>? = null,
        paperSize: PaperSize = PaperSize.LETTER,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO,
        // v1.1.0: opt-in Parent/Guardian signature line — caller passes
        // the resolved SettingsPrefs value rather than this object reading
        // it itself, same reasoning as ensureFontLoaded()'s doc comment
        // above (SheetGenerator deliberately never takes a Context).
        showParentSignatureLine: Boolean = false
    ) {
        val document = PdfDocument()
        // The actual physical PDF page is [paperSize]'s dimensions — but
        // everything below is still laid out in the fixed PAGE_WIDTH x
        // PAGE_HEIGHT (Letter) reference space that SheetTemplate's
        // fractions, MarkerDetector, and BubbleGrader all assume. This is
        // deliberate: it's a PRINTING preference, not a template change —
        // see PaperSize.kt for why scan-time geometry must never depend on
        // what paper size a sheet happened to be printed on.
        val pageInfo = PdfDocument.PageInfo.Builder(
            paperSize.widthPt.toInt(),
            paperSize.heightPt.toInt(),
            1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Uniform scale (never stretched — that would turn the square
        // corner guides into rectangles) to fit the reference content
        // inside the selected paper size. A4 is narrower than Letter
        // (595pt vs 612pt) so this scales DOWN slightly for A4; Folio is
        // the same width but taller, so it scales by 1.0. Multi-scale
        // template matching in MarkerDetector already tolerates size
        // variation, so a slight uniform scale here doesn't affect scan
        // reliability. v3.6.7: offsetX/offsetY used to be computed ONCE
        // here for the whole page — see quadrantOffset() below, called
        // per-region instead, for why that changed.
        val scale = minOf(
            paperSize.widthPt / SheetTemplate.PAGE_WIDTH,
            paperSize.heightPt / SheetTemplate.PAGE_HEIGHT
        )

        val regions = regionsForLayout(paperLayout, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT)
        regions.forEach { region ->
            val (offsetX, offsetY) = quadrantOffset(region, paperLayout, paperSize, scale)
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            drawSheetInRegion(
                canvas, template, region.inset(SheetTemplate.QUARTER_TILE_INSET), answerKey,
                bubbleToCellRatio = bubbleToCellRatio, isPdf = true,
                outerRegion = region, showParentSignatureLine = showParentSignatureLine
            )
            canvas.restore()
        }
        drawCutLines(canvas, paperLayout, paperSize)

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    /**
     * Renders a preview bitmap of one tiled sheet copy, at [widthPx] wide
     * (height computed automatically to match that tile's real proportions
     * for the given [paperLayout] — a ½-page tile is landscape-shaped, not
     * the same portrait shape as a full or ¼ page tile, so this has to know
     * which layout it's previewing or the preview comes out squished).
     * Reuses the exact same drawing code as the real printed PDF, so what's
     * shown in a live preview while adjusting settings always matches what
     * actually prints — nothing to accidentally let drift between the two.
     *
     * If [answerKey] is supplied (one 0-based choice index per question, A=0),
     * the correct bubble for each question is filled in solid, so this can
     * also serve as a preview of the answer key sheet.
     */
    fun renderPreviewBitmap(
        template: SheetTemplate,
        paperLayout: PaperLayout,
        widthPx: Int,
        answerKey: List<Int>? = null,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO,
        context: android.content.Context? = null
    ): Bitmap {
        // v3.9.9: regression fix — the preview silently fell back to the
        // system font instead of Montserrat. Root cause: ensureFontLoaded()
        // was only ever called from MainActivity.onCreate(), and this
        // function (called from GenerateSheetActivity, not MainActivity)
        // never called it itself — it just read customTypeface directly via
        // the SANS/SANS_BOLD getters, which silently fall back to the
        // system font if that's still null (by design, so a slow/failed
        // font load can't crash sheet generation — see the getters below).
        // That fallback is the right behavior for a genuine load failure,
        // but wrong here: this was really just a missing warm-up call, not
        // a failure.
        //
        // [context] is optional and defaults to null so every existing call
        // site stays source-compatible without edits, but a caller that
        // hits this before MainActivity has ever run (the actual
        // regression) needs to pass its own Context for the very first
        // load to succeed — falling back to the previously-cached
        // contextForFontLoad (set by any prior ensureFontLoaded() call)
        // only helps on the SECOND and later calls, not the first. Updated
        // GenerateSheetActivity's call site to pass `this`.
        (context ?: contextForFontLoad)?.let { ensureFontLoaded(it) }

        val sampleTile = regionsForLayout(paperLayout, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT).first()
        val tileAspect = sampleTile.width / sampleTile.height
        val heightPx = (widthPx / tileAspect).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val region = Region(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        drawSheetInRegion(canvas, template, region, answerKey, bubbleToCellRatio = bubbleToCellRatio)
        return bitmap
    }

    // v3.9.9: set by ensureFontLoaded() itself (converted to
    // applicationContext there — see that function's own comment) so
    // renderPreviewBitmap can trigger a font load without needing its own
    // signature changed. Every existing call site of renderPreviewBitmap
    // across the app stays source-compatible.
    private var contextForFontLoad: android.content.Context? = null

    private data class Region(val left: Float, val top: Float, val width: Float, val height: Float)

    /** Returns the pixel rectangles (in [pageWidth] x [pageHeight] space)
     *  for each tiled sheet copy. v3.4: QUARTER is now the only
     *  PaperLayout value (HALF_CROSS/HALF_LENGTH removed) — kept as a
     *  `when` over the enum (rather than inlined) so this stays exhaustive
     *  and gets a compile error if a layout is ever added back. */
    private fun regionsForLayout(paperLayout: PaperLayout, pageWidth: Float, pageHeight: Float): List<Region> {
        val w = pageWidth
        val h = pageHeight
        return when (paperLayout) {
            PaperLayout.QUARTER -> listOf( // 2x2 quarters
                Region(0f, 0f, w / 2f, h / 2f),
                Region(w / 2f, 0f, w / 2f, h / 2f),
                Region(0f, h / 2f, w / 2f, h / 2f),
                Region(w / 2f, h / 2f, w / 2f, h / 2f)
            )
        }
    }

    /** v3.6.4: symmetric inset on all 4 sides of [this], for the CONTENT
     *  box only — see SheetTemplate.QUARTER_TILE_INSET's doc comment for
     *  why this is deliberately a separate step from regionsForLayout()
     *  rather than baked into its output. drawSheetInRegion needs no
     *  changes to pick this up: it already treats whatever Region it's
     *  given as the full content box via its region.left/top/width/height-
     *  based px() function. */
    private fun Region.inset(amount: Float): Region =
        Region(left + amount, top + amount, width - 2f * amount, height - 2f * amount)

    /** v3.6.7: draws the full cut cross exactly once, in absolute physical
     *  page coordinates (paperSize.widthPt/heightPt) — NOT inside any
     *  per-region canvas transform. Previously this was called once per
     *  region using reference-space coordinates inside the (single, shared)
     *  canvas transform; now that each quadrant gets its OWN independent
     *  scale/translate (see quadrantOffset below), there's no one shared
     *  transform left for a reference-space coordinate to be meaningful
     *  in. The cut line is still exactly the true physical half in both
     *  axes — the same physical half quadrantOffset's own col/row split
     *  uses — so the printed line and the per-quadrant centering always
     *  agree on where "the middle" is. */
    private fun drawCutLines(canvas: Canvas, paperLayout: PaperLayout, paperSize: PaperSize) {
        if (paperLayout != PaperLayout.QUARTER) return // only current PaperLayout value
        val paint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.75f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        val midX = paperSize.widthPt / 2f
        val midY = paperSize.heightPt / 2f
        canvas.drawLine(0f, midY, paperSize.widthPt, midY, paint)
        canvas.drawLine(midX, 0f, midX, paperSize.heightPt, paint)
    }

    /**
     * v3.6.7: physical (scale, offsetX, offsetY)-equivalent placement for a
     * single region — returns just the translate, since scale is already
     * shared/uniform across every region (see the class-level rationale in
     * generate()). Previously ALL regions shared one canvas.translate/scale
     * for the whole 612x792 reference page, uniformly centered on the
     * physical paper as a single rigid block. That kept the OVERALL page's
     * top/bottom margins equal — but for QUARTER layout specifically, a
     * person doesn't cut out "the whole page," they cut out ONE quadrant
     * at a time, and each quadrant's OWN margins (within its own quarter
     * of the physical paper) were NOT independently equal: any extra
     * vertical space from Folio being taller (or A4's slightly different
     * aspect) all landed as one lump at the outer top edge of the top-row
     * quadrants and one lump at the outer bottom edge of the bottom-row
     * quadrants, with almost nothing near the shared middle cut line — so
     * a cut-out top quadrant had a big gap above its Name/Date table and
     * almost none above the grid's last row, and a cut-out bottom quadrant
     * had the opposite. Now each region is centered within its OWN
     * quarter of the physical page independently, so every individually-
     * cut piece gets equal margin on all 4 of its own sides. For any
     * non-QUARTER layout (there are none currently — see PaperLayout.kt),
     * this falls back to the old single-block centering.
     */
    private fun quadrantOffset(
        region: Region, paperLayout: PaperLayout, paperSize: PaperSize, scale: Float
    ): Pair<Float, Float> {
        if (paperLayout != PaperLayout.QUARTER) {
            val scaledWidth = SheetTemplate.PAGE_WIDTH * scale
            val scaledHeight = SheetTemplate.PAGE_HEIGHT * scale
            return (paperSize.widthPt - scaledWidth) / 2f to (paperSize.heightPt - scaledHeight) / 2f
        }
        val tileW = SheetTemplate.PAGE_WIDTH / 2f
        val tileH = SheetTemplate.PAGE_HEIGHT / 2f
        val halfPaperW = paperSize.widthPt / 2f
        val halfPaperH = paperSize.heightPt / 2f
        val col = if (region.left < tileW / 2f) 0 else 1
        val row = if (region.top < tileH / 2f) 0 else 1
        val marginXWithinHalf = (halfPaperW - tileW * scale) / 2f
        val marginYWithinHalf = (halfPaperH - tileH * scale) / 2f
        val offsetX = col * halfPaperW + marginXWithinHalf - region.left * scale
        val offsetY = row * halfPaperH + marginYWithinHalf - region.top * scale
        return offsetX to offsetY
    }

    private fun drawSheetInRegion(
        canvas: Canvas, template: SheetTemplate, region: Region, answerKey: List<Int>?,
        defects: List<MarkDefect>? = null,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO,
        isPdf: Boolean = false,
        // v1.1.0: the PRE-inset quadrant tile (i.e. what `region` looked
        // like before .inset(QUARTER_TILE_INSET) was applied by the
        // caller) — only used to place the parent/guardian signature
        // line in the printable margin BELOW the scannable content box,
        // see drawParentSignatureLine's own doc comment for why that
        // needs a separate, larger coordinate space than `region` itself.
        // null for renderPreviewBitmap's call (no outer tile concept for
        // a single preview crop), which is also why the signature line
        // never appears in the live preview — see showParentSignatureLine.
        outerRegion: Region? = null,
        showParentSignatureLine: Boolean = false
    ) {
        fun px(fx: Float, fy: Float): Pair<Float, Float> =
            (region.left + fx * region.width) to (region.top + fy * region.height)

        // Scale text/stroke sizes down for smaller regions so things don't overlap.
        // Uses the SMALLER of the width/height ratios (not their average) so
        // nothing overflows either dimension — a ½-page region is full-width
        // but half-height, and averaging the two ratios used to keep text/
        // strokes too large for how little vertical room a ½-page tile has.
        val regionScale = minOf(region.width / SheetTemplate.PAGE_WIDTH, region.height / SheetTemplate.PAGE_HEIGHT)

        drawHeader(canvas, template, region, regionScale, ::px, isPdf)
        drawMarkers(canvas, template, region, ::px)
        drawGrid(canvas, template, region, regionScale, ::px, answerKey, defects, bubbleToCellRatio)

        // v1.1.0: PDF-only (isPdf), settings-gated (showParentSignatureLine),
        // per explicit request — fine if it never appears in the live
        // Settings/Generate preview as long as it prints on the real sheet.
        if (isPdf && showParentSignatureLine && outerRegion != null) {
            drawParentSignatureLine(canvas, outerRegion)
        }
    }

    /**
     * Draws a centered blank line + "Parent/Guardian Signature" label just
     * below the scannable content box, per explicit request. Deliberately
     * positioned using [outerRegion] — the quadrant tile's bounds BEFORE
     * QUARTER_TILE_INSET was subtracted, i.e. drawSheetInRegion's own
     * `region` after inset — rather than trying to fit inside GRID_BOTTOM's
     * own 0.97 fraction: GRID_BOTTOM and the corner markers' own bottom
     * margin (MARKER_MARGIN_Y) already both reach that same 0.97 line
     * inside the CONTENT box, leaving no real room there for anything
     * else. outerRegion is the full pre-inset tile, so its own bottom
     * ~QUARTER_TILE_INSET's-worth of margin (the same printable strip a
     * cut-out quadrant naturally has below its content) is real,
     * unclaimed space to draw into instead.
     */
    private fun drawParentSignatureLine(canvas: Canvas, outerRegion: Region) {
        fun pxOuter(fx: Float, fy: Float): Pair<Float, Float> =
            (outerRegion.left + fx * outerRegion.width) to (outerRegion.top + fy * outerRegion.height)

        val lineHalfWidth = 0.22f
        val lineY = 0.975f
        val labelY = 0.995f

        val (lineStartX, lineY1) = pxOuter(0.5f - lineHalfWidth, lineY)
        val (lineEndX, _) = pxOuter(0.5f + lineHalfWidth, lineY)
        canvas.drawLine(lineStartX, lineY1, lineEndX, lineY1, Paint().apply {
            color = Color.BLACK
            strokeWidth = 0.75f
            isAntiAlias = true
        })

        val (labelX, labelYpt) = pxOuter(0.5f, labelY)
        canvas.drawText("Parent/Guardian Signature", labelX, labelYpt, Paint().apply {
            color = Color.BLACK
            textSize = 6.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = SANS
        })
    }

    /**
     * Draws the details table (Name/Date row, Section/Subject Teacher
     * row, Score row) and the test title below it, with real cell borders
     * instead of underscore blanks.
     */
    private fun drawHeader(
        canvas: Canvas, template: SheetTemplate, region: Region, scale: Float,
        px: (Float, Float) -> Pair<Float, Float>, isPdf: Boolean
    ) {
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * scale
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f * scale
            typeface = SANS
            textAlign = Paint.Align.LEFT
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f * scale
            typeface = SANS_BOLD
            // Defensive backup only — typeface = SANS_BOLD above should be
            // sufficient on its own (Typeface.create(..., Typeface.BOLD) is
            // the standard way to get a bold typeface), but some OEM font
            // substitution situations have been known to silently ignore
            // the BOLD style flag while still honoring isFakeBoldText. Not
            // a confirmed fix for a reproduced bug — added because I
            // couldn't find an actual defect in the existing code on
            // inspection, only this belt-and-suspenders option.
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val midX = SheetTemplate.GRID_LEFT +
            (SheetTemplate.GRID_RIGHT - SheetTemplate.GRID_LEFT) * SheetTemplate.TABLE_MID_X_FRACTION

        // The 4-cell student-details table (Name/Date/Gr.&Section/Score) is
        // drawn as a single rounded-corner outer box, with straight internal
        // divider lines for the row split and the two column splits — rather
        // than each cell getting its own independent rounded rect (that would
        // round the *inner* corners too, which looks like 4 separate pills
        // instead of one table). This is a pure rendering choice: the table's
        // fractional geometry (GRID_LEFT/RIGHT/TABLE_TOP etc.) is unchanged
        // and still comes from SheetTemplate, so nothing the scanner reads
        // (corner markers, bubble grid) is affected.
        val (outerLeft, outerTop) = px(SheetTemplate.GRID_LEFT, SheetTemplate.TABLE_TOP)
        // v1.2.0: a template with the ID row (SheetTemplate.idDigits) extends the
        // table's outer box down to TABLE_ROW3_BOTTOM; every other sheet keeps
        // the original two-row table, byte-for-byte as before.
        val tableBottomFraction = if (template.idDigits > 0) SheetTemplate.TABLE_ROW3_BOTTOM else SheetTemplate.TABLE_ROW2_BOTTOM
        val (outerRight, outerBottom) = px(SheetTemplate.GRID_RIGHT, tableBottomFraction)
        val (midPx, rowSplitTop) = px(midX, SheetTemplate.TABLE_ROW1_BOTTOM)
        val rowSplitBottom = px(midX, SheetTemplate.TABLE_ROW2_BOTTOM).second

        canvas.drawRoundRect(
            RectF(outerLeft, outerTop, outerRight, outerBottom),
            TABLE_CORNER_RADIUS * scale, TABLE_CORNER_RADIUS * scale, borderPaint
        )
        // Horizontal divider between row 1 (Name/Date) and row 2 (Gr.&Section/Score).
        canvas.drawLine(outerLeft, rowSplitTop, outerRight, rowSplitTop, borderPaint)
        // Vertical divider, split into two segments (one per row) rather than
        // one line top-to-bottom, so it stops exactly at the horizontal
        // divider instead of visually crossing it.
        canvas.drawLine(midPx, outerTop, midPx, rowSplitTop, borderPaint)

        // v1.2.0: the third (ID) row, when this template has one. Merged
        // layout (<= ID_MERGED_MAX_DIGITS): the vertical divider runs on down
        // to the table's bottom edge, the row-2/3 divider covers only the
        // left cell, and Score becomes one tall cell. Full-width layout:
        // divider across the whole table, vertical divider stops at row 2.
        val idDigits = template.idDigits
        val merged = SheetTemplate.idRowIsMerged(idDigits)
        if (idDigits > 0 && merged) {
            canvas.drawLine(midPx, rowSplitTop, midPx, outerBottom, borderPaint)
            canvas.drawLine(outerLeft, rowSplitBottom, midPx, rowSplitBottom, borderPaint)
        } else {
            canvas.drawLine(midPx, rowSplitTop, midPx, rowSplitBottom, borderPaint)
            if (idDigits > 0) canvas.drawLine(outerLeft, rowSplitBottom, outerRight, rowSplitBottom, borderPaint)
        }
        if (idDigits > 0) drawIdRow(canvas, scale, px, isPdf, cellPaint, idDigits, merged)

        drawCellText(canvas, "Name:", SheetTemplate.GRID_LEFT, SheetTemplate.TABLE_TOP, midX, SheetTemplate.TABLE_ROW1_BOTTOM, cellPaint, scale, px, isPdf)
        drawCellText(canvas, "Date:", midX, SheetTemplate.TABLE_TOP, SheetTemplate.GRID_RIGHT, SheetTemplate.TABLE_ROW1_BOTTOM, cellPaint, scale, px, isPdf)
        drawCellText(canvas, "Section:", SheetTemplate.GRID_LEFT, SheetTemplate.TABLE_ROW1_BOTTOM, midX, SheetTemplate.TABLE_ROW2_BOTTOM, cellPaint, scale, px, isPdf)

        // v3.3: Subject Teacher line removed per explicit request. v1.2.0:
        // when the optional ID row is on and merged (see above), the Score
        // CELL is taller but the "Score:" label is deliberately still drawn
        // in the original row-2 band, exactly where it always was —
        // ResultAnnotator.drawScoreOnSheet writes the scanned score into that
        // same band (TABLE_ROW1_BOTTOM..TABLE_ROW2_BOTTOM), so the printed
        // label and the on-screen score never end up at different heights.
        // The extra height is blank writing space below the label.
        drawCellText(canvas, "Score:", midX, SheetTemplate.TABLE_ROW1_BOTTOM, SheetTemplate.GRID_RIGHT, SheetTemplate.TABLE_ROW2_BOTTOM, cellPaint, scale, px, isPdf)

        val (titleX, titleY) = px(0.5f, SheetTemplate.TITLE_Y)
        if (isPdf) {
            drawTitleSafeQ(canvas, template.testName, titleX, titleY, titlePaint)
        } else {
            canvas.drawText(template.testName, titleX, titleY, titlePaint)
        }

        if (template.hasSets) drawSetRow(canvas, template, region, scale, px, borderPaint.strokeWidth)
    }

    /** v1.2.5: the optional "Set:" bubble row. Bubble centres come from
     *  SheetTemplate.setBubbleCenter (the same function BubbleGrader.readSet
     *  reads); labels and the "Set:" caption are drawn relative to them. */
    private fun drawSetRow(
        canvas: Canvas, template: SheetTemplate, region: Region, scale: Float,
        px: (Float, Float) -> Pair<Float, Float>, lineWidth: Float
    ) {
        val radiusPx = (template.setBubbleRadiusFractionOfWidth() * region.width).coerceAtLeast(3f)
        val bubblePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = lineWidth * 1.4f
        }
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f * scale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }
        val captionPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f * scale
            typeface = SANS_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val labels = template.setLabels()
        var firstLabelX = 0f
        for (i in labels.indices) {
            val (fx, fy) = template.setBubbleCenter(i)
            val (cx, cy) = px(fx, fy)
            canvas.drawCircle(cx, cy, radiusPx, bubblePaint)
            val labelX = cx - radiusPx - 6f * scale
            if (i == 0) firstLabelX = labelX
            canvas.drawText(labels[i].toString(), labelX, cy + 3.5f * scale, labelPaint)
        }
        val (_, capY) = px(0f, SheetTemplate.SET_ROW_Y)
        canvas.drawText("Set:", firstLabelX - 7f * scale, capY + 4f * scale, captionPaint)
    }

    /**
     * v3.9.9: hand-tuned fix for specific letter pairs that render with
     * zero gap — visually overlapping — ONLY on the downloaded PDF, never
     * in the in-app preview (confirmed across multiple independent PDF
     * viewers, so it's baked into the PDF file itself, not a viewer
     * quirk). Three separate general-purpose attempts at this (disabling
     * font ligature features, removing double-bolding, drawing every
     * character as its own separate positioned drawText() call, and even
     * rendering the whole label as a bitmap image instead of PDF text)
     * were all tried across v3.9.6–3.9.8 and NONE of them fixed it or
     * changed the visual result at all — see git history for what was
     * ruled out, so a future attempt doesn't repeat the same dead ends.
     *
     * This is a narrower, hand-verified fix instead: specific letter-pair
     * boundaries in these exact 4 label strings are given a small fixed
     * extra gap, and ONLY those boundaries — the pairs that were confirmed
     * fine at normal spacing (e.g. "te" in Date, "ion" in Section, "ore"
     * in Score) are left completely alone. Keyed by the literal label
     * text; if a label's text ever changes, it silently falls back to
     * normal single-call drawText() with no extra spacing anywhere rather
     * than mis-apply gaps to the wrong letters.
     *
     * PDF-only per explicit instruction — the in-app preview never showed
     * this bug and must not be touched, so this whole path is gated behind
     * isPdf and never called from renderPreviewBitmap.
     */
    private val CELL_TEXT_EXTRA_GAP_INDICES: Map<String, Set<Int>> = mapOf(
        "Name:" to setOf(0, 1, 2, 3),           // N|a|m|e|:  — every boundary
        "Date:" to setOf(0, 1, 3),               // D|a|t e|:  — "te" left normal
        "Section:" to setOf(0, 1, 2),            // S|e|c|t ion:  — "ion:" left normal
        "Score:" to setOf(0, 1, 4),              // S|c|o re|:  — "ore" left normal
        // v1.2.0: "ID:" — both letter boundaries spaced, the safe treatment
        // "Name:" got. NOT yet checked against a real downloaded PDF the way
        // the other four were; verify I|D|: spacing on the next test print.
        "ID:" to setOf(0, 1)
    )

    /** Extra space inserted at a flagged boundary, in the same unscaled
     *  point units as textSize — scaled by [scale] like everything else in
     *  this table so it stays proportional across paper sizes. */
    private const val CELL_TEXT_EXTRA_GAP = 1.1f

    /**
     * v1.2.0: draws the optional third header row's contents — the "ID:"
     * label and [digits] empty boxes (one handwritten digit each). The
     * dividers around it are drawn by drawHeader. Box positions come from
     * SheetTemplate.idBoxRect, the same function IdReader uses to crop them
     * at scan time. [merged] = the Score cell is merged beside this row, so
     * the label's cell is just the left cell (see SheetTemplate.idRowIsMerged).
     */
    private fun drawIdRow(
        canvas: Canvas, scale: Float, px: (Float, Float) -> Pair<Float, Float>, isPdf: Boolean,
        cellPaint: Paint, digits: Int, merged: Boolean
    ) {
        val labelRight = if (merged) {
            SheetTemplate.GRID_LEFT + (SheetTemplate.GRID_RIGHT - SheetTemplate.GRID_LEFT) * SheetTemplate.TABLE_MID_X_FRACTION
        } else SheetTemplate.GRID_RIGHT
        drawCellText(
            canvas, "ID:", SheetTemplate.GRID_LEFT, SheetTemplate.TABLE_ROW2_BOTTOM,
            labelRight, SheetTemplate.TABLE_ROW3_BOTTOM, cellPaint, scale, px, isPdf
        )

        val boxPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.9f * scale
        }
        val zonePaint = Paint().apply {
            color = Color.rgb(SheetTemplate.ID_SAFE_GRAY, SheetTemplate.ID_SAFE_GRAY, SheetTemplate.ID_SAFE_GRAY)
            style = Paint.Style.FILL
        }
        for (i in 0 until digits) {
            val box = SheetTemplate.idBoxRect(i, digits)
            val (left, top) = px(box.left, box.top)
            val (right, bottom) = px(box.right, box.bottom)
            // grey "write inside here" zone first, so the box outline stays crisp on top
            val zone = SheetTemplate.idSafeZone(i, digits)
            val (zl, zt) = px(zone.left, zone.top)
            val (zr, zb) = px(zone.right, zone.bottom)
            canvas.drawRect(RectF(zl, zt, zr, zb), zonePaint)
            canvas.drawRect(RectF(left, top, right, bottom), boxPaint)
        }
    }

    /** Corner radius (in the same unscaled point units as the rest of the
     *  template) for the outer student-details table border. Small enough
     *  to stay a subtle rounding rather than a pill shape at the table's
     *  actual printed size. */
    private const val TABLE_CORNER_RADIUS = 6f

    /** Draws one table cell's label, vertically centered and left-aligned.
     *  No longer draws a per-cell border — see drawHeader, which now draws
     *  the whole table's border as a single rounded outer rect plus straight
     *  internal dividers. */
    private fun drawCellText(
        canvas: Canvas, text: String, fLeft: Float, fTop: Float, fRight: Float, fBottom: Float,
        textPaint: Paint, scale: Float,
        px: (Float, Float) -> Pair<Float, Float>, isPdf: Boolean
    ) {
        val (left, top) = px(fLeft, fTop)
        val (_, bottom) = px(fRight, fBottom)

        val textPaddingX = 6f * scale
        val textY = (top + bottom) / 2f + 4f * scale // nudge to visually center the text baseline
        val startX = left + textPaddingX

        val extraGapIndices = if (isPdf) CELL_TEXT_EXTRA_GAP_INDICES[text] else null
        if (extraGapIndices == null) {
            canvas.drawText(text, startX, textY, textPaint)
        } else {
            var cursorX = startX
            text.forEachIndexed { i, ch ->
                val s = ch.toString()
                canvas.drawText(s, cursorX, textY, textPaint)
                cursorX += textPaint.measureText(s)
                if (i in extraGapIndices) cursorX += CELL_TEXT_EXTRA_GAP * scale
            }
        }
    }

    /**
     * Draws the test title, PDF-only, with capital "Q" swapped for "O" plus
     * a manually-drawn tail — Montserrat's actual Q glyph has a 3-contour
     * shape (ring, counter/hole, and a tail that dips below the baseline as
     * its own separate closed contour) that PdfDocument's rasterizer can
     * render as a solid filled disc with no hole, confirmed by direct
     * visual inspection of a downloaded PDF. "O" (2 contours: ring +
     * counter) was checked directly and always renders correctly, and O
     * and Q share the exact same advance width in both font weights, so
     * drawing "O" and overlaying Q's tail at that same glyph origin is
     * geometrically exact, not an approximation.
     *
     * The title otherwise draws as one normal canvas.drawText() call (not
     * per-character) since no letter-spacing collapse has been confirmed
     * in title text specifically — only in the 4 fixed table labels, which
     * get their own hand-verified fix in drawCellText/
     * CELL_TEXT_EXTRA_GAP_INDICES above. If a future title turns out to
     * have the same collapse, extend that table rather than guessing here.
     */
    private fun drawTitleSafeQ(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (!text.contains('Q')) {
            canvas.drawText(text, x, y, paint)
            return
        }

        val safeText = text.replace('Q', 'O')
        canvas.drawText(safeText, x, y, paint)

        val totalWidth = paint.measureText(safeText)
        val startX = when (paint.textAlign) {
            Paint.Align.CENTER -> x - totalWidth / 2f
            Paint.Align.RIGHT -> x - totalWidth
            else -> x
        }
        var cursorX = startX
        for (ch in text) {
            val advanceChar = if (ch == 'Q') 'O' else ch
            if (ch == 'Q') {
                drawQTail(canvas, cursorX, y, paint)
            }
            cursorX += paint.measureText(advanceChar.toString())
        }
    }

    // Tail contour coordinates for capital Q, extracted directly from each
    // font weight's glyf table (font units, 1000 units per em, origin at
    // glyph baseline/left-side-bearing — same coordinate space Canvas text
    // metrics use once divided by 1000 and multiplied by textSize). One
    // polygon per weight because Montserrat's bold tail is visibly chunkier
    // than the regular tail, not just a uniformly scaled copy of it.
    private val Q_TAIL_REGULAR = listOf(
        649f to -139f, 617f to -139f, 557f to -123f, 496f to -88f, 432f to -32f,
        394f to 8f, 475f to 28f, 506f to -9f, 565f to -57f, 622f to -80f,
        650f to -80f, 728f to -80f, 783f to -16f, 819f to -59f, 753f to -139f
    )
    private val Q_TAIL_BOLD = listOf(
        651f to -161f, 610f to -161f, 537f to -143f, 466f to -103f, 389f to -35f,
        344f to 15f, 514f to 58f, 541f to 21f, 587f to -23f, 630f to -41f,
        654f to -41f, 718f to -41f, 768f to 11f, 840f to -75f, 769f to -161f
    )

    /** Draws just the tail stroke of a "Q" at the given baseline position,
     *  scaled to the Paint's textSize. Bold vs regular tail shape is chosen
     *  from typeface.isBold, since that's what actually differs between the
     *  two source fonts' tail proportions. */
    private fun drawQTail(canvas: Canvas, glyphOriginX: Float, baselineY: Float, paint: Paint) {
        val isBold = paint.typeface?.isBold ?: false
        val tail = if (isBold) Q_TAIL_BOLD else Q_TAIL_REGULAR
        val unitsToPx = paint.textSize / 1000f

        val path = Path()
        tail.forEachIndexed { i, (fx, fy) ->
            val px = glyphOriginX + fx * unitsToPx
            val py = baselineY - fy * unitsToPx
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        val tailPaint = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawPath(path, tailPaint)
    }

    /** Draws each corner marker as a hollow black "ring" (border square with a
     *  white center cutout) — see MarkerTemplateFactory for why this shape was
     *  chosen over a solid square. Proportions must match
     *  MarkerTemplateFactory.OUTER_MARGIN_FRACTION / RING_THICKNESS_FRACTION
     *  exactly, since that's what template matching is searching for. */
    private fun drawMarkers(
        canvas: Canvas, template: SheetTemplate, region: Region,
        px: (Float, Float) -> Pair<Float, Float>
    ) {
        val blackPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val sizePx = SheetTemplate.MARKER_SIZE * region.width
        val outerMargin = sizePx * MarkerTemplateFactory.OUTER_MARGIN_FRACTION
        val ringThickness = sizePx * MarkerTemplateFactory.RING_THICKNESS_FRACTION
        val innerMargin = outerMargin + ringThickness

        template.markerCenters().forEach { (fx, fy) ->
            val (cx, cy) = px(fx, fy)
            val half = sizePx / 2f
            canvas.drawRect(cx - half + outerMargin, cy - half + outerMargin, cx + half - outerMargin, cy + half - outerMargin, blackPaint)
            if (sizePx - innerMargin * 2 >= 1f) {
                canvas.drawRect(cx - half + innerMargin, cy - half + innerMargin, cx + half - innerMargin, cy + half - innerMargin, whitePaint)
            }
        }
    }

    private fun drawGrid(
        canvas: Canvas, template: SheetTemplate, region: Region, regionScale: Float,
        px: (Float, Float) -> Pair<Float, Float>, answerKey: List<Int>?,
        defects: List<MarkDefect>? = null,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO
    ) {
        // Bubbles and spacing scale with contentScale too (shrinking further
        // for a test with so many questions the ideal size wouldn't fit),
        // but text labels intentionally do NOT — question numbers and choice
        // letters need to stay a legible fixed size for a human to read
        // regardless of how dense the bubble grid gets. Only regionScale
        // (the tile's own physical page-space size — full/half/quarter)
        // applies to text, since that's a genuine physical size difference,
        // not a content-density squeeze.
        val scale = regionScale * template.contentScale
        val textScale = regionScale

        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f * textScale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f * textScale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bubblePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * scale
        }
        val filledBubblePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        // Bubble radius is derived from the tighter of the two actual on-page
        // measurements — row pitch (height-based) and per-choice column
        // spacing (width-based) — rather than page width alone. That's what
        // keeps bubbles from overlapping on a ½-page sheet, where row pitch
        // shrinks (rows packed into half the page height) but column spacing
        // doesn't change nearly as much.
        val rowHeightPx = template.rowHeightFraction * region.height
        val choiceSpacingPx = template.minChoiceSpacingFraction() * region.width
        val bubbleRadiusPx = (minOf(rowHeightPx, choiceSpacingPx) * bubbleToCellRatio)
            .coerceAtLeast(3f)

        // Choice-letter headers: once per block, just above that block's row-group.
        for (rowGroup in 0 until template.rowGroupCount) {
            val (groupTop, _) = template.rowGroupBounds(rowGroup)
            val (_, headerYAbs) = px(0f, groupTop)
            val headerY = headerYAbs - 7f * textScale
            for (blockInRowGroup in 0 until template.blocksInRowGroup(rowGroup)) {
                template.choiceLetters.forEachIndexed { i, letter ->
                    val hx = template.choiceHeaderCenterX(rowGroup, blockInRowGroup, i)
                    val (headerX, _) = px(hx, 0f)
                    canvas.drawText(letter.toString(), headerX, headerY, headerPaint)
                }
            }
        }

        for (q in 1..template.questionCount) {
            val (lx, ly) = template.questionLabelCenter(q)
            val (labelX, labelY) = px(lx, ly)
            canvas.drawText("$q.", labelX, labelY + 4f * textScale, labelPaint)

            val correctIndex = answerKey?.getOrNull(q - 1)
            val defect = defects?.getOrNull(q - 1) ?: MarkDefect.NONE

            for (choiceIndex in template.choiceLetters.indices) {
                val (bx, by) = template.bubbleCenter(q, choiceIndex)
                val (bubbleX, bubbleY) = px(bx, by)
                canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx, bubblePaint)
                if (correctIndex != choiceIndex) continue

                // Was 0.62f, which left a visible unshaded ring between
                // the fill's edge and the outline's inner edge (looked
                // like a hollow annulus rather than a filled-in bubble).
                // 0.95f reads as fully shaded while still leaving the
                // outline's own outer edge as a clean boundary, instead
                // of the fill and outline overlapping/double-thickening.
                when (defect) {
                    MarkDefect.NONE -> canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx * 0.95f, filledBubblePaint)
                    MarkDefect.BLANK -> {
                        // Deliberately nothing drawn — genuinely blank,
                        // not just a light mark.
                    }
                    MarkDefect.PARTIAL -> {
                        // Visibly reduced radius — a light/incomplete mark,
                        // not a full fill but not empty either.
                        canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx * 0.45f, filledBubblePaint)
                    }
                    MarkDefect.OVERFLOW -> {
                        // Larger than the printed outline itself — ink that
                        // visibly bled past the bubble's edge.
                        canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx * 1.35f, filledBubblePaint)
                    }
                    MarkDefect.MULTI -> {
                        canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx * 0.95f, filledBubblePaint)
                        // Also mark one other choice on this same question —
                        // deterministic (not re-randomized here) pick of
                        // "the next choice letter, wrapping around," so the
                        // second mark is always a genuinely different bubble
                        // regardless of which choice was correct.
                        val secondChoice = (choiceIndex + 1) % template.choiceLetters.size
                        if (secondChoice != choiceIndex) {
                            val (sx, sy) = template.bubbleCenter(q, secondChoice)
                            val (secondX, secondY) = px(sx, sy)
                            canvas.drawCircle(secondX, secondY, bubbleRadiusPx * 0.95f, filledBubblePaint)
                        }
                    }
                }
            }
        }
    }

    /**
     * Testing-tool-only sibling to generate() above — draws a DIFFERENT
     * answer key into each region of the page (one per physical
     * quarter/half tile) instead of repeating the same one. Used
     * exclusively by TestSheetBatchGenerator to pack multiple distinct
     * fill-patterns onto one physical prefilled sheet for
     * scanner-consistency testing. generate() itself, used by every real
     * test-creation path, is untouched — this is a new, additional
     * function, not a modification of it.
     * v3.6.2: applies the same paperSize scale/center transform as
     * generate() above.
     * v3.6.5: [defectsPerRegion], if provided, must be the same size as
     * [answerKeysPerRegion] — lets 4 distinct stress-test defect patterns
     * (previously 4 separate single-defect-pattern pages, via a now-removed
     * sibling function) pack onto ONE physical page's 4 quadrants instead,
     * same as the combo answer-key variants already did. Each quadrant
     * still only ever shows ONE pattern — this doesn't change per-quadrant
     * drawing, just lets defects vary by quadrant the same way answer keys
     * already could.
     */
    fun generateMultiVariant(
        template: SheetTemplate,
        outputFile: File,
        paperLayout: PaperLayout,
        answerKeysPerRegion: List<List<Int>>,
        paperSize: PaperSize = PaperSize.LETTER,
        defectsPerRegion: List<List<MarkDefect>>? = null,
        // v1.1.0: same as generate()'s own param — see its doc comment.
        showParentSignatureLine: Boolean = false
    ) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            paperSize.widthPt.toInt(),
            paperSize.heightPt.toInt(),
            1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // v3.6.7: per-region transform now, not one shared whole-page one —
        // see quadrantOffset()'s doc comment on generate() for why.
        val scale = minOf(
            paperSize.widthPt / SheetTemplate.PAGE_WIDTH,
            paperSize.heightPt / SheetTemplate.PAGE_HEIGHT
        )

        val regions = regionsForLayout(paperLayout, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT)
        require(answerKeysPerRegion.size == regions.size) {
            "generateMultiVariant expected ${regions.size} answer keys for $paperLayout, got ${answerKeysPerRegion.size}"
        }
        require(defectsPerRegion == null || defectsPerRegion.size == regions.size) {
            "generateMultiVariant expected ${regions.size} defect plans for $paperLayout, got ${defectsPerRegion?.size}"
        }
        regions.forEachIndexed { index, region ->
            val (offsetX, offsetY) = quadrantOffset(region, paperLayout, paperSize, scale)
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            drawSheetInRegion(
                canvas, template, region.inset(SheetTemplate.QUARTER_TILE_INSET),
                answerKeysPerRegion[index], defectsPerRegion?.get(index), isPdf = true,
                outerRegion = region, showParentSignatureLine = showParentSignatureLine
            )
            canvas.restore()
        }
        drawCutLines(canvas, paperLayout, paperSize)

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }
}
