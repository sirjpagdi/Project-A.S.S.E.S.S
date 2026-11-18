package com.jpagdi.mcqquickcheck

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.max

/**
 * v1.2.0 — tiny digit classifier for the handwritten-ID boxes: an input -> H ->
 * 10 ReLU network evaluated in plain Kotlin (no ML runtime, no extra
 * dependency — the whole model is a ~150 KB JSON asset,
 * assets/id_digit_model.json, produced by tools/id_model/train_id_model.py).
 *
 * Input is a description of the 32x32 digit frame IdReader builds from a
 * cleaned box crop. The model JSON says which one ("features"): "hog-v1"
 * (v1.2.3 — stroke-direction histograms, 324 numbers) or "blocks-8x8" (the
 * original 64 block sums). See tools/id_model/id_features.py, the reference
 * for both. Model and features MUST stay in sync: retrain whenever the
 * feature pipeline changes.
 *
 * Returns null from [load] if the asset is missing/corrupt, in which case the
 * Classes feature still works with manual linking (IdReader reports "can't
 * read" for every scan instead of crashing).
 */
class IdDigitClassifier private constructor(
    private val w1: FloatArray,      // [hidden * 64], row-major
    private val b1: FloatArray,
    private val w2: FloatArray,      // [10 * hidden], row-major
    private val b2: FloatArray,
    private val hidden: Int,
    private val scale: Float,
    /** "hog-v1" or "blocks-8x8": which extractor IdReader must use for this model. */
    val featureKind: String,
    private val inputSize: Int
) {
    /** Softmax probabilities for digits 0..9. [features] matches [featureKind]. */
    fun predict(features: FloatArray): FloatArray {
        val h = FloatArray(hidden)
        for (j in 0 until hidden) {
            var sum = b1[j]
            val base = j * inputSize
            for (i in 0 until inputSize) sum += w1[base + i] * (features[i] / scale)
            h[j] = max(0f, sum)
        }
        val z = FloatArray(10)
        var zMax = -Float.MAX_VALUE
        for (k in 0 until 10) {
            var sum = b2[k]
            val base = k * hidden
            for (j in 0 until hidden) sum += w2[base + j] * h[j]
            z[k] = sum
            if (sum > zMax) zMax = sum
        }
        var total = 0f
        for (k in 0 until 10) { z[k] = exp(z[k] - zMax); total += z[k] }
        for (k in 0 until 10) z[k] /= total
        return z
    }

    companion object {
        const val FEATURES_BLOCKS = "blocks-8x8"
        const val FEATURES_HOG = "hog-v1"
        private const val BLOCKS_INPUT = 64
        private const val HOG_INPUT = 324
        private const val ASSET = "id_digit_model.json"

        @Volatile private var cached: IdDigitClassifier? = null
        @Volatile private var triedLoading = false

        /** Loads (once) and caches the model. Null if unavailable. */
        fun load(context: Context): IdDigitClassifier? {
            if (triedLoading) return cached
            synchronized(this) {
                if (triedLoading) return cached
                cached = try {
                    val text = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
                    parse(JSONObject(text))
                } catch (e: Exception) {
                    null
                }
                triedLoading = true
                return cached
            }
        }

        private fun parse(obj: JSONObject): IdDigitClassifier? {
            val kind = obj.optString("features", FEATURES_BLOCKS)   // models from before v1.2.3 have no "features"
            val input = obj.optInt("input")
            val expected = when (kind) {
                FEATURES_BLOCKS -> BLOCKS_INPUT
                FEATURES_HOG -> HOG_INPUT
                else -> return null
            }
            if (obj.optString("format") != "mlp-v1" || input != expected) return null
            val w1Rows = obj.getJSONArray("w1")
            val hidden = w1Rows.length()
            val w1 = FloatArray(hidden * input)
            for (j in 0 until hidden) {
                val row = w1Rows.getJSONArray(j)
                if (row.length() != input) return null
                for (i in 0 until input) w1[j * input + i] = row.getDouble(i).toFloat()
            }
            val b1Arr = obj.getJSONArray("b1")
            if (b1Arr.length() != hidden) return null
            val b1 = FloatArray(hidden) { b1Arr.getDouble(it).toFloat() }
            val w2Rows = obj.getJSONArray("w2")
            if (w2Rows.length() != 10) return null
            val w2 = FloatArray(10 * hidden)
            for (k in 0 until 10) {
                val row = w2Rows.getJSONArray(k)
                if (row.length() != hidden) return null
                for (j in 0 until hidden) w2[k * hidden + j] = row.getDouble(j).toFloat()
            }
            val b2Arr = obj.getJSONArray("b2")
            if (b2Arr.length() != 10) return null
            val b2 = FloatArray(10) { b2Arr.getDouble(it).toFloat() }
            return IdDigitClassifier(w1, b1, w2, b2, hidden, obj.optDouble("scale", 16.0).toFloat(), kind, input)
        }
    }
}
