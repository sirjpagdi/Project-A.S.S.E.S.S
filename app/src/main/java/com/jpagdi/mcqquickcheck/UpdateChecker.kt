package com.jpagdi.mcqquickcheck

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors

/**
 * v3.9.0: checks GitHub Releases for a newer build than the one installed.
 * This app's first network call since the diagnostic-upload pipeline was
 * scrapped in v3.1.4 for a plain email handoff instead — unlike that one,
 * this is read-only (no data leaves the device) and hits GitHub's public
 * API directly, no custom backend to host or debug.
 *
 * Endpoint: https://api.github.com/repos/sirjpagdi/Project-A.S.S.E.S.S.-Release/releases/latest
 *
 * Release publishing convention this depends on (repo owner's
 * responsibility going forward, not enforced by this code):
 *  - Each release's tag must be exactly v{versionName} (e.g. "v3.9.0"), or
 *    /releases/latest won't resolve to it.
 *  - Must be published — not left as a draft or marked pre-release —
 *    since /releases/latest only ever returns the newest published one.
 *  - The release BODY must contain a line like "versionCode: 72" — see
 *    extractVersionCode() below for exactly why this, and not the tag/
 *    versionName, is what actually gets compared.
 *  - Must have an attached APK asset named
 *    project_assess_v{versionName}.apk (falls back to the first .apk
 *    asset found if that exact name isn't present, so a release still
 *    works even if this convention is missed — just logged as a
 *    non-fatal mismatch by whoever's checking the repo, not by this app).
 */
object UpdateChecker {

    private const val PREFS = "update_checker_prefs"
    private const val KEY_LAST_CHECKED = "last_checked_millis"
    private const val KEY_LAST_DISMISSED_VERSION_CODE = "last_dismissed_version_code"
    private const val KEY_PENDING_VERSION_CODE = "pending_version_code"
    private const val KEY_PENDING_VERSION_NAME = "pending_version_name"
    private const val KEY_PENDING_NOTES = "pending_notes"
    private const val KEY_PENDING_DOWNLOAD_URL = "pending_download_url"

    private const val RELEASES_URL =
        "https://api.github.com/repos/sirjpagdi/Project-A.S.S.E.S.S.-Release/releases/latest"
    /** Minimum gap between automatic attempts when the last one FAILED
     *  (offline, rate-limited, ...), so a bad connection or broken
     *  release can't make every single launch hit the network. A
     *  successful check isn't governed by this — it's governed by the
     *  once-per-calendar-day rule in autoCheckIfDue(). */
    private const val RETRY_GAP_MILLIS = 30L * 60 * 1000
    private const val KEY_LAST_ATTEMPT = "last_attempt_millis"
    private const val KEY_LAST_PROMPT = "last_prompt_millis"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    sealed class Result {
        data class UpdateAvailable(
            val versionName: String,
            val versionCode: Int,
            val notes: String,
            val downloadUrl: String
        ) : Result()
        object UpToDate : Result()
        object NoReleasesFound : Result()
        data class Failure(val message: String) : Result()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Set by MainActivity while it's on screen so a background check
     *  that finishes AFTER the home screen is already showing can still
     *  prompt right away. Always invoked on the main thread; null when
     *  nobody's listening (the result is cached either way). */
    @Volatile
    var autoUpdateListener: (() -> Unit)? = null

    private fun isSameLocalDay(a: Long, b: Long): Boolean {
        if (a <= 0L || b <= 0L) return false
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** Called on every app launch (see WelcomeActivity.onCreate — runs
     *  regardless of which path it takes through there). Checks GitHub on
     *  the FIRST launch of each calendar day (device local time — not a
     *  rolling 24 hours); any later launch the same day is a cheap no-op
     *  (one SharedPreferences read). A day only counts as "checked" once
     *  a check actually got an answer from the server — an offline or
     *  failed attempt doesn't use up the day, it just waits out
     *  RETRY_GAP_MILLIS before the next launch tries again. A newer
     *  release found here is cached via savePendingUpdate() and the
     *  home screen is told (autoUpdateListener) so it can prompt.
     *  FAILURES ARE STILL SILENT: a background check never interrupts the
     *  teacher with an error. */
    fun autoCheckIfDue(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (isSameLocalDay(p.getLong(KEY_LAST_CHECKED, 0L), now)) return
        if (now - p.getLong(KEY_LAST_ATTEMPT, 0L) < RETRY_GAP_MILLIS) return
        val appContext = context.applicationContext
        executor.execute {
            performCheck(appContext) { result ->
                if (result is Result.UpdateAvailable) {
                    mainHandler.post { autoUpdateListener?.invoke() }
                }
            }
        }
    }

    /** What the home screen should prompt about right now, or null. At
     *  most ONE prompt per calendar day, and only for an update that's
     *  cached, still newer than what's installed, and not previously
     *  skipped ("Skip This Version"). Call markPromptedToday() once the
     *  prompt is actually shown. */
    fun getUpdateToPromptToday(context: Context): Result.UpdateAvailable? {
        val lastPrompt = prefs(context).getLong(KEY_LAST_PROMPT, 0L)
        if (isSameLocalDay(lastPrompt, System.currentTimeMillis())) return null
        return getCachedPendingUpdate(context)
    }

    fun markPromptedToday(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_PROMPT, System.currentTimeMillis()).apply()
    }

    /** Manual check from the About screen's Check for Updates button —
     *  ALWAYS hits the network fresh, ignoring the once-a-day rule (though a
     *  successful result still counts as today's check, same as any other).
     *  Unlike autoCheckIfDue, [onResult] always fires with a real outcome
     *  the teacher can see — "Up to date" / a connection failure / etc. —
     *  posted back on the main thread. */
    fun checkNow(context: Context, onResult: (Result) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            performCheck(appContext) { result -> mainHandler.post { onResult(result) } }
        }
    }

