package com.jpagdi.mcqquickcheck

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/**
 * v1.2.0 — reads a class roster from a CSV or XLSX file into [Learner]s.
 *
 * Accepted layouts (the first sheet only):
 *  - a header row containing an ID column ("ID", "Student ID", "Learner ID",
 *    "ID No.", or a DepEd "LRN" / "Learner Reference Number") plus either one
 *    "Name"/"Learner" column, or separate "Last Name" / "First Name" (and
 *    optional "Middle Name"/"MI") columns — combined as "Last, First M."
 *  - an optional "Gender" / "Sex" column (M/F, Male/Female, Lalaki/Babae...)
 *  - no header at all: the column whose cells are ID_MIN..ID_MAX-digit numbers
 *    is the ID, the first other text column is the name, and a column holding
 *    only M/F-style values (if any) is the gender.
 *
 * Each ID must come out as ID_MIN_DIGITS..ID_MAX_DIGITS digits. Rows that
 * don't (blank, wrong length, duplicate within the file) are SKIPPED and
 * reported, never guessed — a short value usually means Excel dropped a
 * leading zero from a number cell, and silently padding it would risk a wrong
 * match. (Tip shown to the teacher: format the ID column as Text first.)
 * A gender value that isn't recognizably M/F does NOT skip the row: the
 * learner is imported with no gender and a warning is reported.
 */
object RosterImporter {

    class Result(val learners: List<Learner>, val skipped: List<String>, val warnings: List<String> = emptyList())

    class ImportException(message: String) : Exception(message)

