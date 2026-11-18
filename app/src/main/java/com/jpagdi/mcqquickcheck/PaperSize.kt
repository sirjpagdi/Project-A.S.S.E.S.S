package com.jpagdi.mcqquickcheck

/**
 * Physical paper sizes offered for downloading/printing an answer sheet PDF
 * (see Settings > Download paper size). Widths/heights are in PDF points
 * (1/72 inch), matching how SheetTemplate.PAGE_WIDTH/PAGE_HEIGHT are
 * already expressed.
 *
 * IMPORTANT — this is a printing-only concern:
 * SheetTemplate's layout fractions, MarkerDetector's corner detection, and
 * BubbleGrader's bubble-position math are all defined against the fixed
 * PAGE_WIDTH x PAGE_HEIGHT (Letter) reference space and do NOT know or care
 * what physical paper a sheet ends up printed on. SheetGenerator.generate()
 * still lays everything out in that same reference space, then uniformly
 * scales + centers the whole rendered page onto whichever PaperSize was
 * chosen here (see the scale/offsetX/offsetY math in generate()). A uniform
 * scale keeps the square corner guides square — only stretching would
 * distort them — and MarkerDetector's multi-scale template matching already
 * tolerates the kind of size variation that produces (roughly ±3% for A4,
 * none for Folio since it shares Letter's width). Adding a new PaperSize
 * here never requires touching SheetTemplate, MarkerDetector, or
 * BubbleGrader.
 */
enum class PaperSize(val label: String, val widthPt: Float, val heightPt: Float) {
    LETTER("Letter (8.5\" × 11\")", 612f, 792f),
    A4("A4 (210 × 297 mm)", 595f, 842f),
    // "Folio" as commonly sold/printed in the Philippines: 8.5" × 13"
    // (216 × 330 mm) — same width as Letter, taller. Not to be confused
    // with US Legal (8.5" × 14"), which some other regions also call Folio.
    FOLIO("Folio (8.5\" × 13\")", 612f, 936f);

    companion object {
        fun fromNameOrDefault(name: String?): PaperSize =
            entries.firstOrNull { it.name == name } ?: LETTER
    }
}
