package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Bitmap

/**
 * v1.2.0 — the glue between a freshly graded scan and the Classes feature.
 * ScanActivity calls [link] once per scan (after the score is known) and
 * [updateScore] when the score changes later (an ambiguous bubble resolved).
 *
 * Nothing here blocks or slows the normal scan: when the feature is off, the
 * test has no assigned classes, or the sheet has no ID row, it returns
 * [Outcome.Skipped] without doing any work.
 */
object ClassLinker {

    sealed class Outcome {
        /** Feature not in play for this scan (off / locked / no classes / no ID row). */
        object Skipped : Outcome()

        /** Confidently matched; the score is already recorded. */
        data class Linked(val learner: Learner, val className: String) : Outcome()

        /** Matched confidently but the learner ALREADY has a score on this
         *  exam (a rescan). Nothing is saved until the teacher chooses —
         *  call [replaceExisting] to overwrite, or do nothing to keep the old. */
        data class Duplicate(
            val learner: Learner, val className: String, val classId: String,
            val existing: LearnerResult, val pendingResult: LearnerResult
        ) : Outcome()

        /** ID couldn't be matched confidently; filed under "Needs ID check"
         *  on the class sheet, with [pendingId] to update if the score changes. */
        data class NeedsCheck(val pendingId: String, val reason: String) : Outcome()
    }

    /** True when scanning this test should try to link learners. */
    fun appliesTo(context: Context, test: SavedTest): Boolean =
        ClassesAccess.isActive(context) && test.template.idDigits > 0 && test.classIds.isNotEmpty()

    fun link(
        context: Context, test: SavedTest, sheet: Bitmap,
        earned: Int, total: Int, scanId: String?
    ): Outcome {
        if (!appliesTo(context, test)) return Outcome.Skipped
        val digits = test.template.idDigits
        val classes = ClassStorage.getClasses(context, test.classIds)
        // Only IDs exactly as long as the printed ID row can be matched.
        val roster = classes.flatMap { c -> c.learners.map { it.learnerId } }.filter { it.length == digits }
        if (roster.isEmpty()) return Outcome.Skipped

        val read = IdReader.read(context, sheet, digits)
        val testName = test.template.testName
        if (read == null) {
            diag { ScanDiagnostics.logIdNote(context, testName, "ID reader unavailable (digit model or OpenCV) — scan sent to Needs ID check") }
            val p = LearnerResultStorage.addPending(context, testName, earned, total, scanId, emptyList(), "")
            return Outcome.NeedsCheck(p.id, "ID reader unavailable")
        }
        // Learners who already have a score on this exam are less likely to own
        // the NEXT sheet (IdMatcher's "already scored" prior).
        val alreadyScored = LearnerResultStorage.listResults(context, testName).map { it.learnerId }.toSet()
        val match = IdMatcher.match(read, roster, alreadyScored)
        val bestId = match.bestId
        if (bestId == null || !match.confident) {
            val p = LearnerResultStorage.addPending(context, testName, earned, total, scanId, match.candidates, read.digits, read.encode())
            val reason = if (read.readableCount < IdMatcher.minReadable(digits)) "ID not clearly written" else "ID unclear"
            diag { ScanDiagnostics.logIdMatch(context, testName, "NEEDS_CHECK ($reason)", digits, roster.size, read, match, null, match.candidates.firstOrNull()) }
            return Outcome.NeedsCheck(p.id, reason)
        }

        val (cls, learner) = ClassStorage.findLearner(classes, bestId)
            ?: run {
                val p = LearnerResultStorage.addPending(context, testName, earned, total, scanId, match.candidates, read.digits, read.encode())
                return Outcome.NeedsCheck(p.id, "ID unclear")
            }
        val result = LearnerResult(testName, cls.id, learner.learnerId, earned, total, scanId, System.currentTimeMillis())
        val existing = LearnerResultStorage.getResult(context, testName, learner.learnerId)
        if (existing != null && existing.scanId != scanId) {
            diag { ScanDiagnostics.logIdMatch(context, testName, "DUPLICATE (best match already has a score)", digits, roster.size, read, match, bestId, null) }
            return Outcome.Duplicate(learner, cls.name, cls.id, existing, result)
        }
        LearnerResultStorage.upsertResult(context, result)
        fileScanUnderClass(context, testName, cls.name, scanId)
        diag { ScanDiagnostics.logIdMatch(context, testName, "LINKED", digits, roster.size, read, match, bestId, null) }
        return Outcome.Linked(learner, cls.name)
    }

    /** Diagnostics must never break scanning or linking. */
    private inline fun diag(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }

    /** The ID read for a pending scan. [reread] = decode the saved sheet image
     *  again with the CURRENT reader (picks up reader improvements; slow). Either
     *  way, if there's no usable image, the read stored with the scan is used —
     *  instant, and works even with scanned-sheet saving off. Null only for
     *  scans that have neither. */
    private fun readFor(context: Context, p: PendingLink, digits: Int, reread: Boolean): IdReader.Read? {
        val scanId = p.scanId
        if (reread && scanId != null) {
            val bmp = ScannedSheetStorage.loadScan(context, scanId)
            if (bmp != null) {
                val fresh = try { IdReader.read(context, bmp, digits) } finally { bmp.recycle() }
                if (fresh != null) return fresh
            }
        }
        return IdReader.Read.decode(p.readProbs, digits)
    }

