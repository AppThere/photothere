package app.loki.photothere

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keeps the coverage number honest. `POINTER_GAPS` is the list of commands a gesture on the surface should issue and
 * does not; `POINTER_GESTURES` is the list of those it does. Both are out of the menus, so a command left in the
 * gaps after its gesture was built hides a finished feature, and a command in the gestures with no gesture hides a
 * missing one. A command counts as issued when its id is a string literal in the app's own sources (`AppParts.kt`
 * maps tools to commands by id), excluding `Profile.kt`, which only lists them.
 */
class PointerGapsTest {
    private val sources: String = listOf("commonMain", "jnaMain", "androidMain", "jvmMain")
        .map { File("src/$it") }.filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") && it.name != "Profile.kt" }.toList() }
        .joinToString("\n") { it.readText() }

    private fun issued(id: String) = sources.contains("\"$id\"")

    @Test
    fun noCommandIsLeftInTheGapsOnceTheAppIssuesIt() {
        val stale = POINTER_GAPS.filter(::issued).sorted()
        assertEquals(emptyList(), stale, "issued by the app but still in POINTER_GAPS: move to POINTER_GESTURES")
    }

    @Test
    fun everyListedGestureIsIssuedByTheApp() {
        val missing = POINTER_GESTURES.filterNot(::issued).sorted()
        assertEquals(emptyList(), missing, "in POINTER_GESTURES but no source issues it: move to POINTER_GAPS")
    }

    @Test
    fun aCommandIsInOneListOnly() {
        assertEquals(emptySet(), POINTER_GAPS intersect POINTER_GESTURES)
    }
}
