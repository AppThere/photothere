package app.loki.photothere

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdjustDialogsTest {
    private val params = Json.parseToJsonElement(File("src/jvmTest/resources/engine-commands.json").readText()).jsonArray
        .associate { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject["params"]!!.jsonPrimitive.content }

    /** The controls are the command's own: every key is in its params doc with the range and default the dialog uses. */
    @Test
    fun everyControlMatchesTheParamsDoc() {
        for (spec in ADJUST_SPECS.values) {
            val doc = params[spec.command] ?: error("${spec.command} is not an engine command")
            for (s in spec.sliders) {
                val m = Regex("\"${s.key}\":(-?[\\d.]+)\\.\\.(-?[\\d.]+)=(-?[\\d.]+)").find(doc) ?: error("${spec.command}: no '${s.key}' range in $doc")
                val (lo, hi, def) = m.destructured
                assertEquals(lo.toDouble(), s.min, "${s.key} min"); assertEquals(hi.toDouble(), s.max, "${s.key} max"); assertEquals(def.toDouble(), s.default, "${s.key} default")
            }
            for (t in spec.toggles) assertTrue("\"${t.key}\":bool" in doc, "${spec.command}: no bool '${t.key}'")
        }
    }

    @Test
    fun nothingIsAppliedWhileEverythingIsAtItsDefault() {
        val spec = ADJUST_SPECS.getValue("image.adjustments.brightnessContrast")
        assertNull(adjustParams(spec, emptyMap(), emptyMap()))
        assertNull(adjustParams(spec, mapOf("brightness" to 0.0, "contrast" to 0.0), mapOf("legacy" to false)))
        val p = adjustParams(spec, mapOf("brightness" to 40.0), emptyMap())!!
        assertEquals(JsonPrimitive(40), p["brightness"]); assertEquals(JsonPrimitive(0), p["contrast"]); assertEquals(JsonPrimitive(false), p["legacy"])
    }

    @Test
    fun valuesAreClampedAndIntegersStayIntegers() {
        val spec = ADJUST_SPECS.getValue("image.adjustments.levels")
        val p = adjustParams(spec, mapOf("inBlack" to 999.0, "gamma" to 1.456), emptyMap())!!
        assertEquals(JsonPrimitive(253), p["inBlack"]); assertEquals(JsonPrimitive(1.456), p["gamma"])
    }

    @Test
    fun aLogSliderGivesEachDoublingTheSameRoom() {
        val g = ADJUST_SPECS.getValue("image.adjustments.levels").sliders.first { it.key == "gamma" }
        assertEquals(g.position(1.0) - g.position(0.5), g.position(2.0) - g.position(1.0), 1e-9)
        assertEquals(1.0, g.valueAt(g.position(1.0)), 1e-9)
        assertEquals(g.min, g.valueAt(0.0), 1e-9); assertEquals(g.max, g.valueAt(1.0), 1e-9)
        val b = ADJUST_SPECS.getValue("image.adjustments.brightnessContrast").sliders.first()
        assertEquals(0.5, b.position(0.0), 1e-9)   // linear: zero is the middle of -150..150... of the track
    }

    private class Doc { val log = mutableListOf<String>(); var failNext = false
        val session = PreviewSession({ p -> if (failNext) { failNext = false; false } else { log += "apply ${p["brightness"]}"; true } }, { log += "undo" }) }

    private fun p(b: Int) = JsonObject(mapOf("brightness" to JsonPrimitive(b)))

    @Test
    fun eachPreviewReplacesTheLastOne() = runBlocking {
        val d = Doc()
        d.session.show(p(10)); d.session.show(p(20)); d.session.show(null)
        assertEquals(listOf("apply 10", "undo", "apply 20", "undo"), d.log)
        assertTrue(!d.session.applied)
    }

    @Test
    fun cancelLeavesTheDocumentAsItWas() = runBlocking {
        val d = Doc()
        d.session.show(p(10)); d.session.cancel()
        assertEquals(listOf("apply 10", "undo"), d.log)
        d.session.show(p(30))   // a change still queued when the dialog closed
        assertEquals(2, d.log.size)
    }

    @Test
    fun okKeepsOneStepAndIgnoresLaterChanges() = runBlocking {
        val d = Doc()
        d.session.show(p(10)); d.session.commit(p(25)); d.session.show(p(99)); d.session.cancel()
        assertEquals(listOf("apply 10", "undo", "apply 25"), d.log)
    }

    @Test
    fun aFailedPreviewIsNotUndoneLater() = runBlocking {
        val d = Doc()
        d.failNext = true
        d.session.show(p(10)); d.session.cancel()
        assertEquals(emptyList(), d.log, "nothing was applied, so there is nothing to undo")
    }
}