    class RecheckSummary(val linked: Int, val remaining: Int, val noImage: Int)

    /** Re-reads every "Needs ID check" scan of [testName] from its SAVED image
     *  and links the ones that now match confidently. Runs several passes: each
     *  newly linked learner counts as "already scored" for the next scan, which
     *  breaks near-ties (the last few sheets resolve by elimination). Scans
     *  that still can't be matched stay pending, with refreshed guesses. Slow
     *  (decodes images) — call off the main thread. Never overwrites an
     *  existing score: a scan whose best match already has one stays pending. */
    fun recheckPending(context: Context, testName: String, rereadImages: Boolean = true): RecheckSummary {
        val pendingAtStart = LearnerResultStorage.listPending(context, testName)
        val test = TestStorage.loadTestOrNull(context, testName)
        val digits = test?.template?.idDigits ?: 0
        if (test == null || digits <= 0 || !ClassesAccess.isActive(context)) {
            return RecheckSummary(0, pendingAtStart.size, 0)
        }
        val classes = ClassStorage.getClasses(context, test.classIds)
        val roster = classes.flatMap { c -> c.learners.map { it.learnerId } }.filter { it.length == digits }
        if (roster.isEmpty()) return RecheckSummary(0, pendingAtStart.size, 0)

        val scored = LearnerResultStorage.listResults(context, testName).map { it.learnerId }.toMutableSet()
        val reads = HashMap<String, IdReader.Read?>()
        var linked = 0
        var pass = 0
        var progress = true
        while (progress && pass < 4) {
            progress = false
            pass++
            for (p in LearnerResultStorage.listPending(context, testName)) {
                if (!reads.containsKey(p.id)) reads[p.id] = readFor(context, p, digits, rereadImages)
                val read = reads[p.id] ?: continue
                val match = IdMatcher.match(read, roster, scored)
                val best = match.bestId
                val found = if (best != null && match.confident && best !in scored) ClassStorage.findLearner(classes, best) else null
                if (found != null) {
                    val (cls, learner) = found
                    if (resolvePending(context, p, cls.id, cls.name, learner.learnerId, replaceIfExists = false, confirmedByTeacher = false) == null) {
                        scored.add(learner.learnerId)
                        linked++
                        progress = true
                        diag { ScanDiagnostics.logIdMatch(context, testName, "LINKED (re-check pass $pass)", digits, roster.size, read, match, learner.learnerId, null) }
                    }
                } else {
                    LearnerResultStorage.updatePendingRead(context, testName, p.id, match.candidates, read.digits, read.encode())
                }
            }
        }
        val remainingList = LearnerResultStorage.listPending(context, testName)
        val noImage = remainingList.count { reads[it.id] == null }
        diag { ScanDiagnostics.logIdRecheck(context, testName, linked, remainingList.size, noImage) }
        return RecheckSummary(linked, remainingList.size, noImage)
    }

    /** One scan folder per assigned class: once a scan is tied to a learner,
     *  its saved image is moved into that learner's class folder (created on
     *  demand). No-op when the scan wasn't saved (autosave off). */
    fun fileScanUnderClass(context: Context, testName: String, className: String, scanId: String?) {
        if (scanId == null) return
        ScannedSheetStorage.addFolder(context, testName, className)
        ScannedSheetStorage.moveScan(context, scanId, className)
    }

    /** The teacher chose to replace the learner's earlier score with this scan's. */
    fun replaceExisting(context: Context, d: Outcome.Duplicate) {
        LearnerResultStorage.upsertResult(context, d.pendingResult)
        fileScanUnderClass(context, d.pendingResult.testName, d.className, d.pendingResult.scanId)
    }

    /** Score changed after linking (an ambiguous bubble was resolved). */
    fun updateScore(
        context: Context, testName: String, linkedId: String?, pendingId: String?,
        classId: String?, earned: Int, total: Int, scanId: String?
    ) {
        if (linkedId != null && classId != null) {
            LearnerResultStorage.upsertResult(
                context, LearnerResult(testName, classId, linkedId, earned, total, scanId, System.currentTimeMillis())
            )
        } else if (pendingId != null) {
            LearnerResultStorage.updatePendingScore(context, testName, pendingId, earned, total)
        }
    }

    /** Teacher picked a learner for a pending scan from the class sheet.
     *  Returns the existing result if that learner already has a score (the
     *  caller must confirm replacement); null when saved. */
    fun resolvePending(
        context: Context, pending: PendingLink, classId: String, className: String, learnerId: String,
        replaceIfExists: Boolean,
        // false for the automatic links ClassLinker.recheckPending makes — those
        // are confident matches, not ground truth from the teacher.
        confirmedByTeacher: Boolean = true
    ): LearnerResult? {
        val existing = LearnerResultStorage.getResult(context, pending.testName, learnerId)
        if (existing != null && !replaceIfExists) return existing
        LearnerResultStorage.upsertResult(
            context,
            LearnerResult(pending.testName, classId, learnerId, pending.earnedPoints, pending.totalPoints, pending.scanId, System.currentTimeMillis())
        )
        LearnerResultStorage.removePending(context, pending.testName, pending.id)
        fileScanUnderClass(context, pending.testName, className, pending.scanId)
        if (confirmedByTeacher) diag { ScanDiagnostics.logIdConfirmed(context, pending.testName, pending.readDigits, learnerId, pending.candidateIds) }
        return null
    }
}
