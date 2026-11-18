package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-writes a real .xlsx (raw OOXML inside a zip) for Item Analysis —
 * no spreadsheet library, consistent with how this app already hand-draws
 * PDFs (SheetGenerator, via android.graphics.pdf.PdfDocument) and
 * hand-writes zips (TestSheetBatchGenerator) instead of pulling in a new
 * dependency for a one-shape report.
 *
 * The exact XML structure here was validated BEFORE being ported to
 * Kotlin: built and round-tripped an equivalent minimal file through
 * openpyxl (title, values, bold header font, header fill color all
 * confirmed correct) so this isn't a first-try guess at the OOXML format.
 *
 * "Download" means the public Downloads folder, not just a share-sheet
 * handoff. On Android 10+ (API 29+) that's MediaStore.Downloads, which
 * needs zero extra permissions. Below API 29, writing to the public
 * Downloads folder needs WRITE_EXTERNAL_STORAGE plus a runtime-permission
 * flow — this app has exactly one permission total (Camera) today, so
 * rather than add a new dangerous permission for a shrinking slice of old
 * devices, pre-29 falls back to the existing share-sheet pattern instead
 * (same as Generate & Share PDF), with a Toast explaining why rather than
 * silently behaving differently.
 */
object ItemAnalysisXlsxExporter {

