package com.jpagdi.mcqquickcheck

import java.io.Serializable

enum class NumberingOrder { COLUMN_MAJOR, ROW_MAJOR }

/** v1.2.0: one ID digit box as fractions (0..1) of the sheet's width/height.
 *  Top-level (not nested inside SheetTemplate's companion) so callers can
 *  refer to it simply as IdBox. See SheetTemplate.idBoxRect. */
data class IdBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Defines the layout of ONE self-contained bubble sheet purely as fractions
 * (0f..1f) of that sheet's own region. Both SheetGenerator (drawing the
 * printable PDF) and the scanner (MarkerDetector / BubbleGrader) call the
 * functions below, so the printed sheet and the expected bubble positions can
 * never drift out of sync.
 *
 * A physical page can contain 4 independent copies of this template tiled
 * 2x2 (see SheetGenerator's `PaperLayout`), to save paper on short quizzes.
 * v3.4: HALF_CROSS (2-up landscape) and HALF_LENGTH (2-up portrait) removed
 * per explicit request — QUARTER (4-up) is now the only tiling, with its
 * question-count range extended from 10-60 up to 10-90 to cover what those
 * two used to handle. Each tiled copy gets its own 4 corner markers and is
 * meant to be cut apart before handing out — from the scanner's point of
 * view, each cut-out sheet is just "the page," so none of the geometry
 * below needs to know about tiling at all.
 *
 * No student-ID bubble grid — just a name line. Layout follows the ZipGrade
 * convention of stacking questions in blocks of [rowsPerBlock] (default 10),
 * with up to [maxBlocksPerRowGroup] blocks side by side before wrapping to a
 * new row of blocks underneath.
 *
 * IMPORTANT — sizing philosophy: every row-group is drawn at a fixed "ideal"
 * pitch (bigger, more legible bubbles) rather than stretched to fill whatever
 * space happens to be available. This means:
 *  - A block that isn't fully used (e.g. the last block only has questions
 *    21-25 out of a 10-row block) still uses the SAME row height as the full
 *    blocks above it — it just leaves the unused rows blank instead of
 *    spreading its few rows out to fill the block's box.
 *  - If the whole grid ends up shorter than the space available on the page,
 *    the grid is centered in that space rather than stretched to fill it.
 *  - Only when a test has so many questions that the ideal size wouldn't fit
 *    on one page does everything shrink uniformly (via [contentScale]) so it
 *    still fits on a single sheet.
 */
