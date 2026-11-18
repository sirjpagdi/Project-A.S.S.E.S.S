package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Draws color-coded feedback directly onto the (already warped/un-skewed)
 * scanned sheet image, ZipGrade-style, instead of a separate text list of
 * per-question remarks:
 *
 * - The CORRECT bubble for every question gets a green ring, whether or not
 *   the student picked it — so a glance at the sheet shows what the right
 *   answer was everywhere.
 * - If the student's marked bubble is WRONG, that bubble additionally gets
 *   a red ring.
 * - If a question is ambiguous (multiple marks, unclear which was intended),
 *   the marked bubble gets an orange ring instead of red/green, flagging it
 *   as needing a human look rather than asserting an answer.
 * - A blank (nothing marked) just shows the green ring on the correct
 *   answer, with nothing drawn at any other bubble.
 */
object ResultAnnotator {

    private val GREEN = Color.parseColor("#2E7D32")
    private val RED = Color.parseColor("#C62828")
    private val ORANGE = Color.parseColor("#E68A00")

    fun annotate(
        warped: Bitmap,
        template: SheetTemplate,
        scanResults: List<BubbleGrader.QuestionResult>,
        answerKey: List<Int>,
        earnedPoints: Int? = null,
        totalPoints: Int? = null,
        bubbleToCellRatio: Float = SheetTemplate.BUBBLE_TO_CELL_RATIO
    ): Bitmap {
        val output = warped.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        if (earnedPoints != null && totalPoints != null) {
            drawScoreOnSheet(canvas, template, earnedPoints, totalPoints)
        }

        val rowHeightPx = template.rowHeightFraction * output.height
        val choiceSpacingPx = template.minChoiceSpacingFraction() * output.width
        val bubbleRadiusPx = (minOf(rowHeightPx, choiceSpacingPx) * bubbleToCellRatio)
            .coerceAtLeast(3f)
        val ringRadius = bubbleRadiusPx * 1.35f
        val ringStroke = (bubbleRadiusPx * 0.28f).coerceAtLeast(3f)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ORANGE
            strokeCap = Paint.Cap.ROUND
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        scanResults.forEach { result ->
            val correctIndex = answerKey.getOrNull(result.questionNumber - 1)
            val chosenIndex = result.chosenIndex
            // An ambiguous mark never counts as confidently correct, even if
            // it happens to land on the right bubble — matches the stricter
            // scoring rule in AnswerParser.grade().
            val isCleanlyCorrect = chosenIndex != null && chosenIndex == correctIndex && !result.isAmbiguous

            if (result.isAmbiguous) {
                // Ambiguous: strike through the WHOLE row (choice A through
                // the last choice) in orange, rather than ringing just the
                // one bubble the grader happened to pick as "highest" — the
                // point is "don't trust which bubble this is," so marking
                // only one choice implied more confidence than we have.
                drawRowStrikethrough(canvas, template, result.questionNumber, strikePaint, bubbleRadiusPx)
            } else if (chosenIndex != null && chosenIndex != correctIndex) {
                drawRing(canvas, template, result.questionNumber, chosenIndex, ringRadius, ringStroke, RED, ringPaint)
            }

            // Correct answer's position always gets a green ring, so the
            // right answer is visible at a glance even when the student
            // missed it — drawn after the strikethrough so the green ring
            // for the correct choice isn't obscured by the orange line.
            if (correctIndex != null) {
                drawRing(canvas, template, result.questionNumber, correctIndex, ringRadius, ringStroke, GREEN, ringPaint)
            }

            // Green checkmark right next to the question number, drawn AT
            // the label's own vertical center (previously offset upward by
            // over a full ring radius, which visually put it on the row
            // above — that offset is gone; only a small baseline correction
            // remains, since drawText anchors to the text baseline, not the
            // glyph's visual center).
            if (isCleanlyCorrect) {
                val (lx, ly) = template.questionLabelCenter(result.questionNumber)
                val labelX = lx * canvas.width
                val labelY = ly * canvas.height
                labelPaint.textSize = ringRadius * 1.3f
                val baselineCorrection = (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText("✓", labelX, labelY - baselineCorrection, labelPaint)
            }
        }

        return output
    }

    /** Prints the score and percentage directly onto the scanned sheet
     *  image, in the Score table cell, right after the "Score:" label —
     *  not just as separate on-screen text above/below the image. Uses the
     *  exact same fraction constants SheetGenerator used to draw that cell
     *  in the first place (TABLE_MID_X_FRACTION..GRID_RIGHT horizontally;
     *  TABLE_ROW1_BOTTOM..TABLE_ROW2_BOTTOM vertically) — so this can never
     *  draw outside the cell's real printed boundary, on any paper layout. */
    private fun drawScoreOnSheet(canvas: Canvas, template: SheetTemplate, earnedPoints: Int, totalPoints: Int) {
        val percent = if (totalPoints > 0) (earnedPoints * 1000 / totalPoints) / 10.0 else 0.0
        val scoreText = "$earnedPoints / $totalPoints = $percent%"

        val cellLeftPx = SheetTemplate.TABLE_MID_X_FRACTION * canvas.width
        val cellRightPx = SheetTemplate.GRID_RIGHT * canvas.width
        val cellTopPx = SheetTemplate.TABLE_ROW1_BOTTOM * canvas.height
        val cellBottomPx = SheetTemplate.TABLE_ROW2_BOTTOM * canvas.height
        val cellHeightPx = cellBottomPx - cellTopPx

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            // v3.3: this used to separately re-derive a reference cell height
            // here because the real cell height varied depending on
            // template.showSubjectTeacherLine. That toggle is gone — the
            // Score cell only has one possible height now (cellHeightPx,
            // already computed above), so this just uses it directly.
            textSize = cellHeightPx * 0.32f
            textAlign = Paint.Align.LEFT
        }
        // v2.1: re-derived from scratch, per the report that v2.0's "4x"
        // change looked the same on-device. Traced why: cellHeightPx here
        // was already being read correctly (canvas.height IS the real
        // bitmap height, not a stale constant) — the actual problem was
        // that v2.0's math computed textSize = (cellHeightPx * 0.30) * 4,
        // then capped it at cellHeightPx * 0.85. Those two numbers work
        // out to ~71px "desired" vs. a ~50px cap — so the CAP, not the 4x,
        // was what actually applied, and 50px against this app's real
        // cellHeightPx (~59px on a typical scan) is a fairly modest
        // absolute size once photographed and viewed on a phone screen.
        // Rather than another multiplier-of-a-multiplier, this now targets
        // the cell height directly: as large as the cell can hold without
        // touching the row above/below, full stop.
        val scoreTextSize = (cellHeightPx * 0.95f).coerceAtLeast(1f)
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN
            textSize = scoreTextSize
            typeface = Typeface.DEFAULT_BOLD
            // v2.4: right-aligned to the cell's right edge instead of
            // left-anchored right after the label. On HALF_LENGTH sheets
            // in particular, a left-anchored long score string could run
            // past the visible photographed area entirely — right-aligning
            // guarantees it always sits inside the cell's real right
            // boundary (GRID_RIGHT), regardless of how long the string is,
            // as long as it still fits after the shrink-to-fit check below.
            textAlign = Paint.Align.RIGHT
        }

