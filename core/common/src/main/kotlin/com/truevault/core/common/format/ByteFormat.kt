package com.truevault.core.common.format

import java.util.Locale
import kotlin.math.abs

/**
 * Human-readable byte sizes using SI units, matching what Android's own storage UI shows.
 *
 * Pure and locale-aware so it can be unit tested without Android resources.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (abs(bytes) < 1000) return "$bytes B"

    val units = listOf("kB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var unitIndex = -1

    while (abs(value) >= 1000 && unitIndex < units.lastIndex) {
        value /= 1000.0
        unitIndex++
    }

    val pattern = if (abs(value) >= 100) "%.0f %s" else "%.1f %s"
    return String.format(locale, pattern, value, units[unitIndex])
}
