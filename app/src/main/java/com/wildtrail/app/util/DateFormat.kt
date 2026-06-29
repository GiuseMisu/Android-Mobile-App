package com.wildtrail.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatHikeDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return df.format(Date(epochMillis))
}
