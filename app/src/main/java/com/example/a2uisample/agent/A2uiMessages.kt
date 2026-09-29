package com.example.a2uisample.agent

import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import org.json.JSONObject

// Builders for the few server -> client messages both agents send themselves.

/** The one surface this app renders. */
internal const val SURFACE_ID = "coffee_order"

// The Basic Catalog ID the AndroidX renderer registers. The agent must reference the same ID.
internal const val CATALOG_ID = A2uiBasicCatalogV1.CatalogId

internal fun createSurface(): String = message("createSurface", "catalogId" to CATALOG_ID)

/** An `updateDataModel` message that sets [value] at [path], or replaces the whole model. */
internal fun setData(
    path: String?,
    value: Any,
): String =
    message(
        "updateDataModel",
        *listOfNotNull(
            path?.let { "path" to it },
            "value" to value,
        ).toTypedArray(),
    )

/** Wraps a message body in the v0.9.1 envelope. */
internal fun message(
    kind: String,
    vararg body: Pair<String, Any>,
): String {
    val payload = JSONObject().put("surfaceId", SURFACE_ID)
    body.forEach { (k, v) -> payload.put(k, v) }
    return JSONObject().put("version", "v0.9.1").put(kind, payload).toString(2)
}