    fun exportAndShare(activity: Activity, testName: String, folder: String?, result: ItemAnalysis.Result) {
        val bytes = buildXlsx(testName, folder, result)
        val fileName = "ItemAnalysis_${testName}${folder?.let { "_$it" } ?: ""}.xlsx"
            .replace(Regex("[^A-Za-z0-9_.-]"), "_") // sanitize — folder/test names are free text

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsViaMediaStore(activity, fileName, bytes, XLSX_MIME_TYPE)
        } else {
            shareViaFileProvider(activity, fileName, bytes, XLSX_MIME_TYPE, "Share item analysis")
        }
    }

    // No longer XLSX-specific or private (v2.8) — GenerateSheetActivity's
    // new PDF "Download" option reuses this exact MediaStore/fallback
    // pattern instead of re-implementing it, per the explicit "reuse,
    // don't duplicate" instruction for that feature. mimeType is now a
    // parameter instead of the hardcoded XLSX constant; behavior and the
    // pre-Android-10 fallback path are otherwise unchanged from what was
    // already proven here for Item Analysis exports.
    fun saveToDownloadsViaMediaStore(activity: Activity, fileName: String, bytes: ByteArray, mimeType: String) {
        try {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore did not return a Uri")

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open output stream")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Toast.makeText(activity, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * File-based overload — FileExportPrompt's Download path now calls
     * this one exclusively (see its own doc comment for the OOM crash
     * this was written to fix). Streams sourceFile -> MediaStore through
     * copyTo's small internal buffer (8KB by default) instead of needing
     * the file's full contents in one ByteArray first — peak memory here
     * is roughly constant regardless of whether the source file is a
     * 50KB xlsx or a 200MB backup/zip. The ByteArray overload above is
     * left in place only because ItemAnalysisXlsxExporter.exportAndShare()
     * (superseded by ItemAnalysisActivity.generateXlsxThenPrompt, but
     * still technically callable) still uses it for its own small xlsx
     * bytes — not worth touching that dead path for this fix.
     */
    fun saveToDownloadsViaMediaStore(activity: Activity, fileName: String, sourceFile: File, mimeType: String) {
        try {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore did not return a Uri")

            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Could not open output stream")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Toast.makeText(activity, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Pre-Android-10 fallback — same FileProvider + share-sheet pattern
     *  already used by Generate & Share PDF and the test-sheet batch zip. */
    fun shareViaFileProvider(activity: Activity, fileName: String, bytes: ByteArray, mimeType: String, shareTitle: String) {
        try {
            val dir = File(activity.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            Toast.makeText(
                activity,
                "Your Android version needs an extra permission to save directly to Downloads — sharing instead.",
                Toast.LENGTH_LONG
            ).show()

            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, shareTitle))
        } catch (e: Exception) {
            Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // --- OOXML construction (validated structure, see class doc comment) ---

    // No longer private (v2.8) — TestSheetBatchGenerator's sample Item
    // Analysis export needs to build .xlsx bytes directly into its own
    // zip, without triggering this file's share/download Activity flow
    // (saveToDownloadsViaMediaStore/shareViaFileProvider both need a real
    // Activity mid-flow, which a background batch generator doesn't have
    // a meaningful one for). Reusing this function instead of duplicating
    // the OOXML-writing logic keeps exactly one source of truth for the
    // file format.
    fun buildXlsx(testName: String, folder: String?, result: ItemAnalysis.Result): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // v1.2.0: when competencies are configured the workbook gets a
            // second sheet, "Competencies", holding the Most/Least Learned
            // tables. (They used to be appended to sheet 1 AFTER </sheetData>,
            // which is invalid OOXML — Excel and Sheets silently dropped
            // them, so they never appeared in the export.)
            val withCompetencies = result.competencySummaries.isNotEmpty()
            writeEntry(zip, "[Content_Types].xml", if (withCompetencies) CONTENT_TYPES_XML.replace("</Types>", SHEET2_CONTENT_TYPE_XML + "</Types>") else CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "xl/workbook.xml", workbookXml(withCompetencies))
            writeEntry(zip, "xl/_rels/workbook.xml.rels", if (withCompetencies) WORKBOOK_RELS_XML.replace("</Relationships>", SHEET2_REL_XML + "</Relationships>") else WORKBOOK_RELS_XML)
            writeEntry(zip, "xl/styles.xml", STYLES_XML)
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(testName, folder, result))
            if (withCompetencies) writeEntry(zip, "xl/worksheets/sheet2.xml", competencySheetXml(testName, folder, result))
        }
        return out.toByteArray()
    }

    internal fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun workbookXml(withCompetencies: Boolean): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="Item Analysis" sheetId="1" r:id="rId1"/>""" +
        (if (withCompetencies) """<sheet name="Competencies" sheetId="2" r:id="rId3"/>""" else "") +
        """</sheets></workbook>"""

    private const val SHEET2_CONTENT_TYPE_XML =
        """<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
    private const val SHEET2_REL_XML =
        """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>"""

    /**
     * Sheet layout: row 1 = test/folder title (merged-look via a single
     * wide cell, no true merge to keep this simple), row 2 = column
     * headers (styled, s="1"), rows 3+ = one row per item. Numbers use
     * t="n" (default numeric type); text uses t="inlineStr" — both
     * confirmed against a real xlsx reader before this was written.
     *
     * v1.1.1: when competencies are configured (result.competencySummaries
     * non-empty), the "Most Learned Competencies" and "Least Learned
     * Competencies" tables are written to their OWN sheet, "Competencies"
     * (competencySheetXml below) — v1.2.0 moved them there from below this
     * table, where they were never actually readable (see buildXlsx). This
     * sheet is now only the main table, so its AutoFilter, sorting and
     * filtering can never disturb them.
     */
    private fun sheetXml(testName: String, folder: String?, result: ItemAnalysis.Result): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        var rowNum = 1
        val titleText = "Item Analysis: ${escapeXml(testName)}" + (folder?.let { " (${escapeXml(it)})" } ?: "")
        sb.append(row(rowNum++, listOf(Cell.Text(titleText))))
        sb.append(row(rowNum++, listOf(Cell.Text("${result.totalUsable} scans analyzed, high/low groups of ${result.highGroupSize} each"))))
        sb.append(row(rowNum++, emptyList())) // blank spacer row

        val headerRowNum = rowNum
        // v1.1.1: Competency inserted between D Recommendation and Number
        // of Correct Response, per explicit request — column count grows
        // from 14 (A-N) to 15 (A-O).
        val headerCells = listOf(
            "Item", "Correct (High)", "Correct (Low)", "Correct (Total)",
            "P", "P Interpretation", "P Recommendation",
            "D", "D Interpretation", "D Recommendation",
            "Competency",
            "Number of Correct Response", "% of Correct Response", "Mastery Level", "Least Learned (Rank)"
        ).map { Cell.Text(it, styleIndex = 1) }
        sb.append(row(rowNum++, headerCells))

        result.items.forEach { item ->
            sb.append(
                row(
                    rowNum++,
                    listOf(
                        Cell.Number(item.questionNumber.toDouble()),
                        Cell.Number(item.correctHigh.toDouble()),
                        Cell.Number(item.correctLow.toDouble()),
                        Cell.Number(item.correctTotal.toDouble()),
                        Cell.Number(item.p),
                        Cell.Text(item.pLabel),
                        Cell.Text(item.pRecommendation),
                        Cell.Number(item.d),
                        Cell.Text(item.dLabel),
                        Cell.Text(item.dRecommendation),
                        // v1.1.1: wrapText style (index 2, see STYLES_XML)
                        // so a long competency name grows the row's own
                        // height instead of overflowing into neighboring
                        // cells or getting visually clipped.
                        Cell.Text(item.competencyName, styleIndex = 2),
                        // v1.1.0: test-result analysis, against ALL usable
                        // takers (result.totalUsable), not just the P/D
                        // high/low groups above — see ItemAnalysis.ItemStats'
                        // own doc comment.
                        Cell.Number(item.correctAllTakers.toDouble()),
                        Cell.Number(item.percentCorrectAllTakers),
                        Cell.Text(item.masteryLevel),
                        Cell.Number(item.leastLearnedRank.toDouble())
                    )
                )
            )
        }
        val lastMainTableRow = rowNum - 1

        sb.append("</sheetData>")
        // v1.1.0/v1.1.1: turns on Excel/Sheets' own native sort & filter
        // dropdown arrows across the whole header row — real spreadsheet
        // sorting (any column, not just the 4 the in-app preview can
        // sort), done entirely by declaring the range rather than this
        // exporter pre-sorting or writing any extra rows. A–O = the 15
        // columns in headerCells above. Scoped to ONLY the main table's
        // own rows (headerRowNum..lastMainTableRow) — deliberately does
        // NOT extend down into the competency summary rows appended
        // below, so those two tables can never be disturbed by sorting/
        // filtering the main table. Must come after </sheetData> and
        // before </worksheet> per the OOXML element order.
        sb.append("""<autoFilter ref="A$headerRowNum:O$lastMainTableRow"/>""")

        sb.append("</worksheet>")
        return sb.toString()
    }

    /** The "Competencies" sheet. One row per COMPETENCY (not per item): the
     *  Item No. column lists that competency's items compressed into ranges
     *  (e.g. "1-15, 18, 21-24"), then the competency name and its rank.
     *  Two tables side by side — "Most Learned" at A-C (rank 1 first) and
     *  "Least Learned" at E-G (worst rank first), column D left blank as the
     *  gap — matching the two DepEd paper table variants. See
     *  ItemAnalysis.compute()'s doc comment on the rank convention
     *  (1 = most learned/mastered). Every row is written as ONE <row>
     *  element via rowFromMap (cells from both tables merged), and the whole
     *  thing sits inside <sheetData>, so the file is valid OOXML. */
    private fun competencySheetXml(testName: String, folder: String?, result: ItemAnalysis.Result): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // <cols> must come before <sheetData>.
        sb.append(
            """<cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="46" customWidth="1"/>""" +
            """<col min="3" max="3" width="8" customWidth="1"/><col min="4" max="4" width="3" customWidth="1"/>""" +
            """<col min="5" max="5" width="16" customWidth="1"/><col min="6" max="6" width="46" customWidth="1"/>""" +
            """<col min="7" max="7" width="8" customWidth="1"/></cols>"""
        )
        sb.append("<sheetData>")

        val titleText = "Competency Summary: ${testName}" + (folder?.let { " ($it)" } ?: "")
        sb.append(row(1, listOf(Cell.Text(titleText))))

        val mostLearnedOrder = result.competencySummaries.sortedWith(compareBy({ it.rank }, { it.name }))
        val leastLearnedOrder = result.competencySummaries.sortedWith(compareByDescending<ItemAnalysis.CompetencySummary> { it.rank }.thenBy { it.name })

        val headerRow = 3
        sb.append(
            rowFromMap(
                headerRow,
                mapOf(
                    0 to Cell.Text("Item No.", styleIndex = 1),
                    1 to Cell.Text("Most Learned Competencies", styleIndex = 1),
                    2 to Cell.Text("Rank", styleIndex = 1),
                    4 to Cell.Text("Item No.", styleIndex = 1),
                    5 to Cell.Text("Least Learned Competencies", styleIndex = 1),
                    6 to Cell.Text("Rank", styleIndex = 1)
                )
            )
        )
        for (i in mostLearnedOrder.indices) {
            val most = mostLearnedOrder[i]
            val least = leastLearnedOrder[i]
            sb.append(
                rowFromMap(
                    headerRow + 1 + i,
                    mapOf(
                        0 to Cell.Text(formatItemRanges(most.itemNumbers), styleIndex = 2),
                        1 to Cell.Text(most.name, styleIndex = 2),
                        2 to Cell.Number(most.rank.toDouble()),
                        4 to Cell.Text(formatItemRanges(least.itemNumbers), styleIndex = 2),
                        5 to Cell.Text(least.name, styleIndex = 2),
                        6 to Cell.Number(least.rank.toDouble())
                    )
                )
            )
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** [1,2,3,...,15,18,21,22,23,24] -> "1-15, 18, 21-24". Runs of 3+
     *  consecutive numbers become "a-b"; runs of 1-2 are listed
     *  individually. Also used by ItemAnalysisActivity's preview. */
    fun formatItemRanges(itemNumbers: List<Int>): String {
        val nums = itemNumbers.distinct().sorted()
        if (nums.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = nums[0]
        var prev = nums[0]
        fun flush() {
            when {
                prev - start >= 2 -> parts.add("$start-$prev")
                prev == start -> parts.add("$start")
                else -> { parts.add("$start"); parts.add("$prev") }
            }
        }
        for (n in nums.drop(1)) {
            if (n == prev + 1) { prev = n } else { flush(); start = n; prev = n }
        }
        flush()
        return parts.joinToString(", ")
    }

    private sealed class Cell(val styleIndex: Int?) {
        class Text(val value: String, styleIndex: Int? = null) : Cell(styleIndex)
        class Number(val value: Double, styleIndex: Int? = null) : Cell(styleIndex)
    }

    private fun row(rowNum: Int, cells: List<Cell>): String {
        val sb = StringBuilder("""<row r="$rowNum">""")
        cells.forEachIndexed { index, cell ->
            val ref = "${columnLetter(index)}$rowNum"
            val styleAttr = cell.styleIndex?.let { """ s="$it"""" } ?: ""
            when (cell) {
                is Cell.Text -> sb.append("""<c r="$ref"$styleAttr t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                is Cell.Number -> sb.append("""<c r="$ref"$styleAttr t="n"><v>${cell.value}</v></c>""")
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    /** Same as row() above, but cells are placed at explicit (possibly
     *  non-contiguous, 0-based) column indices instead of always starting at
     *  column A — used for the two competency summary tables, which sit
     *  side by side at columns B-D and F-H with a blank gap column
     *  between them, all within the SAME row numbers. Building both
     *  tables' cells for a given row into ONE merged <row> element here
     *  (rather than two separate row() calls emitting two <row r="N">
     *  elements with the same N) matters: OOXML doesn't define what a
     *  reader should do with a duplicate row number, and real-world
     *  behavior ranges from silently keeping only one to prompting to
     *  repair the file — not a chance worth taking. */
    private fun rowFromMap(rowNum: Int, cellsByColumnIndex: Map<Int, Cell>): String {
        val sb = StringBuilder("""<row r="$rowNum">""")
        cellsByColumnIndex.toSortedMap().forEach { (colIndex, cell) ->
            val ref = "${columnLetter(colIndex)}$rowNum"
            val styleAttr = cell.styleIndex?.let { """ s="$it"""" } ?: ""
            when (cell) {
                is Cell.Text -> sb.append("""<c r="$ref"$styleAttr t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                is Cell.Number -> sb.append("""<c r="$ref"$styleAttr t="n"><v>${cell.value}</v></c>""")
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    /** 0-based column index -> spreadsheet letter (A, B, ... Z, AA, ...). */
    internal fun columnLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    internal fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    internal val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """</Types>"""

    internal val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    internal val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
        """</Relationships>"""

    // fontId=0 default, fontId=1 bold white; fillId=2 maroon solid (matches
    // @color/tnhs_maroon used throughout the app's headers elsewhere).
    // cellXfs index 1 = header style (s="1" above).
    // v1.1.1: cellXfs index 2 = wrap-text style (s="2") for the
    // Competency column and the two summary tables' competency-name
    // column — row height is deliberately NOT fixed anywhere in this
    // file, so Excel auto-expands each row to fit whatever wrapText
    // needs, per explicit request ("can slightly adjust height").
    internal val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF6B0F14"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf></cellXfs>""" +
        """</styleSheet>"""
}
