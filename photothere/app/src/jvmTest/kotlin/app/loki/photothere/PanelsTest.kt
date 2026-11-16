package app.loki.photothere

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PanelsTest {
    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun swatchGroupsAreReadAndABadSwatchIsLeftOut() {
        val g = parseSwatches(parse("""{"count":3,"groups":[{"name":"Grays","swatches":[{"name":"Black","hex":"#000000"},{"name":"NoHex"},{"hex":"#fff"}]},{"name":"Empty","swatches":[]},{"swatches":[]}]}"""))
        assertEquals(listOf("Grays", "Empty"), g.map { it.name })
        assertEquals(listOf(Swatch("Black", "#000000")), g[0].items)
        assertEquals(emptyList(), parseSwatches(null))
    }

    @Test fun gradientsAndStylesAndPathsAndActionsAreRead() {
        val gr = parseGradients(parse("""{"groups":[{"name":"Basics","presets":[{"name":"FG to BG","stops":[[0.0,"foreground"],[1.0,"background"]]},{"stops":[]}]}]}"""))
        assertEquals(listOf(GradientPreset("FG to BG", listOf(0.0 to "foreground", 1.0 to "background"))), gr[0].items)
        val st = parseStyles(parse("""{"groups":[{"name":"Basics","presets":[{"name":"Drop Shadow","effects":["Drop Shadow"]},{"name":"None","effects":[]}]}]}"""))
        assertEquals(listOf(StylePreset("Drop Shadow", listOf("Drop Shadow")), StylePreset("None", emptyList())), st[0].items)
        val (paths, work) = parsePaths(parse("""{"paths":[{"name":"Path 1","knots":4},{"knots":2}],"workPath":{"knots":3}}"""))
        assertEquals(listOf(SavedPath("Path 1", 4)), paths); assertEquals(true, work)
        assertEquals(false, parsePaths(parse("""{"paths":[],"workPath":null}""")).second)
        val (actions, rec) = parseActions(parse("""{"actions":[{"name":"Sepia","steps":3,"set":"Mine"},{"steps":1}],"recording":"New"}"""))
        assertEquals(listOf(ActionInfo("Sepia", 3, "Mine")), actions); assertEquals("New", rec)
    }

    @Test fun coloursAreReadAndGradientsResolveForegroundAndBackground() {
        assertEquals(Color(0xFFFF0000), hexColour("#ff0000")); assertNull(hexColour("#fff")); assertNull(hexColour(null))
        val session = parse("""{"foreground":[0.0,0.0,1.0,1.0],"background":[1,1,1,1]}""")
        assertEquals(Color(0f, 0f, 1f), sessionColour(session, "foreground")); assertNull(sessionColour(session, "nothing"))
        val fg = Color.Black; val bg = Color.White
        assertEquals(listOf(0f to fg, 1f to bg), gradientColours(listOf(1.0 to "background", 0.0 to "foreground"), fg, bg), "sorted by position")
        assertNull(gradientColours(listOf(0.0 to "foreground"), fg, bg), "one stop is not a gradient")
        assertNull(gradientColours(listOf(0.0 to "nonsense", 1.0 to "also"), fg, bg))
    }

    /** Every command a panel runs is in the engine's list and its doc names each key the panel sends. */
    @Test fun everyPanelCallIsAKeyTheCommandDocumentsOnACommandTheEngineHas() {
        val docs = (Json.parseToJsonElement(java.io.File("src/jvmTest/resources/engine-commands.json").readText()) as JsonArray)
            .associate { c -> val o = c as JsonObject; (o["id"] as JsonPrimitive).content to (o["params"] as JsonPrimitive).content }
        val sent = mapOf(
            "swatches.use" to listOf("swatch", "group", "target"), "swatches.add" to emptyList(),
            "gradient.presets.select" to listOf("preset", "group"), "style.presets.apply" to listOf("preset", "group"),
            "path.toSelection" to listOf("name"), "path.fill" to listOf("name"), "path.stroke" to listOf("name", "tool"), "path.delete" to listOf("name"),
            "actions.play" to listOf("action"),
        )
        for ((command, keys) in sent) {
            val doc = docs[command]
            assertNotNull(doc, "the engine has no $command")
            for (key in keys) assertEquals(true, Regex("\"$key\"").containsMatchIn(doc), "$command does not document $key: $doc")
        }
        for (c in listOf("swatches.list", "gradient.presets.list", "style.presets.list", "path.list", "actions.list")) assertNotNull(docs[c], "the engine has no $c")
    }
}
