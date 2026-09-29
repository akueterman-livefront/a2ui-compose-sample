package com.example.a2uisample

import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stands in for an LLM-backed agent that walks the user through a coffee order, one step at a time.
 *
 * Like a real agent, it keeps no state between turns. Each button's event context carries the
 * user's current selections, and the agent decides from those alone which steps to show next. It
 * then re-sends the whole component tree for those steps, the way an LLM regenerates its output.
 */
class FakeAgent {
    /** The opening turn: create the surface, seed the data model, and ask the first question. */
    fun connect(): Flow<String> =
        flow {
            emit(createSurface())
            delay(STREAM_DELAY_MS.milliseconds)
            emit(setData(path = null, value = initialData()))
            emit(render(listOf(Step.CoffeeType), Order.EMPTY))
        }

    /** One agent turn in response to a user action. */
    fun respondTo(event: A2uiClientEventMessage): Flow<String> =
        flow {
            emit(setData("/prompt", "Thinking…"))
            delay(THINKING_DELAY_MS.milliseconds)

            val order = Order.from(event.context)
            if (order.type == null) {
                emit(setData("/prompt", "Pick hot or cold first."))
                return@flow
            }

            when (event.type) {
                "chooseType" -> {
                    // New type, new add-in options: clear any add-ins picked for the old type.
                    emit(setData("/order/addIns", JSONArray()))
                    emit(setData("/prompt", "${order.typeLabel} coffee, nice. Any add-ins?"))
                    emit(render(listOf(Step.CoffeeType, Step.AddIns), order))
                }

                "chooseAddIns" -> {
                    if (order.wantsSweetener) {
                        emit(setData("/prompt", "How sweet should it be?"))
                        emit(render(listOf(Step.CoffeeType, Step.AddIns, Step.Sweetness), order))
                    } else {
                        emit(setData("/prompt", CHECKOUT_PROMPT))
                        emit(render(listOf(Step.CoffeeType, Step.AddIns, Step.Checkout), order))
                    }
                }

                "chooseSweetness" -> {
                    emit(setData("/prompt", CHECKOUT_PROMPT))
                    emit(render(order.stepsThroughCheckout(), order))
                }

                "placeOrder" -> {
                    emit(setData("/summary", order.describe()))
                    emit(setData("/prompt", "Thanks! Your order is in."))
                    emit(render(order.stepsThroughCheckout() + Step.Summary, order))
                }
            }
        }

    /** The user's selections, read from an event context where every binding is already resolved. */
    private data class Order(
        val type: String?,
        val addIns: List<String>,
        val sweetness: Int,
        val toGo: Boolean,
    ) {
        val typeLabel: String get() = if (type == HOT) "Hot" else "Cold"
        val wantsSweetener: Boolean get() = SWEETENER in addIns

        fun stepsThroughCheckout(): List<Step> =
            listOfNotNull(
                Step.CoffeeType,
                Step.AddIns,
                Step.Sweetness.takeIf { wantsSweetener },
                Step.Checkout,
            )

        fun describe(): String {
            val labels =
                addIns.map { id ->
                    val label = addInOptions(type).getValue(id).lowercase()
                    if (id == SWEETENER) "$label ($sweetness/5)" else label
                }
            val withAddIns = if (labels.isEmpty()) "" else " with ${labels.joinToString(" and ")}"
            val where = if (toGo) "to go" else "for here"
            return "Order placed: $typeLabel coffee$withAddIns, $where."
        }

        companion object {
            val EMPTY = Order(type = null, addIns = emptyList(), sweetness = 0, toGo = false)

            fun from(context: Map<String, Any?>): Order {
                val type = (context["type"] as? List<*>)?.firstOrNull()?.toString()
                val validAddIns = addInOptions(type).keys
                return Order(
                    type = type,
                    // Drop add-ins left over from the other coffee type.
                    addIns =
                        (context["addIns"] as? List<*>).orEmpty().map(Any?::toString).filter {
                            it in
                                validAddIns
                        },
                    sweetness = (context["sweetness"] as? Number)?.toInt() ?: 0,
                    toGo = context["toGo"] == true,
                )
            }
        }
    }

    /** Each step is a group of components the agent can add to the root Column. */
    private enum class Step {
        CoffeeType,
        AddIns,
        Sweetness,
        Checkout,
        Summary,
    }

