package com.rizz.wanderlist.util

import kotlin.math.round

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** "12 Mar 2026" from UTC-midnight epoch millis (Howard Hinnant's civil-from-days). */
fun formatDate(epochMillis: Long): String {
    val z = epochMillis.floorDiv(86_400_000L) + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "$day ${MONTHS[(month - 1).toInt()]} $year"
}

/**
 * "12 Mar 2026" for a moment in time (e.g. when something was saved), on the device's calendar.
 * [formatDate] is for dates stored as UTC midnight; a moment needs the local offset first, or
 * anything saved in India before 5:30 am would show the day before.
 */
fun formatLocalDate(epochMillis: Long): String = formatDate(epochMillis + localUtcOffsetMillis(epochMillis))

/** How far the device's time zone is ahead of UTC at [epochMillis] (daylight saving included). */
expect fun localUtcOffsetMillis(epochMillis: Long): Long

fun formatCoordinates(latitude: Double, longitude: Double): String {
    fun r(v: Double) = (round(v * 100_000) / 100_000).toString()
    return "${r(latitude)}, ${r(longitude)}"
}