    /** Runs on the executor thread. Records KEY_LAST_CHECKED before doing
     *  anything else — an attempt was made, whether or not it ultimately
     *  succeeds, so a string of offline launches doesn't retry every
     *  single time; it waits out the same 24h window as a successful
     *  check would. */
    private fun performCheck(context: Context, rawOnResult: (Result) -> Unit) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply()
        // Only a real answer from the server counts as "checked today".
        // Connection errors / HTTP errors leave the day open so the next
        // launch (after RETRY_GAP_MILLIS) tries again.
        val onResult: (Result) -> Unit = { result ->
            if (result !is Result.Failure) {
                prefs(context).edit().putLong(KEY_LAST_CHECKED, System.currentTimeMillis()).apply()
            }
            rawOnResult(result)
        }
        try {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                val code = connection.responseCode
                if (code == 404) {
                    onResult(Result.NoReleasesFound)
                    return
                }
                if (code !in 200..299) {
                    onResult(Result.Failure("Server returned HTTP $code. Please try again."))
                    return
                }

                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val releaseBody = json.optString("body", "")
                val remoteVersionCode = extractVersionCode(releaseBody)
                if (remoteVersionCode == null) {
                    onResult(Result.Failure("Found a release but couldn't read its version number — the release notes may be missing the required versionCode line."))
                    return
                }

                @Suppress("DEPRECATION")
                val currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

                if (remoteVersionCode <= currentVersionCode) {
                    clearPendingUpdate(context)
                    onResult(Result.UpToDate)
                    return
                }

                val versionName = tagName.removePrefix("v").ifBlank { "unknown" }
                val downloadUrl = findApkDownloadUrl(json, versionName)
                if (downloadUrl == null) {
                    onResult(Result.Failure("Version $versionName is available, but it has no downloadable APK attached yet."))
                    return
                }

                savePendingUpdate(context, versionName, remoteVersionCode, releaseBody, downloadUrl)
                onResult(Result.UpdateAvailable(versionName, remoteVersionCode, releaseBody, downloadUrl))
            } finally {
                connection.disconnect()
            }
        } catch (e: UnknownHostException) {
            onResult(Result.Failure("Couldn't check — no connection."))
        } catch (e: IOException) {
            onResult(Result.Failure("Couldn't check — no connection."))
        } catch (e: Exception) {
            onResult(Result.Failure("Something went wrong checking for updates. Please try again."))
        }
    }

    /** Looks for the exact project_assess_v{versionName}.apk asset name
     *  first (see this object's class doc comment for the convention);
     *  falls back to the first asset simply ending in .apk if that exact
     *  name isn't found, so a release still works even if the naming
     *  convention was missed on a given publish. Returns null only if
     *  there's no .apk asset at all. */
    private fun findApkDownloadUrl(releaseJson: JSONObject, versionName: String): String? {
        val assets = releaseJson.optJSONArray("assets") ?: return null
        val expectedName = "project_assess_v$versionName.apk"
        var fallbackUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url").ifBlank { null } ?: continue
            if (name == expectedName) return url
            if (fallbackUrl == null && name.endsWith(".apk", ignoreCase = true)) fallbackUrl = url
        }
        return fallbackUrl
    }

    /** Extracts an embedded versionCode from the release body, e.g. a line
     *  reading "versionCode: 72". Deliberately NOT parsing the tag or
     *  versionName as semver for comparison — versionCode only ever
     *  increases across the app's lifetime, while versionName is just a
     *  human-facing label a future release could reset (e.g. back to
     *  1.0.0 for a relaunch) that a naive string/semver parser would
     *  wrongly read as "older" than 3.7.4. versionCode is the one value
     *  this app can trust as a real ordering. */
    private fun extractVersionCode(body: String): Int? {
        val match = Regex("(?i)versionCode\\s*[:=]\\s*(\\d+)").find(body) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun savePendingUpdate(context: Context, versionName: String, versionCode: Int, notes: String, downloadUrl: String) {
        prefs(context).edit()
            .putInt(KEY_PENDING_VERSION_CODE, versionCode)
            .putString(KEY_PENDING_VERSION_NAME, versionName)
            .putString(KEY_PENDING_NOTES, notes)
            .putString(KEY_PENDING_DOWNLOAD_URL, downloadUrl)
            .apply()
    }

    private fun clearPendingUpdate(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_VERSION_CODE)
            .remove(KEY_PENDING_VERSION_NAME)
            .remove(KEY_PENDING_NOTES)
            .remove(KEY_PENDING_DOWNLOAD_URL)
            .apply()
    }

    /** Cached result of the most recent check (manual or silent
     *  auto-check) that found something newer than what's installed —
     *  lets AboutActivity resurface an update the silent background check
     *  found earlier, without making its own network call on every open.
     *  Returns null if nothing's cached, if the app has since actually
     *  been updated to (or past) that cached versionCode, or if the
     *  cached versionCode has already been dismissed via dismissUpdate()
     *  — dismissal only suppresses that specific versionCode; any future
     *  release with a HIGHER versionCode still resurfaces normally.
     *
     *  v3.9.3 FIX: this used to only check the cached versionCode against
     *  the dismissed one, never against what's ACTUALLY installed right
     *  now. Confirmed on a real device: after successfully installing an
     *  update, reopening About still showed the same "Update Available"
     *  dialog for the version the teacher had just installed — the stale
     *  cache from before the update sat there untouched, since nothing
     *  clears it except a fresh network check actually running (throttled
     *  to once a day) or an explicit dismiss. Now checks the real
     *  installed versionCode first and clears+returns null immediately if
     *  it's already caught up, so a stale cache can't outlive the update
     *  it was describing. */
    fun getCachedPendingUpdate(context: Context): Result.UpdateAvailable? {
        val p = prefs(context)
        val versionCode = p.getInt(KEY_PENDING_VERSION_CODE, -1)
        if (versionCode <= 0) return null

        @Suppress("DEPRECATION")
        val currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        if (versionCode <= currentVersionCode) {
            clearPendingUpdate(context)
            return null
        }

        val dismissed = p.getInt(KEY_LAST_DISMISSED_VERSION_CODE, -1)
        if (versionCode <= dismissed) return null
        val versionName = p.getString(KEY_PENDING_VERSION_NAME, null) ?: return null
        val notes = p.getString(KEY_PENDING_NOTES, "") ?: ""
        val downloadUrl = p.getString(KEY_PENDING_DOWNLOAD_URL, null) ?: return null
        return Result.UpdateAvailable(versionName, versionCode, notes, downloadUrl)
    }

    /** Marks this versionCode as dismissed — getCachedPendingUpdate() won't
     *  resurface it again, but will still resurface any future release
     *  with a higher versionCode than this one. */
    fun dismissUpdate(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_LAST_DISMISSED_VERSION_CODE, versionCode).apply()
    }
}
