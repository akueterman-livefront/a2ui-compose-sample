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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "A2UI"

/**
 * An agent backed by Claude. The LLM writes the A2UI messages itself, following the flow in
 * [COFFEE_AGENT_PROMPT].
 *
 * Like [FakeAgent], it keeps no state between turns. Each request is the system prompt plus the
 * one event that just happened. The event context already carries every selection, so there is
 * no chat history to send and the cost per turn stays flat.
 */
class ClaudeAgent(
    apiKey: String,
    private val model: String,
) : Agent {
    private val client =
        AnthropicOkHttpClient
            .builder()
            .apiKey(apiKey)
            .build()
            .async()

    // The same parser the renderer uses, here to reject a bad reply before it reaches the UI.
    private val parser = A2uiMessageParser()

    // Marked cacheable, since it's identical on every request.
    private val systemPrompt =
        TextBlockParam
            .builder()
            .text(COFFEE_AGENT_PROMPT.trim())
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()

    override fun connect(): Flow<String> =
        flow {
            // The surface and catalog are app configuration, so the app creates the surface itself.
            emit(createSurface())
            // A title and prompt right away, so the first request's wait or error has somewhere to show.
            emit(setData(path = null, value = JSONObject().put("prompt", "Connecting to Claude…")))
            emit(placeholderScreen())
            takeTurn(JSONObject().put("name", "start"))
        }

    override fun respondTo(event: A2uiClientEventMessage): Flow<String> =
        flow {
            emit(setData("/prompt", "Thinking…"))
            takeTurn(JSONObject().put("name", event.type).put("context", JSONObject(event.context)))
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
                .addUserMessage(event)
                .build()
        val reply = complete(request)
        val result = parseReply(reply)
        if (result.isSuccess) return result.getOrThrow()

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
        return parseReply(complete(retry)).getOrElse {
            Log.w(TAG, "Retry was also invalid (${it.message})")
            listOf(setData("/prompt", "Sorry, something went wrong. Tap the button again."))
        }
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

    /** Just the title and the `/prompt` text, the same ids the LLM uses for them. */
    private fun placeholderScreen(): String =
        message(
            "updateComponents",
            "components" to
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "root")
                            .put("component", "Column")
                            .put("children", JSONArray().put("title").put("prompt")),
                    ).put(
                        JSONObject()
                            .put("id", "title")
                            .put("component", "Text")
                            .put("text", "Coffee order")
                            .put("variant", "h2"),
                    ).put(
                        JSONObject()
                            .put("id", "prompt")
                            .put("component", "Text")
                            .put("text", JSONObject().put("path", "/prompt")),
                    ),
        )

    private companion object {
        // Generous so a full component tree is never cut off. You're billed for tokens used, not this.
        const val MAX_TOKENS = 16_000L
        const val CHECK_SETUP = "Check ANTHROPIC_API_KEY in local.properties and logcat."
    }
}
