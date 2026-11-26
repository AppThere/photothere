package app.loki.photothere

import app.loki.photothere.dialogs.TRANSLATED_DIALOGS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps `docs/parity.md` true. The document's counts are recomputed here from the code: the `parity-counts` line must
 * match them, and so must the Summary rows that repeat them in prose. A count that moves fails this test until the
 * document is updated.
 */
class ParityDocTest {
    private val doc = File("../docs/parity.md").readText()

    private val actual: Map<String, Int> = run {
        val appParts = File("src/commonMain/kotlin/app/loki/photothere/AppParts.kt").readText()
        mapOf(
            "tools" to TOOLS.size,
            "notYet" to NOT_YET.count { id -> TOOLS.any { it.id == id } },
            "panels" to Regex("""DockPanel\("""").findAll(appParts).count(),
            "dialogs" to TRANSLATED_DIALOGS.size,
            "dialogsCommand" to TRANSLATED_DIALOGS.count { it.command != null },
            "dialogsOwn" to TRANSLATED_DIALOGS.count { it.command == null && !it.hidden },
            "dialogsHidden" to TRANSLATED_DIALOGS.count { it.command == null && it.hidden },
            "adjustSpecs" to ADJUST_SPECS.size,
            "gaps" to POINTER_GAPS.size,
            "gestures" to POINTER_GESTURES.size,
        )
    }

    @Test
    fun theCountsLineMatchesTheCode() {
        val line = Regex("""<!-- parity-counts (.*?) -->""").find(doc)?.groupValues?.get(1)
        assertTrue(line != null, "docs/parity.md has no `<!-- parity-counts … -->` line")
        val written = line.split(' ').filter { it.isNotBlank() }.associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        assertEquals(actual, written, "docs/parity.md's parity-counts line is out of date: update it, and the tables it summarises")
    }

    @Test
    fun theSummaryRowsRepeatTheSameCounts() {
        val a = actual
        val rail = a.getValue("tools") - a.getValue("notYet")
        val expected = listOf(
            "$rail of ${a["tools"]} declared tools in the rail; ${a["notYet"]} held back",
            "${a["panels"]} dock panels",
            "${a["dialogs"]} translated from egui: ${a["dialogsCommand"]} bound to a command, ${a["dialogsOwn"]} run their own command, ${a["dialogsHidden"]} hidden; ${a["adjustSpecs"]} hand-wired",
            "${a["gaps"]} pointer gestures are real gaps",
        )
        val missing = expected.filterNot { doc.contains(it) }
        assertEquals(emptyList(), missing, "docs/parity.md's Summary no longer says: $missing")
    }
}
