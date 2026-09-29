package com.example.a2uisample.agent

import androidx.a2ui.model.protocol.A2uiClientEventMessage
import com.example.a2uisample.BuildConfig
import kotlinx.coroutines.flow.Flow

/**
 * The agent side of the conversation. Each call returns a stream of A2UI v0.9.1 JSON messages
 * for the renderer.
 *
 * Two implementations: [FakeAgent] hard-codes the coffee flow in Kotlin and needs no setup.
 * [ClaudeAgent] asks an LLM to write the same UI from a plain-English prompt.
 */
interface Agent {
    /** The opening turn: create the surface and show the first step. */
    fun connect(): Flow<String>

    /** One agent turn in response to a user action. */
    fun respondTo(event: A2uiClientEventMessage): Flow<String>
}

/**
 * Picks the agent from `local.properties`: [ClaudeAgent] when `ANTHROPIC_API_KEY` is set,
 * otherwise [FakeAgent].
 */
fun createAgent(): Agent =
    if (BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) {
        ClaudeAgent(apiKey = BuildConfig.ANTHROPIC_API_KEY, model = BuildConfig.ANTHROPIC_MODEL)
    } else {
        FakeAgent()
    }
