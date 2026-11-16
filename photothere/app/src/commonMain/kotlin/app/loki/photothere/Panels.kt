package app.loki.photothere

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.loki.kit.ToggleChip
import app.loki.kit.isTouchPlatform
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

private fun JsonElement?.o() = this as? JsonObject
private fun JsonElement?.a() = (this as? JsonArray).orEmpty()
private fun JsonElement?.s() = (this as? JsonPrimitive)?.contentOrNull

/** A named group of items, as the engine's preset lists give them. */
data class Group<T>(val name: String, val items: List<T>)

data class Swatch(val name: String, val hex: String)
data class GradientPreset(val name: String, val stops: List<Pair<Double, String>>)
data class StylePreset(val name: String, val effects: List<String>)
data class SavedPath(val name: String, val knots: Int)
data class ActionInfo(val name: String, val steps: Int, val set: String?)

private fun <T> groups(answer: JsonElement?, items: String, read: (JsonObject) -> T?): List<Group<T>> =
    answer.o()?.get("groups").a().mapNotNull { g ->
        val go = g.o() ?: return@mapNotNull null
        Group(go["name"].s() ?: return@mapNotNull null, go[items].a().mapNotNull { it.o()?.let(read) })
    }

fun parseSwatches(answer: JsonElement?): List<Group<Swatch>> =
    groups(answer, "swatches") { o -> Swatch(o["name"].s() ?: return@groups null, o["hex"].s() ?: return@groups null) }

