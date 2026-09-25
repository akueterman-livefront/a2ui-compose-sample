package com.example.a2uisample

import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stands in for a real AI agent. It streams hard-coded A2UI v0.9.1 JSON messages (exactly what an
 * LLM-backed agent would send over the wire) and answers the one event the UI can emit.
 */
class FakeAgent {

    /** The initial stream: create the surface, send its components, then fill in its data. */
    fun connect(): Flow<String> = flow {
        emit(CREATE_SURFACE)
        delay(STREAM_DELAY_MS.milliseconds) // The surface shows its loading state until "root" arrives.
        emit(UPDATE_COMPONENTS)
        delay(STREAM_DELAY_MS.milliseconds)
        emit(UPDATE_DATA_MODEL)
    }

    /** Responds to user actions dispatched from the surface. */
    fun respondTo(event: A2uiClientEventMessage): List<String> = when (event.type) {
        "placeOrder" -> listOf(summaryUpdate(describeOrder(event.context)))
        else -> emptyList()
    }

    // The event context arrives with each data binding already resolved to its current value.
    private fun describeOrder(context: Map<String, Any?>): String {
        val size = (context["size"] as? List<*>)?.firstOrNull()?.toString() ?: "medium"
        val extras = (context["extras"] as? List<*>).orEmpty().map { EXTRA_LABELS[it] ?: it }
        val sweetness = (context["sweetness"] as? Number)?.toInt() ?: 0
        val toGo = context["toGo"] == true

        val withExtras = if (extras.isEmpty()) "" else " with ${extras.joinToString()}"
        val where = if (toGo) "to go" else "for here"
        return "Order placed: ${size.replaceFirstChar(Char::uppercase)} coffee$withExtras, " +
            "sweetness $sweetness/5, $where."
    }

    private companion object {
        const val STREAM_DELAY_MS = 800L
        const val SURFACE_ID = "coffee_order"

        // The Basic Catalog ID the AndroidX renderer registers. The agent must reference the same ID.
        const val CATALOG_ID = A2uiBasicCatalogV1.CatalogId

        val EXTRA_LABELS = mapOf("oat" to "oat milk", "shot" to "an extra shot", "vanilla" to "vanilla")

        val CREATE_SURFACE = """
            {
              "version": "v0.9.1",
              "createSurface": { "surfaceId": "$SURFACE_ID", "catalogId": "$CATALOG_ID" }
            }
        """.trimIndent()

        // A flat adjacency list: parents reference children by ID. Every input binds to a path in
        // the data model, and the button's event context reads those same paths back.
        val UPDATE_COMPONENTS = """
            {
              "version": "v0.9.1",
              "updateComponents": {
                "surfaceId": "$SURFACE_ID",
                "components": [
                  { "id": "root", "component": "Column",
                    "children": ["title", "size_picker", "extras_picker", "sweetness_slider",
                                 "to_go_checkbox", "divider", "order_button", "summary"] },
                  { "id": "title", "component": "Text", "text": "Build your coffee", "variant": "h2" },
                  { "id": "size_picker", "component": "ChoicePicker", "label": "Size",
                    "variant": "mutuallyExclusive",
                    "options": [
                      { "label": "Small", "value": "small" },
                      { "label": "Medium", "value": "medium" },
                      { "label": "Large", "value": "large" }
                    ],
                    "value": { "path": "/order/size" } },
                  { "id": "extras_picker", "component": "ChoicePicker", "label": "Extras",
                    "variant": "multipleSelection", "displayStyle": "chips",
                    "options": [
                      { "label": "Oat milk", "value": "oat" },
                      { "label": "Extra shot", "value": "shot" },
                      { "label": "Vanilla", "value": "vanilla" }
                    ],
                    "value": { "path": "/order/extras" } },
                  { "id": "sweetness_slider", "component": "Slider", "label": "Sweetness",
                    "min": 0, "max": 5, "value": { "path": "/order/sweetness" } },
                  { "id": "to_go_checkbox", "component": "CheckBox", "label": "To go",
                    "value": { "path": "/order/toGo" } },
                  { "id": "divider", "component": "Divider" },
                  { "id": "order_label", "component": "Text", "text": "Place order" },
                  { "id": "order_button", "component": "Button", "child": "order_label",
                    "variant": "primary",
                    "action": { "event": { "name": "placeOrder", "context": {
                      "size": { "path": "/order/size" },
                      "extras": { "path": "/order/extras" },
                      "sweetness": { "path": "/order/sweetness" },
                      "toGo": { "path": "/order/toGo" }
                    } } } },
                  { "id": "summary", "component": "Text", "text": { "path": "/summary" } }
                ]
              }
            }
        """.trimIndent()

        val UPDATE_DATA_MODEL = """
            {
              "version": "v0.9.1",
              "updateDataModel": {
                "surfaceId": "$SURFACE_ID",
                "value": {
                  "order": { "size": ["medium"], "extras": [], "sweetness": 2, "toGo": false },
                  "summary": "Pick your options and place the order."
                }
              }
            }
        """.trimIndent()

        fun summaryUpdate(text: String): String = """
            {
              "version": "v0.9.1",
              "updateDataModel": {
                "surfaceId": "$SURFACE_ID",
                "path": "/summary",
                "value": ${JSONObject.quote(text)}
              }
            }
        """.trimIndent()
    }
}