data class SheetTemplate(
    val testName: String,
    val questionCount: Int,
    val choiceCount: Int, // 4 (A-D) or 5 (A-E)
    val numbering: NumberingOrder,
    val rowsPerBlock: Int = 10,
    val maxBlocksPerRowGroup: Int = 3,
    // v3.3: target bubble RADIUS in mm, chosen on the Generate Sheet screen
    // (see GenerateSheetActivity's slider, BubbleSizePrefs.kt) and baked in
    // here permanently once a template is created — see idealRowHeightFraction
    // below and BubbleGrader/ResultAnnotator's bubbleRadiusPx formula for how
    // this drives BOTH the printed bubble size AND the scanner's detection
    // radius. Defaults to BubbleSizePrefs.DEFAULT_MM for templates built
    // before this existed.
    val bubbleSizeMm: Float = BubbleSizePrefs.DEFAULT_MM,
    // v1.2.0 (Classes feature): number of handwritten ID digit boxes in the
    // optional third header row of this test's printed sheet — 0 = no row
    // (every sheet made before this feature, and any sheet made while
    // Classes was off), otherwise ID_MIN_DIGITS..ID_MAX_DIGITS. Baked in at
    // creation like bubbleSizeMm: SheetGenerator draws the row from this
    // value and ScanActivity reads the boxes from it, never from the live
    // Settings stepper, so a printed sheet always scans the way it was made.
    val idDigits: Int = 0,
    // v1.2.5 (Test sets): number of answer-key sets this sheet carries, 1..4.
    // 1 = no set row (every sheet made before this feature). 2..4 = a row of
    // that many "Set:" bubbles is printed between the title and the column
    // letters; the student shades their set and the scanner grades with that
    // set's key. Baked in at creation like idDigits, so a printed sheet
    // always scans the way it was made.
    val setCount: Int = 1
) : Serializable {

    companion object {
        // A single sheet region is treated as if it were its own US-Letter-shaped
        // page for the purposes of fraction math; SheetGenerator scales this
        // down when tiling multiple sheets onto one physical page.
        const val PAGE_WIDTH = 612f
        const val PAGE_HEIGHT = 792f

        // v3.6.4: reserves room for printer hardware margin so all 4 edges
        // of each cut-apart QUARTER quadrant end up with an equal content
        // margin — including the edges that land on the page's own outer
        // boundary, not just the internal cut edges, which previously had
        // no margin reservation at all. Applied to the CONTENT box only
        // (see SheetGenerator.regionsForLayout/Region.inset) — the dashed
        // cut line itself stays exactly on the true w/2, h/2 boundary so
        // cutting the paper in half stays precise. Kept as a Float literal
        // (0.28f * 72f), not folded into a single Double/Int constant —
        // see known bug class #4 (Kotlin won't implicitly narrow a bare
        // decimal literal to Float when it reaches Canvas's Float-only
        // draw calls).
        const val QUARTER_TILE_INSET = 0.28f * 72f // ~0.28in in points

        const val MARKER_MARGIN_X = 0.03f
        // Bottom markers sit at this margin from the bottom edge, as before.
        // Top markers no longer use a fixed margin — see TITLE_Y below; they
        // flank the title line instead of sitting at the very top edge, since
        // the details table now occupies that space.
        const val MARKER_MARGIN_Y = 0.03f
        const val MARKER_SIZE = 0.028f

        // --- Details table (Name/Date row, Gr.&Section/Score row) ---------
        const val TABLE_TOP = 0.045f
        const val TABLE_ROW1_BOTTOM = 0.085f   // bottom of Name / Date cells
        const val TABLE_ROW2_BOTTOM = 0.125f   // bottom of the Section cell
        const val TABLE_SCORE_BOTTOM = 0.160f  // bottom of the (taller) Score cell
        const val TABLE_MID_X_FRACTION = 0.66f // left column (Name/Gr.&Section) is wider than the right

        // --- v1.2.0: optional ID row (third header row) -------------------
        // Only drawn/read when SheetTemplate.idDigits > 0. It sits BELOW
        // the existing two rows, so every existing header fraction above
        // (and everything that reads them: the saved-scan Name/Section
        // thumbnail crop, ResultAnnotator's score label) is unchanged, and
        // the bubble grid / corner markers do not move at all. TITLE_Y
        // (0.205) cannot move because the top corner markers are placed at
        // that height — see markerCenters() — so the row's bottom edge is
        // capped just above the title's cap-height (~0.189).
        //
        // Two layouts, chosen by digit count (idRowIsMerged):
        //  - up to ID_MERGED_MAX_DIGITS: the ID boxes live in the LEFT cell
        //    (under Section) and the Score cell is MERGED down beside them
        //    (rows 2+3), blank, tall enough to write "87 / 100".
        //  - more digits than that: the left cell is too narrow for
        //    comfortably writable boxes (~3.5 mm each at 12 digits on a
        //    quarter-page sheet), so the ID row spans the table's FULL width
        //    and Score keeps its normal single-row cell.
        // The ID is a teacher-configured number of digits (Settings stepper,
        // default 6, 4..12 — 12 fits a DepEd LRN). Box SIZE is capped, so a
        // short ID gets the same comfortably-writable boxes as a long one;
        // the boxes are left-aligned after the "ID:" label.
        const val TABLE_ROW3_BOTTOM = 0.172f
        const val ID_MIN_DIGITS = 4
        const val ID_MAX_DIGITS = 12
        const val ID_DEFAULT_DIGITS = 6
        const val ID_MERGED_MAX_DIGITS = 8
        // Share of the zone's (left cell or full table) width reserved for the "ID:" label.
        const val ID_LABEL_ZONE_FRACTION = 0.095f
        const val ID_RIGHT_PAD_FRACTION = 0.012f   // gap between last box and the zone's right edge
        const val ID_BOX_WIDTH_RATIO = 0.84f       // box width as a share of its pitch
        const val ID_MAX_PITCH = 0.082f            // widest spacing between boxes (fraction of sheet width)
        const val ID_MAX_BOX_WIDTH = 0.064f        // widest a single box gets (fraction of sheet width)
        const val ID_BOX_VERTICAL_PAD = 0.0045f    // top/bottom padding inside the row, fraction of height
        // v1.2.3: a light-grey "write inside here" zone printed inside every ID
        // box. Students keep digits off the box lines — a digit touching a line
        // was the reader's worst case. Light enough that ink stays far darker.
        const val ID_SAFE_INSET_X = 0.14f          // each side, as a share of the box width
        const val ID_SAFE_INSET_Y = 0.12f          // each side, as a share of the box height
        const val ID_SAFE_GRAY = 0xE4              // 0..255

        fun clampIdDigits(n: Int): Int = n.coerceIn(ID_MIN_DIGITS, ID_MAX_DIGITS)

        /** True when the Score cell is merged beside the ID row (see above). */
        fun idRowIsMerged(digits: Int): Boolean = digits in 1..ID_MERGED_MAX_DIGITS

        /** The grey writing zone inside box [index] (see ID_SAFE_INSET_*). */
        fun idSafeZone(index: Int, digits: Int): IdBox {
            val b = idBoxRect(index, digits)
            val dx = (b.right - b.left) * ID_SAFE_INSET_X
            val dy = (b.bottom - b.top) * ID_SAFE_INSET_Y
            return IdBox(b.left + dx, b.top + dy, b.right - dx, b.bottom - dy)
        }

        /** Box for digit [index] (0-based, left to right) of an ID that is
         *  [digits] long. Shared by SheetGenerator (drawing) and IdReader
         *  (cropping) so the printed boxes and the scanner's crops can never
         *  drift apart. */
        fun idBoxRect(index: Int, digits: Int): IdBox {
            val tableWidth = GRID_RIGHT - GRID_LEFT
            val zoneRightEdge = if (idRowIsMerged(digits)) GRID_LEFT + tableWidth * TABLE_MID_X_FRACTION else GRID_RIGHT
            val zoneWidth = zoneRightEdge - GRID_LEFT
            val zoneLeft = GRID_LEFT + zoneWidth * ID_LABEL_ZONE_FRACTION
            val zoneRight = zoneRightEdge - zoneWidth * ID_RIGHT_PAD_FRACTION
            val pitch = minOf((zoneRight - zoneLeft) / digits, ID_MAX_PITCH)
            val boxWidth = minOf(pitch * ID_BOX_WIDTH_RATIO, ID_MAX_BOX_WIDTH)
            val left = zoneLeft + pitch * index + (pitch - boxWidth) / 2f
            return IdBox(
                left = left,
                top = TABLE_ROW2_BOTTOM + ID_BOX_VERTICAL_PAD,
                right = left + boxWidth,
                bottom = TABLE_ROW3_BOTTOM - ID_BOX_VERTICAL_PAD
            )
        }

        // Title sits below the table, centered, with the top corner markers
        // flanking it at the same height.
        const val TITLE_Y = 0.205f

        // v1.2.5: the optional "Set:" bubble row sits just under the title
        // line (fraction of sheet height), above the choice letters.
        const val SET_ROW_Y = 0.226f
        const val SET_MIN = 1
        const val SET_MAX = 4
        // Bubble radius cap (points) and centre-to-centre pitch (in radii).
        const val SET_RADIUS_MAX_PT = 9f
        const val SET_PITCH_RADII = 5.6f
        // Keep the right-most bubble clear of the top-right corner marker.
        const val SET_RIGHT_INSET = 0.015f

        fun clampSetCount(n: Int): Int = n.coerceIn(SET_MIN, SET_MAX)

        /** Printed labels for [count] sets: X Y / X Y Z / W X Y Z. */
        fun setLabelsFor(count: Int): List<Char> = when (clampSetCount(count)) {
            2 -> listOf('X', 'Y')
            3 -> listOf('X', 'Y', 'Z')
            4 -> listOf('W', 'X', 'Y', 'Z')
            else -> emptyList()
        }

        const val GRID_TOP = 0.25f
        const val GRID_BOTTOM = 0.97f
        // v3.9.5: was 0.05/0.97 — a 5% left margin against a 3% right
        // margin. The bubble grid never showed this (blockBounds already
        // re-centers its own content within GRID_LEFT..GRID_RIGHT, so any
        // leftover space split evenly regardless of where the two edges
        // sit), but the student-details table spans GRID_LEFT..GRID_RIGHT
        // directly with no re-centering step of its own, so it inherited
        // the asymmetry and sat visibly off-center. Now symmetric at 4%
        // each side. Safe to change: MarkerDetector's corner markers don't
        // use these two constants (see TOP_MARGIN/marker position consts
        // below), and the scan-time readers of GRID_LEFT/GRID_RIGHT
        // (ScannedSheetStorage's thumbnail crop, ResultAnnotator's score-
        // label placement) both re-derive their crop/draw position from
        // whatever this constant is at scan time rather than from a value
        // baked in when the sheet was printed — so old and new sheets both
        // stay internally consistent, they just center slightly differently.
        const val GRID_LEFT = 0.04f
        const val GRID_RIGHT = 0.96f

        // Bigger, bolder bubbles than the original draft (was 0.009f). This is
        // now a starting point only — SheetGenerator derives the actual drawn
        // radius from the tighter of the real row pitch / column spacing for
        // whatever paper size is being rendered (see BUBBLE_TO_CELL_RATIO),
        // rather than always sizing off page width alone. Sizing bubbles off
        // width alone was the ½-page bug: a ½-page sheet's row pitch shrinks
        // (rows are packed into half the page height) but its width doesn't,
        // so width-only sizing kept bubbles full-size while rows got packed
        // tighter, and adjacent rows started touching.
        const val BUBBLE_RADIUS_FRACTION = 0.016f
        const val BUBBLE_TO_CELL_RATIO = 0.40f

        // 1pt = 1/72in = 0.3528mm — used to convert bubbleSizeMm (a physical
        // measurement) into the page-fraction math everything else here uses.
        const val MM_PER_POINT = 0.3528f

        // v3.3: no longer used directly — row pitch is now derived per-template
        // from bubbleSizeMm (see idealRowHeightFraction below) instead of this
        // fixed constant, so a small test's row pitch (and thus bubble size)
        // actually reflects the teacher's chosen bubble size rather than
        // always being forced to this one fixed value regardless of layout.
        // Left in place only as a sanity-check reference for what the old
        // fixed pitch used to be.
        const val IDEAL_ROW_HEIGHT_FRACTION = 0.050f

        // Vertical gap between stacked row-groups (e.g. 1-10/11-20 vs. 21-30).
        const val ROW_GROUP_GAP = 0.045f

        /** v3.6: the largest rowsPerBlock the current tier system ever
         *  produces — every maxBlocksPerRowGroupRange boundary (25 items/1
         *  column, 50/2, 75/3, 100/4) works out to exactly 25 rows in the
         *  fullest block at each tier. Used below to make the row pitch a
         *  genuine fixed constant: tuned so a 25-row block exactly fills
         *  the available grid height, then used as-is for every block on
         *  every sheet regardless of question count. Was 30 (for the old
         *  3-column, 90-question ceiling); the new 4-column, 100-question
         *  ceiling redistributes the same question load over one more
         *  column, so the fullest single column only ever needs 25 rows
         *  now. Dropping this constant means idealRowHeightFraction below
         *  divides the same available grid height by a SMALLER number of
         *  rows, so each row - and therefore each bubble - ends up
         *  slightly bigger than before. If a future tier change ever
         *  pushes rowsPerBlock above 25, update this alongside it - it is
         *  not derived automatically. */
        const val MAX_ROWS_PER_BLOCK = 25

        // v3.5: LABEL_WIDTH_FRACTION (a fraction of a block's own width) is
        // GONE — it made the "12." label wider on a 1-column sheet than on a
        // 3-column sheet purely because the block itself was wider, with no
        // relation to the actual printed bubble size. Same problem existed
        // for the gap between side-by-side blocks: blockBounds used to give
        // blocks ZERO gap at all, butting adjacent columns' bubbles directly
        // against each other. Both are now sized off the one thing that
        // should actually determine them — the real printed bubble diameter
        // (see bubbleDiameterFraction below) — so the number label and the
        // gap between columns each measure exactly one bubble-width, on
        // every layout, regardless of block count.

        /**
         * Width/height of ONE cut-out sheet for a given tiling (1, 2, or 4 copies
         * per physical page). A half-page sheet is wide/short (landscape); full
         * and quarter page sheets share the same portrait proportions. Used both
         * by the printable PDF (implicitly, via the region math in SheetGenerator)
         * and by the scanner (to frame the on-screen guide and validate detected
         * marker shapes against the right expected proportions).
         */
        /**
         * Width/height of ONE cut-out sheet. v3.4: HALF_CROSS/HALF_LENGTH
         * removed per explicit request (fast-scan toggle observed making no
         * measurable difference; QUARTER absorbed their former 60-90
         * question-count range instead — see max/minQuestionCountForLayout
         * and maxBlocksPerRowGroupRange below). QUARTER is now the only
         * PaperLayout value, kept as an enum (rather than deleted outright)
         * so the layout concept stays explicit in SheetTemplate/TestStorage
         * rather than silently assuming "the only shape" everywhere.
         */
        fun sheetAspectRatio(layout: PaperLayout): Float = when (layout) {
            PaperLayout.QUARTER -> (PAGE_WIDTH / 2f) / (PAGE_HEIGHT / 2f) // portrait tile
        }

        /** Fractional (x, y) position of each of the 4 corner markers, centers.
         *  Top markers flank the title line (see TITLE_Y); bottom markers sit at
         *  the usual small margin from the bottom edge. Shared by SheetGenerator
         *  (drawing) and ScanOverlayView (the on-screen alignment guide), so
         *  they can never drift out of sync with each other. */
        fun markerFractions(): List<Pair<Float, Float>> {
            val left = MARKER_MARGIN_X
            val right = 1f - MARKER_MARGIN_X
            val topY = TITLE_Y
            val bottomY = 1f - MARKER_MARGIN_Y
            return listOf(
                left to topY,       // top-left
                right to topY,      // top-right
                left to bottomY,    // bottom-left
                right to bottomY    // bottom-right
            )
        }

        /** Max/min items in a single column (rowsPerBlock), given how many
         *  columns the sheet is using. Replaces the old flat cap of 30, and
         *  the older N/columnCount-with-no-rounding cap:
         *
         *  - n <= 10: every item fits in a single column, so the only valid
         *    value is n itself (this is also enforced on columnCount at the
         *    call site — see GenerateSheetActivity's effectiveColumnsRange).
         *  - n > 10: the SUGGESTED MAXIMUM per column is
         *    ceil((n / columnCount) / 10) * 10 — rounded up to the nearest
         *    10, so the UI doesn't propose an oddly-specific ceiling like 34.
         *    The user can still edit DOWN from that ceiling to any smaller
         *    value that is itself a multiple of 5, as long as
         *    columnCount * value >= n (every item still fits) — this is
         *    what lets, e.g., 45 items over 3 columns land on an even
         *    15-15-15 split instead of being stuck at the lopsided 20-20-5
         *    the raw nearest-10 ceiling alone would otherwise suggest. The
         *    LOW end of the returned range is that smallest valid multiple
         *    of 5 — callers that want the "best fit" value (autofill) should
         *    prefer range.first over range.last for exactly this reason. */
        /** Hard cap on total question count for a paper layout — independent
         *  of columns/rows-per-column (rowsPerBlockRange above governs the
         *  per-column split once a question count is chosen; this governs
         *  the question count itself, before columns/rows even come into
         *  it). QUARTER tiles are physically smaller than a half-page tile,
         *  so it gets the lower cap. */
        /** Hard cap on total question count. v3.4: HALF_CROSS/HALF_LENGTH
         *  removed (see sheetAspectRatio above); QUARTER's range extended
         *  from its old 10-60 up to 10-90 to absorb what those two used to
         *  cover.
         *  v3.6: extended again, from 90 to 100, by adding a 4th column
         *  (see maxBlocksPerRowGroupRange below) - MAX_ROWS_PER_BLOCK
         *  dropped from 30 to 25 rows to make room. NOT based on new
         *  device-diagnostic data the way the old 60 ceiling and every
         *  other tier boundary in this file originally was - a
         *  quarter-page tile is physically smaller than the half-page
         *  tiles that used to own the 61-90 range, and the 91-100 range is
         *  smaller still (a 4th column on the same tile width). Legibility/
         *  scan-reliability at this range is unverified on a real device.
         *  Flagged here deliberately rather than silently treated as
         *  equally trustworthy as the tuned values elsewhere in this
         *  file. */
        fun maxQuestionCountForLayout(layout: PaperLayout): Int = when (layout) {
            PaperLayout.QUARTER -> 100
        }

        /** Minimum question count. v3.4: unchanged at 10 — see
         *  maxQuestionCountForLayout above for the max-end change. */
        fun minQuestionCountForLayout(layout: PaperLayout): Int = when (layout) {
            PaperLayout.QUARTER -> 10
        }

        fun rowsPerBlockRange(questionCount: Int, columnCount: Int): IntRange {
            val n = questionCount.coerceAtLeast(1)
            if (n <= 10) return n..n
            val safeColumns = columnCount.coerceAtLeast(1)
            val perColumnRaw = n.toDouble() / safeColumns
            val suggestedMax = (kotlin.math.ceil(perColumnRaw / 10.0) * 10).toInt()
            val smallestFittingMultipleOf5 = (kotlin.math.ceil(perColumnRaw / 5.0) * 5).toInt()
            val low = smallestFittingMultipleOf5.coerceAtMost(suggestedMax).coerceAtLeast(5)
            return low..suggestedMax.coerceAtLeast(low)
        }

        /** Fallback for any call site not yet passing columnCount — treats
         *  it as a single column, which is the least restrictive case
         *  (matches "let this many items exist across the whole sheet").
         *  Prefer the two-parameter overload above wherever the actual
         *  column count is known. */
        fun rowsPerBlockRange(questionCount: Int): IntRange = rowsPerBlockRange(questionCount, 1)

        /** Sensible number of columns side by side (maxBlocksPerRowGroup),
         *  given question count — each tier collapsing to a single valid
         *  column count (not a range the teacher picks within), consistent
         *  with the item-count-tier approach this file already used before
         *  v3.4's PaperLayout consolidation.
         *
         *  v3.4: HALF_LENGTH/HALF_CROSS removed; QUARTER's tiers replaced
         *  wholesale (not just extended):
         *    10-30 items → 1 column
         *    31-60 items → 2 columns
         *    61-90 items → 3 columns
         *
         *  v3.6: replaced wholesale again, adding a 4th column and
         *  tightening the per-column cap from 30 to 25 (see
         *  MAX_ROWS_PER_BLOCK):
         *    10-25 items → 1 column
         *    26-50 items → 2 columns
         *    51-75 items → 3 columns
         *    76-100 items → 4 columns
         *  Each boundary lands exactly on a multiple of 25 (max items per
         *  column), so every tier's fullest column is always exactly 25
         *  rows — this is what MAX_ROWS_PER_BLOCK assumes. Like the v3.4
         *  61-90 tier before it, this is a fresh product decision, not
         *  verified against real-device scan data the way the original
         *  10-60 tiers were.
         *
         *  Out-of-range questionCount values fall back to the nearest tier
         *  rather than crashing, since this can be called transiently while
         *  a field is mid-edit before the count itself gets clamped by the
         *  caller. */
        fun maxBlocksPerRowGroupRange(questionCount: Int, layout: PaperLayout): IntRange {
            val n = questionCount.coerceAtLeast(1)
            return when {
                n <= 25 -> 1..1
                n <= 50 -> 2..2
                n <= 75 -> 3..3
                else -> 4..4
            }
        }
    }

    val choiceLetters: List<Char>
        get() = ('A'..('A' + choiceCount - 1)).toList()

    val blockCount: Int
        get() = kotlin.math.ceil(questionCount.toDouble() / rowsPerBlock).toInt()

    val rowGroupCount: Int
        get() = kotlin.math.ceil(blockCount.toDouble() / maxBlocksPerRowGroup).toInt()

    /** How many blocks sit side by side in this row-group (0-based). */
    fun blocksInRowGroup(rowGroup: Int): Int {
        val remaining = blockCount - rowGroup * maxBlocksPerRowGroup
        return remaining.coerceIn(0, maxBlocksPerRowGroup)
    }

    /** How many question rows are actually used in this global block index. */
    fun rowsInBlock(globalBlockIndex: Int): Int {
        val used = questionCount - globalBlockIndex * rowsPerBlock
        return used.coerceIn(0, rowsPerBlock)
    }

    // ---- Fixed-pitch sizing / centering -----------------------------------

    private val availableHeight: Float
        get() = GRID_BOTTOM - GRID_TOP

    /** v3.5.1: was derived from bubbleSizeMm (rowPitchPt = bubbleRadiusPt /
     *  BUBBLE_TO_CELL_RATIO) — per explicit request, row spacing must be
     *  fixed identically on every sheet regardless of question count, not
     *  computed per-template. This is now a single constant: availableHeight
     *  split evenly across MAX_ROWS_PER_BLOCK rows, so the fullest possible
     *  block (25 rows as of v3.6, whether that's a 25q/1col, 50q/2col,
     *  75q/3col, or 100q/4col sheet) fills the grid's full available
     *  height exactly, with no leftover margin at top/bottom. A 10- or
     *  20-question block uses this exact same per-row pitch too — it just
     *  has fewer rows, so it naturally ends above the bottom of the grid
     *  instead of every sheet recomputing its own stretched pitch to
     *  always reach the bottom. */
    private val idealRowHeightFraction: Float
        get() = availableHeight / MAX_ROWS_PER_BLOCK

    /** Height a single row-group would take up at the ideal (unshrunk) pitch. */
    private val idealGroupHeight: Float
        get() = rowsPerBlock * idealRowHeightFraction

    private val idealTotalContentHeight: Float
        get() = rowGroupCount * idealGroupHeight +
            (rowGroupCount - 1).coerceAtLeast(0) * ROW_GROUP_GAP

    /** Bubble diameter fraction WITHOUT contentScale applied — used only to
     *  compute how much width the widest row-group would need at full
     *  (unshrunk) size, so contentScale (below) can check for horizontal
     *  overflow without a circular dependency on itself (contentScale ->
     *  bubbleDiameterFraction -> contentScale). Not used anywhere else;
     *  every other caller wants the real, already-scaled
     *  bubbleDiameterFraction. */
    private val idealBubbleDiameterFraction: Float
        get() = 2f * (bubbleSizeMm / MM_PER_POINT) / PAGE_WIDTH

    /** Width the widest row-group (the one with the most side-by-side
     *  blocks) would need at ideal, unshrunk bubble size — one block's
     *  content width (label + all choice bubbles at fixed pitch) times the
     *  block count, plus a one-bubble-diameter gap between each pair of
     *  blocks. Mirrors idealTotalContentHeight's role for the vertical
     *  axis: a ceiling contentScale checks against, not a value used for
     *  drawing directly. */
    private val idealMaxRowGroupWidth: Float
        get() {
            val widestBlockCount = (0 until rowGroupCount).maxOfOrNull { blocksInRowGroup(it) } ?: 1
            val idealChoicePitch = idealBubbleDiameterFraction * 2f
            val idealBlockContentWidth = idealBubbleDiameterFraction + choiceCount * idealChoicePitch
            val idealGap = if (widestBlockCount > 1) idealBubbleDiameterFraction else 0f
            return idealBlockContentWidth * widestBlockCount + idealGap * (widestBlockCount - 1)
        }

    /**
     * v3.6: now also guards against HORIZONTAL overflow, not just vertical
     * — a teacher-adjustable bubble size (see BubbleSizePrefs) near its
     * MAX_MM combined with the new 4-column tier can need slightly more
     * width than the grid has (verified: MAX_MM at 4 columns overflows by
     * about 5% of grid width before this fix), whereas every tier at every
     * bubble size up to MAX_MM was already safe on the vertical axis.
     * Previously 1f in every case the tier system produced on the vertical
     * axis alone (idealRowHeightFraction is tuned so MAX_ROWS_PER_BLOCK
     * rows exactly fill availableHeight, and no current tier ever exceeds
     * that many rows per block — see MAX_ROWS_PER_BLOCK). Still a
     * defensive fallback for the normal/default bubble size, not a normal
     * code path there — but now a REAL, reachable path at the large end of
     * the bubble-size slider, where everything (bubble radius, fonts, row
     * pitch, column width) shrinks together rather than letting columns
     * overlap.
     */
    val contentScale: Float
        get() {
            val heightScale = idealTotalContentHeight.let { total ->
                if (total <= availableHeight || total <= 0f) 1f else availableHeight / total
            }
            val widthScale = idealMaxRowGroupWidth.let { width ->
                val availableWidth = GRID_RIGHT - GRID_LEFT
                if (width <= availableWidth || width <= 0f) 1f else availableWidth / width
            }
            return minOf(heightScale, widthScale)
        }

    private val actualGroupHeight: Float
        get() = idealGroupHeight * contentScale

    private val actualGroupGap: Float
        get() = ROW_GROUP_GAP * contentScale

    /** The real printed bubble's diameter, as a fraction of the tile's own
     *  width (same "treat this tile as its own page" fraction space as
     *  everything else here). Scaled by contentScale for the same reason
     *  actualGroupHeight is — on a dense sheet where contentScale < 1,
     *  bubbles/rows shrink together, so the column gap and number label
     *  (both now derived from this) shrink right along with them instead of
     *  staying oversized relative to the smaller bubbles. */
    private val bubbleDiameterFraction: Float
        get() = (2f * (bubbleSizeMm / MM_PER_POINT) / PAGE_WIDTH) * contentScale

    /** True content width of one block: the number label plus every choice
     *  bubble at its fixed pitch (see choicePitchFraction further down) —
     *  exactly what bubbleCenter()/questionLabelCenter() actually draw
     *  into, with no stretch factor added on top. v3.6: blockBounds used
     *  to divide the FULL grid width evenly across blocksHere regardless
     *  of how much width a block's own bubbles actually needed — on a 1-
     *  or 2-column sheet this stretched each block far wider than its
     *  content, leaving a large dead gap between the last bubble of one
     *  column and the number label of the next ("too much space between
     *  columns", even though the bubble-to-bubble pitch inside a block was
     *  already fixed correctly as of v3.5.1). blockBounds now sizes each
     *  block to this real content width instead of stretching it. */
    private val blockContentWidthFraction: Float
        get() = bubbleDiameterFraction + choiceCount * choicePitchFraction

    /** Top of the grid content, after centering any leftover vertical space. */
    private val contentTopOffset: Float
        get() {
            val actualTotal = rowGroupCount * actualGroupHeight +
                (rowGroupCount - 1).coerceAtLeast(0) * actualGroupGap
            val leftover = (availableHeight - actualTotal).coerceAtLeast(0f)
            return GRID_TOP + leftover / 2f
        }

    data class Slot(val questionNumber: Int, val rowGroup: Int, val blockInRowGroup: Int, val rowInBlock: Int)

    // NOTE: this is a plain computed property (not `by lazy`), on purpose.
    // SheetTemplate now travels through Intent.putExtra(...) as Serializable
    // (for AnswerKeyActivity), and `by lazy` stores its cached value behind a
    // Lazy<T> delegate object that does NOT survive Android's real Java
    // serialization of Intent extras — that mismatch is what was crashing the
    // app the moment "Create Answer Key Here" was tapped. Recomputing this
    // list each time is effectively free at quiz/exam sizes.
    private val slots: List<Slot>
        get() = buildSlots()

    private fun buildSlots(): List<Slot> {
        val result = mutableListOf<Slot>()
        var qNum = 1
        if (numbering == NumberingOrder.COLUMN_MAJOR) {
            // Down each block's rows first, then the next block to the right,
            // then the next row-group down.
            outer@ for (rowGroup in 0 until rowGroupCount) {
                for (blockInRowGroup in 0 until blocksInRowGroup(rowGroup)) {
                    val globalBlock = rowGroup * maxBlocksPerRowGroup + blockInRowGroup
                    for (rowInBlock in 0 until rowsInBlock(globalBlock)) {
                        if (qNum > questionCount) break@outer
                        result.add(Slot(qNum, rowGroup, blockInRowGroup, rowInBlock))
                        qNum++
                    }
                }
            }
        } else {
            // Across each row of blocks first, then down to the next row.
            outer@ for (rowGroup in 0 until rowGroupCount) {
                val blocksHere = blocksInRowGroup(rowGroup)
                val maxRows = (0 until blocksHere).maxOfOrNull {
                    rowsInBlock(rowGroup * maxBlocksPerRowGroup + it)
                } ?: 0
                for (rowInBlock in 0 until maxRows) {
                    for (blockInRowGroup in 0 until blocksHere) {
                        val globalBlock = rowGroup * maxBlocksPerRowGroup + blockInRowGroup
                        if (rowInBlock >= rowsInBlock(globalBlock)) continue
                        if (qNum > questionCount) break@outer
                        result.add(Slot(qNum, rowGroup, blockInRowGroup, rowInBlock))
                        qNum++
                    }
                }
            }
        }
        return result
    }

    private fun slotFor(questionNumber: Int): Slot =
        slots.getOrNull(questionNumber - 1)
            ?: error("No layout slot for question $questionNumber")

    /** Fractional (x, y) position of each of the 4 corner markers, centers.
     *  See the companion object's markerFractions() — this instance method
     *  just delegates to it (kept for call-site convenience). */
    fun markerCenters(): List<Pair<Float, Float>> = markerFractions()

    /** (top, bottom) fraction bounds of a given row-group, at its fixed pitch. */
    fun rowGroupBounds(rowGroup: Int): Pair<Float, Float> {
        val top = contentTopOffset + rowGroup * (actualGroupHeight + actualGroupGap)
        return top to (top + actualGroupHeight)
    }

    /** (left, width) fraction bounds of a given block within its row-group.
     *  v3.5: blocks used to butt flush against each other (no gap at all)
     *  when more than one sat side by side — fixed by inserting a one
     *  bubble-diameter gap between them.
     *  v3.6: blocks were still being STRETCHED to fill the grid's full
     *  width even after that gap fix — blockWidth was
     *  `(gridWidth - totalGap) / blocksHere`, a share of the available
     *  width, not the block's actual content width. That's what left a
     *  large dead gap between the last bubble of one column and the number
     *  label of the next on any sheet using fewer than the max column
     *  count. Blocks are now sized to blockContentWidthFraction (their
     *  REAL content width — see that property below) with a fixed
     *  one-bubble-diameter gap between them, and the whole row of blocks
     *  is centered horizontally in the grid — the same "don't stretch,
     *  center the leftover space" treatment contentTopOffset already
     *  gives the grid vertically. */
    fun blockBounds(rowGroup: Int, blockInRowGroup: Int): Pair<Float, Float> {
        val blocksHere = blocksInRowGroup(rowGroup)
        val gap = if (blocksHere > 1) bubbleDiameterFraction else 0f
        val blockWidth = blockContentWidthFraction
        val rowGroupTotalWidth = blockWidth * blocksHere + gap * (blocksHere - 1)
        val leftover = ((GRID_RIGHT - GRID_LEFT) - rowGroupTotalWidth).coerceAtLeast(0f)
        val rowGroupLeft = GRID_LEFT + leftover / 2f
        val left = rowGroupLeft + blockInRowGroup * (blockWidth + gap)
        return left to blockWidth
    }

    /** Uniform row height within a block: always rowsPerBlock slots tall, even
     *  if this particular block only uses some of them. This is what keeps
     *  item 21-25's row spacing identical to item 1-10's and 11-20's. */
    private fun rowHeightFor(rowGroup: Int): Float = actualGroupHeight / rowsPerBlock

    /** Row height (fraction of page height), same for every row-group by design. */
    val rowHeightFraction: Float
        get() = actualGroupHeight / rowsPerBlock

    /** Fixed center-to-center distance between adjacent choice bubbles (one
     *  bubble's own diameter + a gap of exactly one more bubble-diameter),
     *  same on every layout regardless of block width. v3.5.1: this REPLACES
     *  the old `(blockWidth - labelWidth) / choiceCount`, which stretched
     *  bubbles apart to fill however wide the block happened to be — fine
     *  on a narrow 3-column block, but on a wide 1-column block (e.g. 30
     *  questions) it spread A/B/C/D far enough apart that the gap between
     *  bubbles was several bubble-widths, not the one bubble-width this was
     *  supposed to measure. Same source both SheetGenerator (drawing) and
     *  BubbleGrader/ResultAnnotator (scanning) read from — see
     *  minChoiceSpacingFraction below — so scan detection stays in lockstep
     *  with what's actually printed. */
    private val choicePitchFraction: Float
        get() = bubbleDiameterFraction * 2f

    /**
     * Per-choice horizontal spacing (fraction of page width) — now a fixed
     * value (see choicePitchFraction) rather than the tightest of several
     * row-groups' stretched spacings, since it no longer varies by block
     * width at all. Name/signature kept as-is: BubbleGrader, ResultAnnotator,
     * and SheetGenerator all call this to stay in sync with SheetTemplate as
     * the single source of truth.
     */
    fun minChoiceSpacingFraction(): Float = choicePitchFraction

    /**
     * Fractional center position (x, y) of the bubble for [questionNumber] (1-based)
     * and [choiceIndex] (0-based, 0=A).
     */
    fun bubbleCenter(questionNumber: Int, choiceIndex: Int): Pair<Float, Float> {
        val slot = slotFor(questionNumber)
        val (groupTop, _) = rowGroupBounds(slot.rowGroup)
        val (blockLeft, _) = blockBounds(slot.rowGroup, slot.blockInRowGroup)

        val rowHeight = rowHeightFor(slot.rowGroup)
        val y = groupTop + slot.rowInBlock * rowHeight + rowHeight / 2f

        val labelWidth = bubbleDiameterFraction
        val choiceSpacing = choicePitchFraction
        val x = blockLeft + labelWidth + choiceIndex * choiceSpacing + choiceSpacing / 2f

        return x to y
    }

    /** Fractional center for where the question-number label is drawn. */
    fun questionLabelCenter(questionNumber: Int): Pair<Float, Float> {
        val slot = slotFor(questionNumber)
        val (groupTop, _) = rowGroupBounds(slot.rowGroup)
        val (blockLeft, _) = blockBounds(slot.rowGroup, slot.blockInRowGroup)

        val rowHeight = rowHeightFor(slot.rowGroup)
        val y = groupTop + slot.rowInBlock * rowHeight + rowHeight / 2f

        val labelWidth = bubbleDiameterFraction
        val x = blockLeft + labelWidth / 2f

        return x to y
    }

    /** Fraction x-position of the choice-letter header above a given block/choice. */
    fun choiceHeaderCenterX(rowGroup: Int, blockInRowGroup: Int, choiceIndex: Int): Float {
        val (blockLeft, _) = blockBounds(rowGroup, blockInRowGroup)
        val labelWidth = bubbleDiameterFraction
        val choiceSpacing = choicePitchFraction
        return blockLeft + labelWidth + choiceIndex * choiceSpacing + choiceSpacing / 2f
    }

    // ---- v1.2.5: set-bubble row -------------------------------------------

    val hasSets: Boolean get() = setCount > 1

    fun setLabels(): List<Char> = setLabelsFor(setCount)

    /** Set-bubble radius in points: the sheet's own bubble size (so the row
     *  matches the answer bubbles) but never bigger than SET_RADIUS_MAX_PT. */
    fun setBubbleRadiusPt(): Float =
        ((bubbleSizeMm / MM_PER_POINT) * contentScale).coerceAtMost(SET_RADIUS_MAX_PT)

    /** Radius as a fraction of the sheet's WIDTH (multiply by the drawn or
     *  warped image width to get pixels — generator and scanner both do). */
    fun setBubbleRadiusFractionOfWidth(): Float = setBubbleRadiusPt() / PAGE_WIDTH

    /** Fractional centre of set bubble [index] (0-based), right-aligned to the
     *  grid's right edge. Same function SheetGenerator draws from and
     *  BubbleGrader.readSet reads from. */
    fun setBubbleCenter(index: Int): Pair<Float, Float> {
        val r = setBubbleRadiusFractionOfWidth()
        val pitch = SET_PITCH_RADII * r
        val lastX = GRID_RIGHT - SET_RIGHT_INSET - r
        val x = lastX - (setCount - 1 - index) * pitch
        return x to SET_ROW_Y
    }
}