fun parseGradients(answer: JsonElement?): List<Group<GradientPreset>> =
    groups(answer, "presets") { o ->
        val stops = o["stops"].a().mapNotNull { s -> val p = s.a(); (p.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.let { t -> p.getOrNull(1).s()?.let { c -> t to c } } }
        GradientPreset(o["name"].s() ?: return@groups null, stops)
    }

fun parseStyles(answer: JsonElement?): List<Group<StylePreset>> =
    groups(answer, "presets") { o -> StylePreset(o["name"].s() ?: return@groups null, o["effects"].a().mapNotNull { it.s() }) }

fun parsePaths(answer: JsonElement?): Pair<List<SavedPath>, Boolean> {
    val o = answer.o() ?: return emptyList<SavedPath>() to false
    val paths = o["paths"].a().mapNotNull { p -> p.o()?.let { SavedPath(it["name"].s() ?: return@mapNotNull null, (it["knots"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0) } }
    return paths to (o["workPath"] != null && o["workPath"] !is kotlinx.serialization.json.JsonNull)
}

fun parseActions(answer: JsonElement?): Pair<List<ActionInfo>, String?> {
    val o = answer.o() ?: return emptyList<ActionInfo>() to null
    val actions = o["actions"].a().mapNotNull { a -> a.o()?.let { ActionInfo(it["name"].s() ?: return@mapNotNull null, (it["steps"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0, it["set"].s()) } }
    return actions to o["recording"].s()
}

/** `#rrggbb` as a colour; null for anything else. */
fun hexColour(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    return if (h.length == 6) h.toLongOrNull(16)?.let { Color(0xFF000000L or it) } else null
}

/** The foreground or background of `session.inspect` (`[r, g, b, a]`, 0..1) as a colour. */
fun sessionColour(session: JsonElement?, key: String): Color? {
    val c = session.o()?.get(key).a().map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
    return if (c.size >= 3) Color(c[0].toFloat().coerceIn(0f, 1f), c[1].toFloat().coerceIn(0f, 1f), c[2].toFloat().coerceIn(0f, 1f)) else null
}

/** The stops of a gradient preset as colours, `foreground` and `background` standing for the paint in hand; null when there are fewer than two. */
fun gradientColours(stops: List<Pair<Double, String>>, fg: Color, bg: Color): List<Pair<Float, Color>>? {
    val out = stops.mapNotNull { (t, c) -> (when (c) { "foreground" -> fg; "background" -> bg; else -> hexColour(c) })?.let { t.toFloat().coerceIn(0f, 1f) to it } }.sortedBy { it.first }
    return out.takeIf { it.size >= 2 }
}

private val BOX = RoundedCornerShape(3.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchesPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val s = state.o()
    val sets = parseSwatches(s?.get("swatches.list"))
    var toBackground by remember { mutableStateOf(false) }
    val chip = if (isTouchPlatform) 36.dp else 24.dp
    Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(sessionColour(s?.get("session.inspect"), "foreground") ?: Color.Black, BOX).border(1.dp, MaterialTheme.colorScheme.outline, BOX))
            Box(Modifier.size(22.dp).background(sessionColour(s?.get("session.inspect"), "background") ?: Color.White, BOX).border(1.dp, MaterialTheme.colorScheme.outline, BOX))
            ToggleChip("Set background", toBackground) { toBackground = it }
        }
        for (g in sets) {
            Text(g.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (sw in g.items) Box(
                    Modifier.size(chip).background(hexColour(sw.hex) ?: Color.Transparent, BOX).border(1.dp, MaterialTheme.colorScheme.outlineVariant, BOX).clickable {
                        run("swatches.use", buildJsonObject { put("swatch", sw.name); put("group", g.name); put("target", if (toBackground) "background" else "foreground") })
                    },
                )
            }
        }
        OutlinedButton({ run("swatches.add", buildJsonObject { }) }) { Text("Add the foreground colour", style = MaterialTheme.typography.labelMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradientsPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val s = state.o()
    val sets = parseGradients(s?.get("gradient.presets.list"))
    val current = s?.get("gradient.presets.list").o()?.get("current").o()?.get("name").s()
    val fg = sessionColour(s?.get("session.inspect"), "foreground") ?: Color.Black
    val bg = sessionColour(s?.get("session.inspect"), "background") ?: Color.White
    Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (g in sets) {
            Text(g.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (p in g.items) {
                    val colours = gradientColours(p.stops, fg, bg)
                    Box(
                        Modifier.width(if (isTouchPlatform) 64.dp else 48.dp).heightIn(min = if (isTouchPlatform) 36.dp else 24.dp)
                            .then(if (colours != null) Modifier.background(Brush.horizontalGradient(colorStops = colours.map { it.first to it.second }.toTypedArray()), BOX) else Modifier.background(Color.Transparent, BOX))
                            .border(if (p.name == current) 2.dp else 1.dp, if (p.name == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, BOX)
                            .clickable { run("gradient.presets.select", buildJsonObject { put("preset", p.name); put("group", g.name) }) },
                    )
                }
            }
        }
        if (current != null) Text(current, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun StylesPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val sets = parseStyles(state.o()?.get("style.presets.list"))
    Column(modifier.verticalScroll(rememberScrollState())) {
        for (g in sets) {
            Text(g.name, Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (p in g.items) Row(
                Modifier.fillMaxWidth().heightIn(min = if (isTouchPlatform) 44.dp else 32.dp)
                    .clickable { run("style.presets.apply", buildJsonObject { put("preset", p.name); put("group", g.name) }) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(p.name, style = MaterialTheme.typography.bodySmall)
                if (p.effects.isNotEmpty()) Text(p.effects.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PathsPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val (saved, hasWork) = parsePaths(state.o()?.get("path.list"))
    val rows = (if (hasWork) listOf(SavedPath("work", 0)) else emptyList()) + saved
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (rows.isEmpty()) Text("No paths. Draw one with the Pen tool.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (p in rows) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (p.name == "work") "Work Path" else p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val name = buildJsonObject { put("name", p.name) }
                TextButton({ run("path.toSelection", name) }) { Text("Selection", style = MaterialTheme.typography.labelMedium) }
                TextButton({ run("path.fill", name) }) { Text("Fill", style = MaterialTheme.typography.labelMedium) }
                TextButton({ run("path.stroke", buildJsonObject { put("name", p.name); put("tool", "brush") }) }) { Text("Stroke", style = MaterialTheme.typography.labelMedium) }
                TextButton({ run("path.delete", name) }) { Text("Delete", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
fun ActionsPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val (actions, recording) = parseActions(state.o()?.get("actions.list"))
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (recording != null) Text("Recording $recording", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFFD32F2F))
        if (actions.isEmpty()) Text("No actions yet.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (a in actions) Row(
            Modifier.fillMaxWidth().heightIn(min = if (isTouchPlatform) 44.dp else 32.dp).clickable { run("actions.play", buildJsonObject { put("action", a.name) }) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column { Text("▶  ${a.name}", style = MaterialTheme.typography.bodyMedium); a.set?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("${a.steps} steps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
