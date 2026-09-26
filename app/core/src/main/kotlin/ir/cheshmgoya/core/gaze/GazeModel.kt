package ir.cheshmgoya.core.gaze

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/** One calibration observation: features while the patient looked at [target]. */
data class GazeSample(val features: DoubleArray, val target: Pt)

/**
 * Ridge regression from gaze features to a screen point (both axes normalised
 * 0..1 over the app window). Fitted from the 9-point calibration.
 */
@Serializable
data class GazeModel(
    val mean: List<Double>,
    val std: List<Double>,
    val wx: List<Double>,
    val wy: List<Double>,
    val bx: Double,
    val by: Double,
) {
    fun predict(features: DoubleArray): Pt {
        var x = bx
        var y = by
        for (i in features.indices) {
            val z = (features[i] - mean[i]) / std[i]
            x += wx[i] * z
            y += wy[i] * z
        }
        return Pt(x, y)
    }

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(s: String): GazeModel? = runCatching { json.decodeFromString<GazeModel>(s) }.getOrNull()
            ?.takeIf { it.mean.size == GazeFeatures.SIZE }

        fun fit(samples: List<GazeSample>, lambda: Double = 1.0): GazeModel {
            require(samples.size >= GazeFeatures.SIZE + 2) { "not enough calibration samples" }
            val n = samples.size
            val d = samples[0].features.size
            val mean = DoubleArray(d) { j -> samples.sumOf { it.features[j] } / n }
            val std = DoubleArray(d) { j ->
                sqrt(samples.sumOf { (it.features[j] - mean[j]).let { v -> v * v } } / n).takeIf { it > 1e-9 } ?: 1.0
            }
            val z = Array(n) { i -> DoubleArray(d) { j -> (samples[i].features[j] - mean[j]) / std[j] } }
            val bx = samples.sumOf { it.target.x } / n
            val by = samples.sumOf { it.target.y } / n
            // (ZᵀZ + λI) w = Zᵀ(t - b)
            val a = Array(d) { r -> DoubleArray(d) { c -> var s = 0.0; for (i in 0 until n) s += z[i][r] * z[i][c]; s + if (r == c) lambda else 0.0 } }
            val rx = DoubleArray(d) { r -> var s = 0.0; for (i in 0 until n) s += z[i][r] * (samples[i].target.x - bx); s }
            val ry = DoubleArray(d) { r -> var s = 0.0; for (i in 0 until n) s += z[i][r] * (samples[i].target.y - by); s }
            return GazeModel(mean.toList(), std.toList(), solve(a, rx).toList(), solve(a, ry).toList(), bx, by)
        }

        /** Gaussian elimination with partial pivoting; the ridge term keeps it well-conditioned. */
        private fun solve(m: Array<DoubleArray>, b: DoubleArray): DoubleArray {
            val n = b.size
            val a = Array(n) { r -> m[r].copyOf() }
            val x = b.copyOf()
            for (col in 0 until n) {
                val p = (col until n).maxBy { kotlin.math.abs(a[it][col]) }
                if (p != col) { val t = a[p]; a[p] = a[col]; a[col] = t; val tb = x[p]; x[p] = x[col]; x[col] = tb }
                val piv = a[col][col]
                for (r in col + 1 until n) {
                    val f = a[r][col] / piv
                    if (f == 0.0) continue
                    for (c in col until n) a[r][c] -= f * a[col][c]
                    x[r] -= f * x[col]
                }
            }
            for (r in n - 1 downTo 0) {
                var s = x[r]
                for (c in r + 1 until n) s -= a[r][c] * x[c]
                x[r] = s / a[r][r]
            }
            return x
        }
    }
}
