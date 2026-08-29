package com.aituber.poc.viseme

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight, model-free vowel classifier for the first real-time viseme MVP.
 *
 * This is intentionally a latency/UX probe rather than a production phoneme model.
 * It estimates F1/F2-ish spectral-envelope peaks from 16 kHz mono PCM and maps them
 * onto coarse Mandarin-friendly A/E/I/O/U mouth-shape prototypes.
 */
class MandarinVisemeClassifier(
    private val sampleRate: Int = 16_000,
    private val fftSize: Int = 1024,
    private val silenceRms: Double = 0.018,
) {
    enum class Viseme { REST, A, E, I, O, U }

    data class Result(
        val viseme: Viseme,
        val rawViseme: Viseme,
        val rms: Double,
        val f1Hz: Double?,
        val f2Hz: Double?,
        val confidence: Double,
        val processingMs: Double,
    )

    private data class Prototype(val viseme: Viseme, val f1: Double, val f2: Double)

    private val prototypes = listOf(
        Prototype(Viseme.A, 780.0, 1250.0),
        Prototype(Viseme.E, 500.0, 1650.0),
        Prototype(Viseme.I, 300.0, 2450.0),
        Prototype(Viseme.O, 500.0, 950.0),
        Prototype(Viseme.U, 340.0, 800.0),
    )

    private val recent = ArrayDeque<Viseme>()
    private var stableViseme = Viseme.REST
    private var silenceFrames = 0

    fun reset() {
        recent.clear()
        stableViseme = Viseme.REST
        silenceFrames = 0
    }

    fun classify(samples: ShortArray, count: Int = samples.size): Result {
        val startNs = System.nanoTime()
        val validCount = min(count, samples.size)
        if (validCount <= 0) {
            return finish(Viseme.REST, Viseme.REST, 0.0, null, null, 0.0, startNs)
        }

        var sumSquares = 0.0
        for (i in 0 until validCount) {
            val x = samples[i] / 32768.0
            sumSquares += x * x
        }
        val rms = sqrt(sumSquares / validCount)
        if (rms < silenceRms) {
            silenceFrames += 1
            if (silenceFrames >= 2) {
                recent.clear()
                stableViseme = Viseme.REST
            }
            return finish(stableViseme, Viseme.REST, rms, null, null, 1.0, startNs)
        }
        silenceFrames = 0

        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        var previous = 0.0
        val windowCount = min(validCount, fftSize)
        for (i in 0 until windowCount) {
            val x = samples[i] / 32768.0
            val emphasized = x - 0.97 * previous
            previous = x
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / max(1, windowCount - 1))
            real[i] = emphasized * window
        }

        fft(real, imag)
        val half = fftSize / 2
        val magnitude = DoubleArray(half)
        for (i in 0 until half) {
            magnitude[i] = ln(1e-9 + hypot(real[i], imag[i]))
        }

        // Heavy smoothing suppresses pitch harmonics and exposes a rough vocal-tract envelope.
        val smoothed = movingAverage(magnitude, radius = 8)
        val f1 = peakFrequency(smoothed, 220.0, 1000.0)
        val f2Lower = max(720.0, f1 + 260.0)
        val f2 = peakFrequency(smoothed, f2Lower, 3000.0)

        val scored = prototypes.map { prototype ->
            val df1 = (f1 - prototype.f1) / 360.0
            val df2 = (f2 - prototype.f2) / 850.0
            prototype to sqrt(df1 * df1 + df2 * df2)
        }.sortedBy { it.second }

        val raw = scored.first().first.viseme
        val bestDistance = scored.first().second
        val secondDistance = scored.getOrNull(1)?.second ?: bestDistance + 1.0
        val margin = (secondDistance - bestDistance).coerceAtLeast(0.0)
        val confidence = (0.35 + margin * 0.8).coerceIn(0.0, 1.0)

        push(raw)
        val stable = majorityVote()
        stableViseme = stable
        return finish(stable, raw, rms, f1, f2, confidence, startNs)
    }

    internal fun classifyFormantsForTest(f1Hz: Double, f2Hz: Double): Viseme {
        return prototypes.minBy { prototype ->
            val df1 = (f1Hz - prototype.f1) / 360.0
            val df2 = (f2Hz - prototype.f2) / 850.0
            df1 * df1 + df2 * df2
        }.viseme
    }

    private fun push(viseme: Viseme) {
        recent.addLast(viseme)
        while (recent.size > 3) recent.removeFirst()
    }

    private fun majorityVote(): Viseme {
        if (recent.isEmpty()) return Viseme.REST
        val counts = recent.groupingBy { it }.eachCount()
        val bestCount = counts.values.maxOrNull() ?: 1
        val candidates = counts.filterValues { it == bestCount }.keys
        return when {
            stableViseme in candidates -> stableViseme
            else -> recent.lastOrNull { it in candidates } ?: recent.last()
        }
    }

    private fun finish(
        viseme: Viseme,
        raw: Viseme,
        rms: Double,
        f1: Double?,
        f2: Double?,
        confidence: Double,
        startNs: Long,
    ): Result {
        val ms = (System.nanoTime() - startNs) / 1_000_000.0
        return Result(viseme, raw, rms, f1, f2, confidence, ms)
    }

    private fun peakFrequency(spectrum: DoubleArray, lowHz: Double, highHz: Double): Double {
        val lowBin = ((lowHz * fftSize) / sampleRate).toInt().coerceIn(1, spectrum.lastIndex)
        val highBin = ((highHz * fftSize) / sampleRate).toInt().coerceIn(lowBin, spectrum.lastIndex)
        var bestBin = lowBin
        var bestValue = Double.NEGATIVE_INFINITY
        for (bin in lowBin..highBin) {
            if (spectrum[bin] > bestValue) {
                bestValue = spectrum[bin]
                bestBin = bin
            }
        }
        return bestBin.toDouble() * sampleRate / fftSize
    }

    private fun movingAverage(values: DoubleArray, radius: Int): DoubleArray {
        val out = DoubleArray(values.size)
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        for (i in values.indices) {
            val left = max(0, i - radius)
            val right = min(values.lastIndex, i + radius)
            out[i] = (prefix[right + 1] - prefix[left]) / (right - left + 1)
        }
        return out
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenReal = cos(angle)
            val wLenImag = sin(angle)
            var i = 0
            while (i < n) {
                var wReal = 1.0
                var wImag = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uReal = real[i + k]
                    val uImag = imag[i + k]
                    val vReal = real[i + k + half] * wReal - imag[i + k + half] * wImag
                    val vImag = real[i + k + half] * wImag + imag[i + k + half] * wReal
                    real[i + k] = uReal + vReal
                    imag[i + k] = uImag + vImag
                    real[i + k + half] = uReal - vReal
                    imag[i + k + half] = uImag - vImag
                    val nextReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextReal
                }
                i += len
            }
            len = len shl 1
        }
    }
}
