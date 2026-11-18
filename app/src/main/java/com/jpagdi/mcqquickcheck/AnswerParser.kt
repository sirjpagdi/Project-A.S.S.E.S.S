package com.jpagdi.mcqquickcheck

object AnswerParser {

    /**
     * Parses a raw answer key string like "A,B,C,D,E" or "ABCDE" or one answer
     * per line into a list of 0-based choice indices (0=A, 1=B, ...).
     *
     * Kept for backward compatibility (e.g. a pasted key), though the main
     * flow now builds the key by tapping bubbles in AnswerKeyActivity instead
     * of typing it.
     */
    fun parseKey(raw: String, choiceCount: Int): List<Int> {
        val tokens = raw
            .split(Regex("[,\\n\\s]+"))
            .filter { it.isNotBlank() }

        return parseKeyTokens(tokens, choiceCount).answers
    }

    /** Result of parsing a raw scanned/typed key: the answers actually
     *  resolved, plus which 1-based question numbers were skipped because
     *  the scan couldn't confidently read them (a "?" token — see
     *  ScanActivity.showKeyScanResult, which emits "?" for any blank or
     *  isAmbiguous bubble). Kept separate from parseKey() above (which
     *  still returns just the List<Int> for existing callers like a pasted
     *  key, where every token is always expected to resolve) since this is
     *  specifically for the scanned-key path. */
    data class ParsedKeyResult(val answers: List<Int>, val unresolvedQuestionNumbers: List<Int>)

    /**
     * v3.3 FIX: parseKey() used to crash outright — token.first() on "?"
     * produced letter='?', index='?'-'A' = -18, which failed the
     * `index in 0 until choiceCount` check and threw
     * IllegalArgumentException("Invalid answer letter: ?"). Since that
     * exception aborted the ENTIRE parse, a scanned key with even ONE
     * blank/ambiguous question (exactly what an unclear scan produces —
     * see ScanActivity's showKeyScanResult, now also reached on
     * REJECTED_UNCLEAR, not just ACCEPTED) discarded every answer it DID
     * read correctly, not just the flagged one. Fixed by skipping "?"
     * tokens instead of throwing on them, and reporting which question
     * numbers were skipped so the caller can tell the teacher exactly
     * which ones still need manual entry — the resulting key is SHORTER
     * than questionCount rather than containing a placeholder value,
     * since answerKey is typed as List<Int> (non-nullable) everywhere
     * downstream (TestStorage, SheetGenerator, ResultAnnotator,
     * AnswerParser.grade() itself) and introducing a sentinel like -1
     * would need every one of those sites to guard against it. grade()
     * already tolerates a key shorter than questionCount (key.getOrNull(i)
     * above), so a short key from a partial scan fails safe already — it
     * just didn't have a friendly "here's what's missing" message
     * pointing at it until now.
     */
    fun parseKeyTokens(tokens: List<String>, choiceCount: Int): ParsedKeyResult {
        val answers = mutableListOf<Int>()
        val unresolved = mutableListOf<Int>()
        tokens.forEachIndexed { i, token ->
            val trimmed = token.trim()
            if (trimmed == "?") {
                unresolved.add(i + 1)
                return@forEachIndexed
            }
            val letter = trimmed.uppercase().first()
            val index = letter - 'A'
            require(index in 0 until choiceCount) { "Invalid answer letter: $token" }
            answers.add(index)
        }
        return ParsedKeyResult(answers, unresolved)
    }

    data class GradeSummary(
        val correct: Int,
        val total: Int,
        val perQuestion: List<Boolean?>, // null = left blank on the scanned sheet
        val earnedPoints: Int = correct,
        val totalPoints: Int = total
    )

    /**
     * Grades a scanned sheet against [key], weighting each question by
     * [points] (defaults to 1 pt each when not provided, so old call sites
     * still work).
     */
    fun grade(
        key: List<Int>,
        scanned: List<BubbleGrader.QuestionResult>,
        points: List<Int> = List(key.size) { 1 }
    ): GradeSummary {
        // An ambiguous mark NEVER counts as correct, even if it happens to
        // land on the right bubble — previously ambiguity was only a
        // side-note flag shown to the teacher while still silently counting
        // toward the score if it matched. That gave the benefit of the
        // doubt to unclear marks, which real scans showed was too lenient.
        val perQuestion = scanned.mapIndexed { i, result ->
            val expected = key.getOrNull(i) ?: return@mapIndexed null
            when {
                result.chosenIndex == null -> null // blank
                result.isAmbiguous -> false // unclear mark — never given credit
                else -> result.chosenIndex == expected
            }
        }

        // "Work backwards": explicitly count what's wrong/ambiguous and
        // blank first, then derive correct = total - those, rather than
        // only ever computing "correct" directly — makes the stricter rule
        // above the natural default instead of an easy-to-miss special case.
        val blankCount = perQuestion.count { it == null }
        val wrongOrAmbiguousCount = perQuestion.count { it == false }
        val correct = key.size - blankCount - wrongOrAmbiguousCount

        val earnedPoints = perQuestion.withIndex().sumOf { (i, isCorrect) ->
            if (isCorrect == true) points.getOrElse(i) { 1 } else 0
        }
        val totalPoints = points.take(key.size).sum().let { if (it == 0) key.size else it }
        return GradeSummary(
            correct = correct,
            total = key.size,
            perQuestion = perQuestion,
            earnedPoints = earnedPoints,
            totalPoints = totalPoints
        )
    }
}
