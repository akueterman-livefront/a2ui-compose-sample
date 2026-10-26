package com.example.a2uisample

import android.util.Log

/** The logcat tag for all A2UI traffic: `adb logcat -s A2UI`. */
internal const val TAG = "A2UI"

// Under logcat's ~4K limit per entry, with room for the header.
private const val LOG_CHUNK = 3_500

/** Logs [text] in pieces, split at line breaks where possible, since logcat truncates long entries. */
internal fun logLong(text: String) {
    var start = 0
    while (start < text.length) {
        var end = minOf(start + LOG_CHUNK, text.length)
        if (end < text.length) {
            text.lastIndexOf('\n', end).takeIf { it > start }?.let { end = it + 1 }
        }
        Log.d(TAG, text.substring(start, end))
        start = end
    }
}
