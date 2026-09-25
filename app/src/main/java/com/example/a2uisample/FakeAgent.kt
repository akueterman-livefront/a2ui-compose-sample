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
        "greet" -> {
            val name = (event.context["name"] as? String).orEmpty().ifBlank { "stranger" }
            listOf(greetingUpdate("Hello, $name! This text came back from the agent."))
        }
        else -> emptyList()
    }

    private companion object {
        const val STREAM_DELAY_MS = 800L
        const val SURFACE_ID = "hello_surface"

        // The Basic Catalog ID the AndroidX renderer registers. The agent must reference the same ID.
        const val CATALOG_ID = A2uiBasicCatalogV1.CatalogId

        val CREATE_SURFACE = """
            {
              "version": "v0.9.1",
              "createSurface": { "surfaceId": "$SURFACE_ID", "catalogId": "$CATALOG_ID" }
            }
        """.trimIndent()

        // A flat adjacency list: parents reference children by ID.
        val UPDATE_COMPONENTS = """
            {
              "version": "v0.9.1",
              "updateComponents": {
                "surfaceId": "$SURFACE_ID",
                "components": [
                  { "id": "root", "component": "Column",
                    "children": ["title", "name_field", "greet_button", "greeting"] },
                  { "id": "title", "component": "Text", "text": "Agent-rendered UI", "variant": "h2" },
                  { "id": "name_field", "component": "TextField",
                    "label": "Your name", "value": { "path": "/form/name" } },
                  { "id": "greet_label", "component": "Text", "text": "Say hello" },
                  { "id": "greet_button", "component": "Button", "child": "greet_label",
                    "variant": "primary",
                    "action": { "event": { "name": "greet",
                      "context": { "name": { "path": "/form/name" } } } } },
                  { "id": "greeting", "component": "Text", "text": { "path": "/greeting" } }
                ]
              }
            }
        """.trimIndent()

        val UPDATE_DATA_MODEL = """
            {
              "version": "v0.9.1",
              "updateDataModel": {
                "surfaceId": "$SURFACE_ID",
                "value": { "form": { "name": "" }, "greeting": "Type a name and tap the button." }
              }
            }
        """.trimIndent()

        fun greetingUpdate(text: String): String = """
            {
              "version": "v0.9.1",
              "updateDataModel": {
                "surfaceId": "$SURFACE_ID",
                "path": "/greeting",
                "value": ${JSONObject.quote(text)}
              }
            }
        """.trimIndent()
    }
}
