package app.samloader.common.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Format {
    private fun twoDecimals(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0.00"
        val neg = value < 0
        val v = kotlin.math.abs(value)
        val rounded = round(v * 100.0) / 100.0
        val intPart = rounded.toLong()
        val frac = round((rounded - intPart) * 100.0).toInt()
        val base = intPart.toString() + "." + frac.toString().padStart(2, '0')
        return if (neg) "-$base" else base
    }

    fun bytesPerSec(bps: Double): String {
        if (bps.isNaN() || bps.isInfinite() || bps < 0) return "0 B/s"
        val units = arrayOf("B/s", "KiB/s", "MiB/s", "GiB/s")
        var v = bps
        var u = 0
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024
            u++
        }
        return twoDecimals(v) + " " + units[u]
    }

    fun size(bytes: Long): String {
        val units = arrayOf("B", "KiB", "MiB", "GiB")
        var v = abs(bytes).toDouble()
        var u = 0
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024
            u++
        }
        val sign = if (bytes < 0) "-" else ""
        return sign + twoDecimals(v) + " " + units[u]
    }

    fun eta(secondsRemaining: Long): String {
        if (secondsRemaining <= 0) return "00:00:00"
        val d: Duration = secondsRemaining.seconds
        val h = d.inWholeHours
        val m = (d.inWholeMinutes % 60)
        val s = (d.inWholeSeconds % 60)
        return h.toString().padStart(2, '0') + ":" +
                m.toString().padStart(2, '0') + ":" +
                s.toString().padStart(2, '0')
    }

    fun compositeProgress(done: Long, total: Long, bps: Double?, etaSec: Long?, threads: Int?): String {
        val parts = mutableListOf<String>()
        if (total > 0) {
            parts += size(done) + "/" + size(total)
            val pct = (min(max(done, 0L), max(total, 1L)).toDouble() / max(total, 1L).toDouble()) * 100.0
            parts += twoDecimals(pct) + "%"
        } else {
            parts += size(done)
        }
        if (bps != null) parts += bytesPerSec(bps)
        if (etaSec != null && etaSec >= 0) parts += ("ETA " + eta(etaSec))
        if (threads != null && threads > 0) parts += ("T=" + threads)
        return parts.joinToString("  •  ")
    }
}