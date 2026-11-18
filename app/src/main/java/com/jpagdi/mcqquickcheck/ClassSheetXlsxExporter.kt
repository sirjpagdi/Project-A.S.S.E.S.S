package com.jpagdi.mcqquickcheck

import java.util.zip.ZipOutputStream

/**
 * v1.2.0 — builds the per-class, per-exam score spreadsheet (.xlsx) that
 * ClassSheetActivity exports. Reuses ItemAnalysisXlsxExporter's package parts
 * (content types, rels, styles — now `internal` there) so both exports share
 * one proven XLSX structure; only the sheet content and name differ.
 *
 * Layout: row 1 title, row 2 summary, row 3 blank, row 4 header
 * (No., ID, Name, Gender, Score, Total, Percent), then one row per enrolled learner.
 * ID is written as TEXT (inline string) so a leading zero survives and Excel
 * never shows it in scientific notation. Learners with no score yet get blank
 * Score/Total/Percent cells. Rows are written in the order given (the class
 * sheet's current sort).
 */
object ClassSheetXlsxExporter {

    class Row(val learnerId: String, val name: String, val gender: String, val earned: Int?, val total: Int?)

    fun build(className: String, testName: String, rows: List<Row>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ItemAnalysisXlsxExporter.writeEntry(zip, "[Content_Types].xml", ItemAnalysisXlsxExporter.CONTENT_TYPES_XML)
            ItemAnalysisXlsxExporter.writeEntry(zip, "_rels/.rels", ItemAnalysisXlsxExporter.RELS_XML)
            ItemAnalysisXlsxExporter.writeEntry(zip, "xl/workbook.xml", workbookXml())
            ItemAnalysisXlsxExporter.writeEntry(zip, "xl/_rels/workbook.xml.rels", ItemAnalysisXlsxExporter.WORKBOOK_RELS_XML)
            ItemAnalysisXlsxExporter.writeEntry(zip, "xl/styles.xml", ItemAnalysisXlsxExporter.STYLES_XML)
            ItemAnalysisXlsxExporter.writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(className, testName, rows))
        }
        return out.toByteArray()
    }

    private fun workbookXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="Scores" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private fun text(ref: String, value: String, style: Int? = null): String {
        val s = style?.let { """ s="$it"""" } ?: ""
        return """<c r="$ref"$s t="inlineStr"><is><t>${ItemAnalysisXlsxExporter.escapeXml(value)}</t></is></c>"""
    }

    private fun number(ref: String, value: Double): String = """<c r="$ref" t="n"><v>$value</v></c>"""

    private fun sheetXml(className: String, testName: String, rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="6" customWidth="1"/><col min="2" max="2" width="16" customWidth="1"/><col min="3" max="3" width="34" customWidth="1"/><col min="4" max="7" width="12" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        val scored = rows.filter { it.earned != null && it.total != null && it.total > 0 }
        val summary = if (scored.isEmpty()) {
            "${rows.size} learners, none scanned yet"
        } else {
            val avg = scored.map { it.earned!! * 100.0 / it.total!! }.average()
            "${scored.size} of ${rows.size} learners scanned, average ${"%.1f".format(avg)}%"
        }
        sb.append("""<row r="1">${text("A1", "$className — $testName")}</row>""")
        sb.append("""<row r="2">${text("A2", summary)}</row>""")

        val headers = listOf("No.", "ID", "Name", "Gender", "Score", "Total", "Percent")
        sb.append("""<row r="4">""")
        headers.forEachIndexed { i, h -> sb.append(text("${ItemAnalysisXlsxExporter.columnLetter(i)}4", h, 1)) }
        sb.append("</row>")

        rows.forEachIndexed { index, r ->
            val n = 5 + index
            sb.append("""<row r="$n">""")
            sb.append(number("A$n", (index + 1).toDouble()))
            sb.append(text("B$n", r.learnerId))
            sb.append(text("C$n", r.name))
            sb.append(text("D$n", when (r.gender) { "M" -> "Male"; "F" -> "Female"; else -> "" }))
            if (r.earned != null && r.total != null) {
                sb.append(number("E$n", r.earned.toDouble()))
                sb.append(number("F$n", r.total.toDouble()))
                if (r.total > 0) sb.append(number("G$n", Math.round(r.earned * 1000.0 / r.total) / 10.0))
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }
}
