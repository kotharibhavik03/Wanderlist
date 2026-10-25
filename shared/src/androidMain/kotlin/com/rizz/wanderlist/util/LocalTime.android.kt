package com.rizz.wanderlist.util

import java.util.TimeZone

actual fun localUtcOffsetMillis(epochMillis: Long): Long = TimeZone.getDefault().getOffset(epochMillis).toLong()