    private companion object {
        const val STREAM_DELAY_MS = 800L
        const val THINKING_DELAY_MS = 600L
        const val SURFACE_ID = "coffee_order"

        // The Basic Catalog ID the AndroidX renderer registers. The agent must reference the same ID.
        const val CATALOG_ID = A2uiBasicCatalogV1.CatalogId

        const val HOT = "hot"
        const val COLD = "cold"
        const val SWEETENER = "sweetener"
        const val CHECKOUT_PROMPT = "Last step: for here or to go?"

        fun addInOptions(type: String?): Map<String, String> =
            when (type) {
                HOT -> mapOf("halfAndHalf" to "Half & half", "milk" to "Milk")
                COLD -> mapOf(SWEETENER to "Sweetener", "ice" to "Ice")
                else -> emptyMap()
            }

        fun initialData(): JSONObject =
            JSONObject()
                .put("prompt", "Hi! Hot or cold coffee today?")
                .put(
                    "order",
                    JSONObject()
                        .put("type", JSONArray())
                        .put("addIns", JSONArray())
                        .put("sweetness", 2)
                        .put("toGo", false),
                ).put("summary", "")

        /** An `updateComponents` message holding the root Column plus every component for [steps]. */
        fun render(
            steps: List<Step>,
            order: Order,
        ): String {
            val components =
                mutableListOf(
                    component(
                        "root",
                        "Column",
                        "children" to
                            JSONArray(
                                listOf(
                                    "title",
                                    "prompt",
                                ) + steps.flatMap(::componentIds),
                            ),
                    ),
                    component("title", "Text", "text" to "Coffee order", "variant" to "h2"),
                    component("prompt", "Text", "text" to path("/prompt")),
                )
            steps.forEach { components += componentsFor(it, order) }
            return message("updateComponents", "components" to JSONArray(components))
        }

        fun componentIds(step: Step): List<String> =
            when (step) {
                Step.CoffeeType -> listOf("type_picker", "type_next")
                Step.AddIns -> listOf("add_ins_picker", "add_ins_next")
                Step.Sweetness -> listOf("sweetness_slider", "sweetness_next")
                Step.Checkout -> listOf("divider", "to_go_checkbox", "order_button")
                Step.Summary -> listOf("summary")
            }

        fun componentsFor(
            step: Step,
            order: Order,
        ): List<JSONObject> =
            when (step) {
                Step.CoffeeType -> {
                    listOf(
                        component(
                            "type_picker",
                            "ChoicePicker",
                            "label" to "Coffee type",
                            "variant" to "mutuallyExclusive",
                            "options" to options(mapOf(HOT to "Hot", COLD to "Cold")),
                            "value" to path("/order/type"),
                        ),
                    ) + button("type_next", "Next", "chooseType", "type")
                }

                Step.AddIns -> {
                    listOf(
                        component(
                            "add_ins_picker",
                            "ChoicePicker",
                            "label" to "Add-ins",
                            "variant" to "multipleSelection",
                            "displayStyle" to "chips",
                            "options" to options(addInOptions(order.type)),
                            "value" to path("/order/addIns"),
                        ),
                    ) + button("add_ins_next", "Next", "chooseAddIns", "type", "addIns")
                }

                Step.Sweetness -> {
                    listOf(
                        component(
                            "sweetness_slider",
                            "Slider",
                            "label" to "Sweetness",
                            "min" to 0,
                            "max" to 5,
                            "value" to path("/order/sweetness"),
                        ),
                    ) +
                        button(
                            "sweetness_next",
                            "Next",
                            "chooseSweetness",
                            "type",
                            "addIns",
                            "sweetness",
                        )
                }

                Step.Checkout -> {
                    listOf(
                        component("divider", "Divider"),
                        component(
                            "to_go_checkbox",
                            "CheckBox",
                            "label" to "To go",
                            "value" to path("/order/toGo"),
                        ),
                    ) +
                        button(
                            "order_button",
                            "Place order",
                            "placeOrder",
                            "type",
                            "addIns",
                            "sweetness",
                            "toGo",
                        )
                }

                Step.Summary -> {
                    listOf(component("summary", "Text", "text" to path("/summary")))
                }
            }

        /** A Button (plus its label Text) whose event context reads [fields] from `/order`. */
        fun button(
            id: String,
            label: String,
            event: String,
            vararg fields: String,
        ): List<JSONObject> {
            val context = JSONObject()
            fields.forEach { context.put(it, path("/order/$it")) }
            return listOf(
                component("${id}_label", "Text", "text" to label),
                component(
                    id,
                    "Button",
                    "child" to "${id}_label",
                    "variant" to "primary",
                    "action" to
                        JSONObject().put(
                            "event",
                            JSONObject().put("name", event).put("context", context),
                        ),
                ),
            )
        }

        fun component(
            id: String,
            type: String,
            vararg props: Pair<String, Any>,
        ): JSONObject =
            JSONObject().put("id", id).put("component", type).apply {
                props.forEach { (k, v) ->
                    put(k, v)
                }
            }

        fun path(path: String): JSONObject = JSONObject().put("path", path)

        fun options(options: Map<String, String>): JSONArray =
            JSONArray(
                options.map { (value, label) ->
                    JSONObject().put("label", label).put("value", value)
                },
            )

        fun createSurface(): String = message("createSurface", "catalogId" to CATALOG_ID)

        fun setData(
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
        fun message(
            kind: String,
            vararg body: Pair<String, Any>,
        ): String {
            val payload = JSONObject().put("surfaceId", SURFACE_ID)
            body.forEach { (k, v) -> payload.put(k, v) }
            return JSONObject().put("version", "v0.9.1").put(kind, payload).toString(2)
        }
    }
}
