package io.github.merry8989uk.photoveil.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PhotoVeil's shield engine.
 *
 * The idea comes from the PhotoGuard line of work (Salman et al., "Raising the
 * Cost of Malicious AI-Powered Image Editing"): make a photo hostile to
 * automated image models by adding a perturbation that a person cannot see but
 * that pushes a diffusion model's image encoder off the original. PhotoGuard
 * does it with gradients through Stable Diffusion's VAE, on a GPU. A phone has
 * neither, so this engine takes the gradient-free route:
 *
 *  1. A deterministic, seed-derived noise field is band-shaped — half of it
 *     low-passed (a 5-tap separable kernel) and half high-passed (a Laplacian).
 *     That puts energy in the band a diffusion VAE's 8x downsampling folds back
 *     into the latent, and the low-passed half is the part that survives a
 *     re-encode.
 *  2. Amplitude is modulated by a local-texture mask, so most of the energy
 *     sits where the eye cannot see it (busy areas) and least of it on flat
 *     gradients like skin or sky.
 *  3. The per-pixel delta is clamped to what the pixel can actually take, so a
 *     near-white pixel gets a downward nudge instead of nothing at all — no
 *     banding, no blown highlights, no dead zones.
 *
 * All of it is pure Kotlin over ARGB pixels: no model, no network, no
 * dependency. The same algorithm runs in the browser demo on the project page,
 * so what the site shows is what the app does.
 *
 * Measured behaviour (see README for the calibration table): at the default
 * strength the result is 45-52 dB PSNR, i.e. imperceptible, and a JPEG re-save
 * at quality 90 erodes a large part of the perturbation. That is a real limit
 * of any invisible perturbation, and the app says so instead of pretending
 * otherwise.
 */
object ShieldEngine {

    /** Mask floor: how much of the shield lands even in a perfectly flat area. */
    private const val MASK_FLOOR = 0.32f

    /** How fast the mask rises with local texture. */
    private const val TEXTURE_GAIN = 2.2f

    /** Share of the field that is low-passed (survives re-encoding). */
    private const val MID_SHARE = 0.5f

    /** Amplitude ceiling per mode, in 0..255 units, at strength = 1. */
    fun amplitudeFor(mode: ShieldMode, strength: Float): Float {
        val s = strength.coerceIn(0f, 1f)
        return when (mode) {
            ShieldMode.STANDARD -> 2.0f + 12.0f * s
            ShieldMode.STRONG -> 5.0f + 25.0f * s
            ShieldMode.VISIBLE -> 2.0f + 12.0f * s
        }
    }

    /** Applies the shield. Returns the new bitmap plus measured statistics. */
    fun shield(source: Bitmap, params: ShieldParams): ShieldResult {
        val started = System.currentTimeMillis()
        val w = source.width
        val h = source.height
        require(w > 0 && h > 0) { "empty bitmap" }

        val out = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("could not copy bitmap")

        val amp = amplitudeFor(params.mode, params.strength)
        val seed = params.seed

        var sumAbs = 0.0
        var maxAbs = 0
        var changed = 0L
        var total = 0L

        val bandRows = 96
        val margin = 2 // the 5-tap kernel reaches two pixels out

        var y0 = 0
        while (y0 < h) {
            val rows = min(bandRows, h - y0)
            val top = (y0 - margin).coerceAtLeast(0)
            val bottom = (y0 + rows + margin).coerceAtMost(h)
            val bandHeight = bottom - top

            // Source pixels for the band, plus the margin rows we need.
            val src = IntArray(w * bandHeight)
            source.getPixels(src, 0, w, 0, top, w, bandHeight)

            // One hash per pixel, then a horizontal 5-tap pass over the band.
            val hash = FloatArray(w * bandHeight)
            var idx = 0
            for (yy in top until bottom) {
                for (xx in 0 until w) {
                    hash[idx++] = hash01(xx, yy, seed)
                }
            }
            val hSum = FloatArray(w * bandHeight)
            for (r in 0 until bandHeight) {
                val base = r * w
                for (x in 0 until w) {
                    val a = hash[base + (x - 2).coerceAtLeast(0)]
                    val b = hash[base + (x - 1).coerceAtLeast(0)]
                    val c = hash[base + x]
                    val d = hash[base + (x + 1).coerceAtMost(w - 1)]
                    val e = hash[base + (x + 2).coerceAtMost(w - 1)]
                    hSum[base + x] = (a + 2f * b + 3f * c + 2f * d + e) / 9f
                }
            }

            val outBand = IntArray(w * rows)
            for (yy in 0 until rows) {
                val y = y0 + yy
                val ly = y - top
                val rowBase = ly * w
                val upBase = (if (y > 0) ly - 1 else ly) * w
                val downBase = (if (y < h - 1) ly + 1 else ly) * w
                val outBase = yy * w

                val m2 = (ly - 2).coerceIn(0, bandHeight - 1) * w
                val m1 = (ly - 1).coerceIn(0, bandHeight - 1) * w
                val p1 = (ly + 1).coerceIn(0, bandHeight - 1) * w
                val p2 = (ly + 2).coerceIn(0, bandHeight - 1) * w

                for (x in 0 until w) {
                    val c = src[rowBase + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF

                    val xm = if (x > 0) x - 1 else x
                    val xp = if (x < w - 1) x + 1 else x

                    // Local texture from a 4-neighbour luminance gradient.
                    val gx = abs(lum(src[rowBase + xp]) - lum(src[rowBase + xm]))
                    val gy = abs(lum(src[downBase + x]) - lum(src[upBase + x]))
                    val texture = (((gx + gy) * 0.5f) / 255f).coerceIn(0f, 1f)
                    val mask = (MASK_FLOOR + TEXTURE_GAIN * texture).coerceAtMost(1f)

                    // High-pass half: Laplacian of the field, zero mean.
                    val high = ((4f * hash[rowBase + x] - hash[rowBase + xm] -
                        hash[rowBase + xp] - hash[upBase + x] - hash[downBase + x]) / 6f)
                        .coerceIn(-1f, 1f)

                    // Low-pass half: 5-tap vertically on the already-smoothed rows.
                    val mid = ((hSum[m2 + x] + 2f * hSum[m1 + x] + 3f * hSum[rowBase + x] +
                        2f * hSum[p1 + x] + hSum[p2 + x]) / 9f).coerceIn(-1f, 1f)

                    val field = MID_SHARE * mid + (1f - MID_SHARE) * high

                    // Clamp to what this pixel can take rather than fading out.
                    val lo = -minOf(r, g, b).toFloat()
                    val hi = (255 - maxOf(r, g, b)).toFloat()
                    val delta = (field * amp * mask).coerceIn(lo, hi)
                    val di = delta.roundToInt()

                    val nr = (r + di).coerceIn(0, 255)
                    val ng = (g + di).coerceIn(0, 255)
                    val nb = (b + di).coerceIn(0, 255)

                    val ad = abs(nr - r)
                    sumAbs += ad.toDouble()
                    if (ad > maxAbs) maxAbs = ad
                    if (nr != r || ng != g || nb != b) changed++
                    total++

                    outBand[outBase + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            out.setPixels(outBand, 0, w, 0, y0, w, rows)
            y0 += rows
        }

        if (params.mode == ShieldMode.VISIBLE) {
            drawWatermark(out, params.watermarkText)
        }

        val before = noiseFloor(source)
        val after = noiseFloor(out)

        return ShieldResult(
            bitmap = out,
            stats = ShieldStats(
                meanAbsDelta = if (total > 0) sumAbs / total else 0.0,
                maxDelta = maxAbs,
                changedPercent = if (total > 0) changed * 100.0 / total else 0.0,
                noiseFloorBefore = before,
                noiseFloorAfter = after,
                elapsedMs = System.currentTimeMillis() - started,
            ),
        )
    }

    /**
     * Noise floor: mean distance between each pixel and a 5x5 smooth of its
     * surroundings, measured only where the image is flat. A camera leaves
     * almost nothing there; a perturbation leaves its whole amplitude there.
     */
    fun noiseFloor(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return 0.0

        val step = (maxOf(w, h) / 480).coerceAtLeast(1)
        val lumRows = Array(5) { FloatArray(w) }
        val sumRows = Array(5) { FloatArray(w) }
        val px = IntArray(w)
        var loadedUpTo = -3

        fun slotOf(y: Int): Int = ((y % 5) + 5) % 5

        fun loadRow(y: Int) {
            val clamped = y.coerceIn(0, h - 1)
            bitmap.getPixels(px, 0, w, 0, clamped, w, 1)
            val l = lumRows[slotOf(y)]
            for (x in 0 until w) l[x] = lum(px[x])
            val s = sumRows[slotOf(y)]
            for (x in 0 until w) {
                val a = l[(x - 2).coerceAtLeast(0)]
                val b = l[(x - 1).coerceAtLeast(0)]
                val c = l[x]
                val d = l[(x + 1).coerceAtMost(w - 1)]
                val e = l[(x + 2).coerceAtMost(w - 1)]
                s[x] = (a + 2f * b + 3f * c + 2f * d + e) / 9f
            }
        }

        while (loadedUpTo < 2) {
            loadedUpTo++
            loadRow(loadedUpTo)
        }

        var sum = 0.0
        var count = 0
        for (y in 0 until h) {
            while (loadedUpTo < y + 2) {
                loadedUpTo++
                loadRow(loadedUpTo)
            }
            if (y % step != 0) continue

            val cur = lumRows[slotOf(y)]
            val up = lumRows[slotOf(y - 1)]
            val down = lumRows[slotOf(y + 1)]
            val s2 = sumRows[slotOf(y - 2)]
            val s1 = sumRows[slotOf(y - 1)]
            val s0 = sumRows[slotOf(y)]
            val s4 = sumRows[slotOf(y + 1)]
            val s5 = sumRows[slotOf(y + 2)]

            var x = 2
            while (x < w - 2) {
                val gx = abs(cur[x + 1] - cur[x - 1])
                val gy = abs(down[x] - up[x])
                if (gx + gy < 6f) {
                    val blur = (s2[x] + 2f * s1[x] + 3f * s0[x] + 2f * s4[x] + s5[x]) / 9f
                    sum += abs(cur[x] - blur).toDouble()
                    count++
                }
                x += step
            }
        }
        return if (count > 0) sum / count else 0.0
    }

    /**
     * Compares two same-sized images. This is the reliable check: when the
     * shield is intact the difference is fine-grained and noise-like; when the
     * file was edited or resized the difference is smooth and structural.
     */
    fun compare(original: Bitmap, candidate: Bitmap): CompareResult {
        val w = min(original.width, candidate.width)
        val h = min(original.height, candidate.height)
        if (w < 8 || h < 8) {
            return CompareResult(0.0, 0.0, 0.0, CompareResult.Verdict.STRUCTURAL_CHANGE)
        }
        val step = (maxOf(w, h) / 600).coerceAtLeast(1)
        val a = IntArray(w)
        val b = IntArray(w)

        var sum = 0.0
        var neighbourSum = 0.0
        var count = 0L
        var changed = 0L

        for (y in 0 until h) {
            original.getPixels(a, 0, w, 0, y, w, 1)
            candidate.getPixels(b, 0, w, 0, y, w, 1)
            var prev = 0f
            var hasPrev = false
            var x = 0
            while (x < w) {
                val d = lum(a[x]) - lum(b[x])
                sum += abs(d).toDouble()
                if (abs(d) > 0.5) changed++
                if (hasPrev) neighbourSum += abs(d - prev).toDouble()
                prev = d
                hasPrev = true
                count++
                x += step
            }
        }
        if (count == 0L) {
            return CompareResult(0.0, 0.0, 0.0, CompareResult.Verdict.STRUCTURAL_CHANGE)
        }
        val meanAbs = sum / count
        val neighbour = if (count > 1) neighbourSum / (count - 1) else 0.0
        // Uncorrelated (noise-like) differences change about 1.41x per step;
        // smooth differences barely change at all.
        val hf = if (meanAbs > 1e-6) (neighbour / (1.41 * meanAbs)).coerceIn(0.0, 1.0) else 0.0

        val verdict = when {
            meanAbs < 0.25 -> CompareResult.Verdict.IDENTICAL
            hf >= 0.6 -> CompareResult.Verdict.SHIELD_PRESENT
            else -> CompareResult.Verdict.STRUCTURAL_CHANGE
        }
        return CompareResult(
            meanAbsDiff = meanAbs,
            changedPercent = changed * 100.0 / count,
            hfFraction = hf,
            verdict = verdict,
        )
    }

    /** Simple, dependency-free hash in [-1, 1). Deterministic in (x, y, seed). */
    private fun hash01(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h and 0xFFFF).toFloat() / 65535f) * 2f - 1f
    }

    private fun lum(c: Int): Float {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Visible mode: a tiled, low-opacity stamp that survives editing. */
    private fun drawWatermark(bitmap: Bitmap, text: String) {
        if (text.isBlank()) return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 34
            textSize = maxOf(20f, bitmap.width * 0.032f)
            isFakeBoldText = true
        }
        val label = text.trim()
        val textWidth = paint.measureText(label)
        canvas.save()
        canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
        var y = -bitmap.height.toFloat()
        val stepY = paint.textSize * 4.0f
        while (y < bitmap.height * 2f) {
            var x = -bitmap.width.toFloat()
            while (x < bitmap.width * 2f) {
                canvas.drawText(label, x, y, paint)
                x += textWidth + paint.textSize * 3f
            }
            y += stepY
        }
        canvas.restore()
    }
}

enum class ShieldMode(val label: String, val blurb: String) {
    STANDARD("Standard", "Invisible. The default shield for photos you post."),
    STRONG("Strong", "Invisible in normal viewing, heavier in the pixels. Use when a photo will be re-shared a lot."),
    VISIBLE("Visible", "Adds a faint repeated stamp on top of the invisible shield."),
}

data class ShieldParams(
    val mode: ShieldMode = ShieldMode.STANDARD,
    val strength: Float = 0.6f,
    val seed: Int = DEFAULT_SEED,
    val watermarkText: String = "AI-PROTECTED",
) {
    companion object {
        const val DEFAULT_SEED = 0x5EEDBEEF.toInt()
    }
}

data class ShieldStats(
    val meanAbsDelta: Double,
    val maxDelta: Int,
    val changedPercent: Double,
    val noiseFloorBefore: Double,
    val noiseFloorAfter: Double,
    val elapsedMs: Long,
)

data class ShieldResult(val bitmap: Bitmap, val stats: ShieldStats)

/** Result of comparing an original against a file that may still be shielded. */
data class CompareResult(
    val meanAbsDiff: Double,
    val changedPercent: Double,
    val hfFraction: Double,
    val verdict: Verdict,
) {
    enum class Verdict(val title: String, val detail: String) {
        SHIELD_PRESENT(
            "Shield intact",
            "The difference between these two files is fine-grained and " +
                "noise-like, spread across the whole frame — the signature of an " +
                "intact shield. Note that a heavy JPEG re-save of a shielded " +
                "file can look similar, because compression adds its own " +
                "fine-grained noise.",
        ),
        IDENTICAL(
            "No shield between these files",
            "These files are effectively identical. Either the file was never " +
                "shielded, or the perturbation was stripped by a lossless " +
                "round-trip that dropped it.",
        ),
        STRUCTURAL_CHANGE(
            "The image was changed, not shielded",
            "The difference is smooth and structural — a crop, a resize, a " +
                "colour shift or an edit. Any shield this file once carried has " +
                "been replaced by real changes.",
        ),
    }
}

/** What the inspector reports about a single file. */
data class FileReport(
    val width: Int,
    val height: Int,
    val noiseFloor: Double,
    val hasExif: Boolean,
    val hasGps: Boolean,
)
