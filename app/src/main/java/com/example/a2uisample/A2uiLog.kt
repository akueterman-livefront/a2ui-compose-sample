package com.example.a2uisample

import android.util.Log
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

/**
 * Formats a client message as indented JSON so it reads like the agent's messages in the log.
 * The library has no public serializer for these, so this mirrors the properties, not the wire format.
 */
internal fun A2uiClientToServerMessage.toLogString(): String {
    val json =
        when (this) {
            is A2uiClientEventMessage -> {
                JSONObject()
                    .put("type", type)
                    .put("surfaceId", surfaceId)
                    .put("componentId", componentId)
                    .put("timestamp", utcFormat().format(Date(timestamp)))
                    .put("context", JSONObject(context))
                    .put("clientDataModel", clientDataModel?.let { JSONObject(it.toPayloadMap()) } ?: JSONObject.NULL)
            }

            is A2uiClientErrorMessage -> {
                JSONObject()
                    .put("code", code)
                    .put("surfaceId", surfaceId)
                    .put("message", message)
                    .put("context", JSONObject(context))
            }

            else -> {
                return toString()
            }
        }
    return "${this::class.simpleName}\n${json.toString(2)}"
}

// SimpleDateFormat isn't thread-safe, and java.time needs API 26 (minSdk is 24).
private fun utcFormat() =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
