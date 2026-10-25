package org.mz.killrs.shared.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** Formats a dollar amount consistently across mock checkout and saved orders. */
fun formatUsd(amount: Double): String {
    if (!amount.isFinite()) return "—"
    val cents = (abs(amount) * 100).roundToLong()
    val sign = if (amount < 0 && cents != 0L) "-" else ""
    return "${sign}\$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
