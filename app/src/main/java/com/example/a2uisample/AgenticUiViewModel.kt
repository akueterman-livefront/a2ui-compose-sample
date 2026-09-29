package com.example.a2uisample

import android.util.Log
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "A2UI"

class AgenticUiViewModel : ViewModel() {
    private val agent = FakeAgent()

    // Parses raw JSON strings into typed A2UI protocol messages.
    private val parser = A2uiMessageParser()

    // The Material 3 Basic Catalog. Media components need host-provided renderers; this sample
    // doesn't use them, so they're no-ops. ChoicePicker is swapped for a radio-button renderer.
    private val catalog =
        materialA2uiBasicCatalogV1(
            image = MaterialA2uiBasicCatalogV1Defaults.image { _, _, _, _, _ -> },
            video = MaterialA2uiBasicCatalogV1Defaults.video { _, _, _ -> },
            audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, _, _ -> },
            urlOpener = {},
            messageFormatter = { pattern, _, _ -> pattern },
            localeProvider = A2uiLocaleProvider.Default,
            choicePicker = RadioChoicePicker, // Specific override
        )

    private val processor = A2uiMessageProcessor(catalogs = listOf(catalog))

    val surfaces: StateFlow<List<A2uiSurfaceModel>> = processor.activeSurfaces

    init {
        // The processor only handles queued messages while this loop is running.
        viewModelScope.launch(Dispatchers.Default) {
            processor.collectMessages()
        }

        // Client -> agent: user actions and errors. A newer action cancels a reply still in progress.
        viewModelScope.launch {
            processor.outboundEvents.collectLatest(::onOutboundMessage)
        }

        // Agent -> client: the message stream.
        viewModelScope.launch {
            agent.connect().collect(::onAgentMessage)
        }
    }

    private fun onAgentMessage(json: String) {
        Log.d(TAG, "agent -> client:\n$json")
        processor.processInput(parser, json)
    }

    private suspend fun onOutboundMessage(message: A2uiClientToServerMessage) {
        Log.d(TAG, "client -> agent: $message")
        when (message) {
            is A2uiClientEventMessage -> {
                agent.respondTo(message).collect(::onAgentMessage)
            }

            is A2uiClientErrorMessage -> {
                Log.w(
                    TAG,
                    "Renderer reported an error: ${message.message}",
                )
            }
        }
    }
}