    /** Detects XLSX by its ZIP signature ("PK"); anything else is read as CSV text. */
    fun parse(bytes: ByteArray): Result {
        val rows = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            readXlsxRows(bytes)
        } else {
            readCsvRows(String(bytes, Charsets.UTF_8))
        }
        return parseRows(rows)
    }

    fun parse(stream: InputStream): Result = parse(stream.readBytes())

    // --- rows -> learners ----------------------------------------------------------

    internal fun parseRows(rows: List<List<String>>): Result {
        if (rows.isEmpty()) throw ImportException("The file is empty.")

        var headerIndex = -1
        for (i in 0 until minOf(rows.size, 6)) {
            if (rows[i].any { isIdHeader(it) }) { headerIndex = i; break }
        }

        val idCol: Int
        val nameCols: List<Int>
        val genderCol: Int
        val firstDataRow: Int
        if (headerIndex >= 0) {
            val header = rows[headerIndex].map { it.trim().lowercase() }
            idCol = header.indexOfFirst { isIdHeader(it) }
            val full = header.indexOfFirst { it == "name" || it == "full name" || it == "learner" || it == "learner name" || it == "learner's name" || it == "student name" || it == "student" }
            val last = header.indexOfFirst { it == "last name" || it == "surname" || it == "lastname" || it == "family name" }
            val first = header.indexOfFirst { it == "first name" || it == "given name" || it == "firstname" }
            val mid = header.indexOfFirst { it == "middle name" || it == "mi" || it == "m.i." || it == "middle initial" || it == "middlename" }
            genderCol = header.indexOfFirst { it == "gender" || it == "sex" || it == "m/f" || it == "gender (m/f)" || it == "sex (m/f)" }
            nameCols = when {
                full >= 0 -> listOf(full)
                last >= 0 && first >= 0 -> listOfNotNull(last, first, mid.takeIf { it >= 0 })
                else -> throw ImportException("Found an ID column but no name column. Add a \"Name\" column (or \"Last Name\" and \"First Name\").")
            }
            firstDataRow = headerIndex + 1
        } else {
            val sample = rows.take(10)
            val width = sample.maxOf { it.size }
            idCol = (0 until width).firstOrNull { c ->
                sample.count { r -> r.getOrNull(c)?.let { ClassStorage.normalizeId(it) } != null } >= maxOf(1, sample.size / 2)
            } ?: throw ImportException("Couldn't find an ID column. Use a header row with \"ID\" and \"Name\", or make sure the IDs are ${SheetTemplate.ID_MIN_DIGITS}-${SheetTemplate.ID_MAX_DIGITS} digit numbers.")
            genderCol = (0 until width).firstOrNull { c ->
                c != idCol && sample.isNotEmpty() && sample.all { r ->
                    val v = r.getOrNull(c).orEmpty()
                    v.isBlank() || ClassStorage.normalizeGender(v).isNotEmpty()
                } && sample.any { r -> ClassStorage.normalizeGender(r.getOrNull(c).orEmpty()).isNotEmpty() }
            } ?: -1
            val nameCol = (0 until width).firstOrNull { c ->
                c != idCol && c != genderCol && sample.any { r -> r.getOrNull(c)?.any { ch -> ch.isLetter() } == true }
            } ?: throw ImportException("Couldn't find a name column next to the IDs.")
            nameCols = listOf(nameCol)
            firstDataRow = 0
        }

        val learners = ArrayList<Learner>()
        val skipped = ArrayList<String>()
        val warnings = ArrayList<String>()
        val seen = HashSet<String>()
        for (r in firstDataRow until rows.size) {
            val row = rows[r]
            if (row.all { it.isBlank() }) continue
            val rawId = row.getOrNull(idCol)?.trim().orEmpty()
            val name = combineName(nameCols.map { row.getOrNull(it)?.trim().orEmpty() })
            val label = "Row ${r + 1}" + if (name.isNotBlank()) " (${name})" else ""
            if (rawId.isBlank() && name.isBlank()) continue
            val id = ClassStorage.normalizeId(rawId)
            when {
                id == null -> skipped.add("$label: ID \"$rawId\" is not ${SheetTemplate.ID_MIN_DIGITS}-${SheetTemplate.ID_MAX_DIGITS} digits")
                name.isBlank() -> skipped.add("$label: no name")
                !seen.add(id) -> skipped.add("$label: ID $id appears more than once in the file")
                else -> {
                    val rawGender = if (genderCol >= 0) row.getOrNull(genderCol).orEmpty() else ""
                    if (ClassStorage.isUnrecognizedGender(rawGender)) {
                        warnings.add("$label: gender \"${rawGender.trim()}\" isn't M or F — imported without gender")
                    }
                    learners.add(Learner(id, name, ClassStorage.normalizeGender(rawGender)))
                }
            }
        }
        if (genderCol < 0 && learners.isNotEmpty()) {
            warnings.add(0, "No Gender column found — learners were imported without gender. Add a \"Gender\" column (M/F) to include it.")
        }
        return Result(learners, skipped, warnings)
    }

    private fun isIdHeader(cell: String): Boolean {
        val c = cell.trim().lowercase()
        return c == "id" || c == "id no" || c == "id no." || c == "id number" || c == "student id" || c == "learner id" ||
            c == "learner's id" || c == "lrn" || c.startsWith("lrn ") || c.contains("learner reference")
    }

    private fun combineName(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> {
            val last = parts[0]
            val first = parts[1]
            val mid = parts.getOrNull(2).orEmpty()
            val midInitial = if (mid.isNotBlank()) " ${mid.first().uppercaseChar()}." else ""
            when {
                last.isBlank() -> first
                first.isBlank() -> last
                else -> "$last, $first$midInitial"
            }
        }
    }

    // --- CSV ---------------------------------------------------------------------------

    internal fun readCsvRows(textIn: String): List<List<String>> {
        val text = textIn.removePrefix("\uFEFF")
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }
            ?.takeIf { d -> firstLine.contains(d) } ?: ','

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                    } else cell.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == delimiter -> { row.add(cell.toString()); cell.clear() }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    // --- XLSX --------------------------------------------------------------------------

    private fun readXlsxRows(bytes: ByteArray): List<List<String>> {
        var sharedStringsXml: String? = null
        val sheets = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStringsXml = String(zip.readBytes(), Charsets.UTF_8)
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheets[name] = String(zip.readBytes(), Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        // The first sheet by name order (sheet1.xml in files written by Excel/Sheets/LibreOffice;
        // sorting by length first keeps sheet2 ahead of sheet10).
        val firstSheetKey = sheets.keys.sortedWith(compareBy({ it.length }, { it })).firstOrNull()
            ?: throw ImportException("That doesn't look like a valid .xlsx file.")
        val sheetXml = sheets.getValue(firstSheetKey)
        val shared = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheetXml, shared)
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        val out = ArrayList<String>()
        var current: StringBuilder? = null
        var inT = false
        var inPhonetic = false
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "rPh" -> inPhonetic = true
                    "t" -> inT = true
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inT && !inPhonetic) current?.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                    "si" -> { out.add(current?.toString().orEmpty()); current = null }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        val rows = ArrayList<List<String>>()
        var row = HashMap<Int, String>()
        var cellCol = 0
        var cellType = ""
        var inV = false
        var inInlineT = false
        val value = StringBuilder()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = HashMap()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellCol = columnIndex(parser.getAttributeValue(null, "r") ?: "")
                        value.clear()
                    }
                    "v" -> inV = true
                    "t" -> if (cellType == "inlineStr") inInlineT = true
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inV || inInlineT) value.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inInlineT = false
                    "c" -> {
                        val raw = value.toString()
                        row[cellCol] = when (cellType) {
                            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
                            "inlineStr", "str" -> raw
                            "b" -> raw
                            else -> plainNumber(raw)
                        }
                    }
                    "row" -> {
                        val width = (row.keys.maxOrNull() ?: -1) + 1
                        rows.add(List(width) { row[it].orEmpty() })
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "1.36653015012E11" -> "136653015012"; plain text/blank passes through. */
    private fun plainNumber(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        return try {
            BigDecimal(t).toPlainString().let { if (it.contains('.') && it.trimEnd('0').endsWith('.')) it.substringBefore('.') else it }
        } catch (e: NumberFormatException) {
            t
        }
    }

    /** "C12" -> 2 (0-based column). */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return maxOf(n - 1, 0)
    }
}
