package com.example.a2uisample.agent

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Claude copies the patterns in the example replies, so a mistake there turns into a mistake on
 * screen. These checks keep every example valid, and the start one is also shown as-is.
 */
@RunWith(AndroidJUnit4::class)
class TroubleshooterExamplesTest {
    private val examples = TroubleshooterExamples()
    private val all =
        mapOf("start" to examples.start, "clarify" to examples.clarify, "diagnose" to examples.diagnose)

    @Test
    fun everyMessageParses() {
        val parser = A2uiMessageParser()
        all.forEach { (name, json) ->
            messages(json).forEach { message ->
                assertEquals("$name: version", "v0.9.1", message.getString("version"))
                parser.parse(message.toString())
            }
        }
    }

    @Test
    fun componentTreeIsCompleteAndOrdered() {
        all.forEach { (name, json) ->
            val updates = messages(json).filter { it.has("updateComponents") }
            assertEquals("$name: one updateComponents", 1, updates.size)
            assertTrue("$name: updateComponents is last", messages(json).last().has("updateComponents"))

            val components = updates.single().getJSONObject("updateComponents").getJSONArray("components")
            val list = List(components.length()) { components.getJSONObject(it) }
            assertEquals("$name: root first", "root", list.first().getString("id"))

            val ids = list.map { it.getString("id") }
            assertEquals("$name: unique ids", ids.size, ids.toSet().size)
            list.forEachIndexed { index, component ->
                references(component).forEach { child ->
                    val childIndex = ids.indexOf(child)
                    assertTrue("$name: ${component.getString("id")} -> missing $child", childIndex >= 0)
                    assertTrue("$name: $child comes before its parent", childIndex > index)
                }
            }
        }
    }

    private fun messages(json: String): List<JSONObject> {
        val array = JSONArray(json)
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun references(component: JSONObject): List<String> =
        buildList {
            component.optString("child").takeIf { it.isNotEmpty() }?.let(::add)
            component.optJSONArray("children")?.let { children ->
                repeat(children.length()) { add(children.getString(it)) }
            }
        }
}
