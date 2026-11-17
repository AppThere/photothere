package app.loki.photothere

import app.loki.kit.EngineClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolRailTest {
    private val ids = loadTools(EngineClient(FixtureTransport("[]"))).flatMap { listOf(it) + it.alternates }.map { it.id }

    /** A tool the canvas does nothing with must not be in the rail: pressing it only wrote "does not work yet" in the status line. */
    @Test
    fun noToolTheCanvasCannotUseIsOffered() {
        assertEquals(emptyList(), ids.filter { it in NOT_YET })
    }

    @Test
    fun theRestOfASlotSurvivesWhenOneToolIsHidden() {
        // Crop's slot also held Slice and Slice Select; the eyedropper's held Ruler, Note and Count.
        for (id in listOf("Move", "Crop", "PerspectiveCrop", "Eyedropper", "ColorSampler", "SpotHealing", "Healing", "Rectangle", "Line", "Hand", "Zoom")) assertTrue(id in ids, "$id is missing from the rail")
        assertTrue(ids.size >= 58, "only ${ids.size} tools in the rail")
    }

    @Test
    fun everyHiddenToolIsOneThePhotoCraftToolListHas() {
        assertEquals(emptyList(), NOT_YET.filter { id -> TOOLS.none { it.id == id } })
    }
}
