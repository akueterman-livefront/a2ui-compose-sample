package com.example.a2uisample.agent

import android.util.Log
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiException
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.example.a2uisample.TAG
import com.example.a2uisample.logLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A brew troubleshooter backed by Claude. The LLM designs each screen itself, guided by
 * [troubleshooterPrompt], which includes [catalogSchema], the JSON Schema of the catalog the app
 * renders with.
 *
 * Unlike [FakeAgent], it keeps the session's conversation: a diagnosis builds on what the user
 * said earlier and on the recipes already tried. "Start over" clears it.
 */
class ClaudeAgent(
    apiKey: String,
    private val model: String,
    catalogSchema: String,
) : Agent {
    private val client =
        AnthropicOkHttpClient
            .builder()
            .apiKey(apiKey)
            .build()
            .async()

    // The same parser the renderer uses, here to reject a bad reply before it reaches the UI.
    private val parser = A2uiMessageParser()

    private val examples = TroubleshooterExamples()

    // Marked cacheable, since it's identical on every request. Logged once, here, for that reason.
    private val systemPrompt =
        TextBlockParam
            .builder()
            .text(
                troubleshooterPrompt(catalogSchema, examples).trim().also {
                    logLong("System prompt (${it.length} chars):\n$it")
                },
            ).cacheControl(CacheControlEphemeral.builder().build())
            .build()

    // Earlier turns of this session, as (event, reply) pairs. Only valid replies are kept.
    private val history = ArrayDeque<Pair<String, String>>()

    override fun connect(): Flow<String> =
        flow {
            // The surface and catalog are app configuration, so the app creates the surface itself.
            emit(createSurface())
            showStartScreen()
        }

    override fun respondTo(event: A2uiClientEventMessage): Flow<String> =
        flow {
            if (event.type == "startOver") {
                history.clear()
                showStartScreen()
                return@flow
            }
            emit(setData("/prompt", "Thinking…"))
            takeTurn(JSONObject().put("name", event.type).put("context", JSONObject(event.context)))
        }

    /** The opening screen never changes, so it comes straight from the example, not a request. */
    private suspend fun FlowCollector<String>.showStartScreen() {
        val messages = JSONArray(examples.start)
        repeat(messages.length()) { emit(messages.getJSONObject(it).toString(2)) }
    }

    /** Asks Claude for this turn's UI and emits it, or emits an error prompt if the call fails. */
    private suspend fun FlowCollector<String>.takeTurn(event: JSONObject) {
        val messages =
            try {
                askForUi(event.toString())
            } catch (e: AnthropicServiceException) {
                Log.w(TAG, "Claude API error ${e.statusCode()}", e)
                listOf(setData("/prompt", "Claude API error ${e.statusCode()}. $CHECK_SETUP"))
            } catch (e: AnthropicIoException) {
                Log.w(TAG, "Couldn't reach Claude", e)
                listOf(setData("/prompt", "Couldn't reach Claude. Check your connection."))
            }
        messages.forEach { emit(it) }
    }

    /** One request, plus one retry that tells Claude what was wrong if the reply is invalid. */
    private suspend fun askForUi(event: String): List<String> {
        val request =
            MessageCreateParams
                .builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(listOf(systemPrompt))
                .apply {
                    history.forEach { (pastEvent, pastReply) ->
                        addUserMessage(pastEvent)
                        addAssistantMessage(pastReply)
                    }
                }.addUserMessage(event)
                .build()
        Log.d(TAG, "client -> Claude: system prompt + ${history.size} earlier turns + $event")
        val reply = complete(request)
        val result = parseReply(reply)
        if (result.isSuccess) {
            remember(event, reply)
            return result.getOrThrow()
        }

        val error = result.exceptionOrNull()?.message
        Log.w(TAG, "Invalid reply ($error), retrying once")
        val retry =
            request
                .toBuilder()
                .addAssistantMessage(reply)
                .addUserMessage(
                    "Your previous reply was invalid: $error. " +
                        "Reply again with only the corrected JSON array.",
                ).build()
        val retryReply = complete(retry)
        return parseReply(retryReply)
            .onSuccess { remember(event, retryReply) }
            .getOrElse {
                Log.w(TAG, "Retry was also invalid (${it.message})")
                listOf(setData("/prompt", "Sorry, something went wrong. Tap the button again."))
            }
    }

    /** Keeps the last [MAX_HISTORY] turns, so a long session's cost per turn stays bounded. */
    private fun remember(
        event: String,
        reply: String,
    ) {
        history.addLast(event to reply)
        if (history.size > MAX_HISTORY) history.removeFirst()
    }

    /** Sends [request] and returns the reply text. Cancelling the coroutine cancels the call. */
    private suspend fun complete(request: MessageCreateParams): String {
        val response = client.messages().create(request).await()
        val usage = response.usage()
        Log.d(
            TAG,
            "Claude tokens: in=${usage.inputTokens()} " +
                "(cache read ${
                    usage.cacheReadInputTokens().orElse(0)
                }), out=${usage.outputTokens()}",
        )
        return response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    }

    /** Splits the reply into individual A2UI messages, failing if any of them is invalid. */
    private fun parseReply(reply: String): Result<List<String>> =
        try {
            // Models often wrap JSON in a ```json fence despite the prompt.
            val json =
                reply
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
            val array = JSONArray(json.trim())
            Result.success(
                List(array.length()) { i ->
                    array.getJSONObject(i).toString().also(parser::parse)
                },
            )
        } catch (e: JSONException) {
            Result.failure(e)
        } catch (e: A2uiException) {
            Result.failure(e)
        }

    private companion object {
        // Generous so a full component tree is never cut off. You're billed for tokens used, not this.
        const val MAX_TOKENS = 16_000L
        const val MAX_HISTORY = 6
        const val CHECK_SETUP = "Check ANTHROPIC_API_KEY in local.properties and logcat."
    }
}