        val labelText = "Score:"
        val labelWidth = labelPaint.measureText(labelText)
        val paddingX = cellHeightPx * 0.15f

        // The printed "Score:" label is already visible on the underlying
        // photo (the annotated bitmap is a full copy of the warped scan) —
        // labelWidth is used ONLY to budget available space so the value
        // doesn't grow back over the label; it no longer determines WHERE
        // the value is drawn (that's now always the cell's right edge, via
        // textAlign = RIGHT above), so the earlier fix to labelPaint's
        // measurement reference matters even less than it used to — the
        // toggle state can no longer shift the score's horizontal position
        // at all, only how aggressively it has to shrink to fit.
        var valuePaint = scorePaint
        val valueWidth = valuePaint.measureText(scoreText)
        val availableWidth = (cellRightPx - cellLeftPx) - labelWidth - paddingX * 2
        if (valueWidth > availableWidth && availableWidth > 0) {
            val shrinkFactor = (availableWidth / valueWidth).coerceAtLeast(0.35f)
            valuePaint = Paint(scorePaint).apply {
                color = scorePaint.color
                typeface = scorePaint.typeface
                textAlign = scorePaint.textAlign
                textSize = scorePaint.textSize * shrinkFactor
            }
        }

        // Proper baseline centering via font metrics, rather than the old
        // fixed offset that was hand-tuned for the old (much smaller) text
        // size and would sit noticeably off-center at 4x.
        val fm = valuePaint.fontMetrics
        val textY = (cellTopPx + cellBottomPx) / 2f - (fm.ascent + fm.descent) / 2f

        canvas.drawText(scoreText, cellRightPx - paddingX, textY, valuePaint)
    }

    private fun drawRing(
        canvas: Canvas, template: SheetTemplate, questionNumber: Int, choiceIndex: Int,
        radius: Float, strokeWidth: Float, color: Int, paint: Paint
    ) {
        val (fx, fy) = template.bubbleCenter(questionNumber, choiceIndex)
        val cx = fx * canvas.width
        val cy = fy * canvas.height
        paint.color = color
        paint.strokeWidth = strokeWidth
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** Draws a single horizontal line through the centers of every choice
     *  bubble (A through the last choice) for one question, spanning from
     *  just before the first bubble to just after the last. */
    private fun drawRowStrikethrough(
        canvas: Canvas, template: SheetTemplate, questionNumber: Int,
        paint: Paint, bubbleRadiusPx: Float
    ) {
        val lastChoiceIndex = template.choiceLetters.size - 1
        val (firstX, y) = template.bubbleCenter(questionNumber, 0)
        val (lastX, _) = template.bubbleCenter(questionNumber, lastChoiceIndex)
        val startX = firstX * canvas.width - bubbleRadiusPx * 1.35f
        val endX = lastX * canvas.width + bubbleRadiusPx * 1.35f
        val cy = y * canvas.height
        paint.strokeWidth = (bubbleRadiusPx * 0.22f).coerceAtLeast(3f)
        canvas.drawLine(startX, cy, endX, cy, paint)
    }
}
