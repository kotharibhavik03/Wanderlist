package com.rizz.wanderlist.util

/** "1 place", "3 places". Pass [plural] for irregular words, e.g. "person" / "people". */
fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
