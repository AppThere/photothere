package app.loki.photothere

// PhotoThere's own parts of the art workspace (first written by egui2compose, now the app's): the
// tool palette in the desktop's slots, the colour swatches under it, the options bar, and the
// Properties, Layers, Channels and History panels, all read from `document.inspect` and
// `session.inspect` and acting through engine commands.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.loki.kit.ChoiceChip
import app.loki.kit.DockPanel
import app.loki.kit.NumberChip
import app.loki.kit.ToggleChip
import app.loki.kit.EngineClient
import app.loki.kit.RailTool
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Bandage
import com.composables.icons.lucide.Blend
import com.composables.icons.lucide.Brush
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Frame
import com.composables.icons.lucide.Hand
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lasso
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.MousePointer2
import com.composables.icons.lucide.PenTool
import com.composables.icons.lucide.Pipette
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.SquareDashed
import com.composables.icons.lucide.SquarePlus
import com.composables.icons.lucide.Stamp
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Type
import com.composables.icons.lucide.Wand
import com.composables.icons.lucide.ZoomIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The document, then the session (foreground and background colours, open documents). */
val STATE_COMMANDS: List<String> = listOf("document.inspect", "session.inspect", "brush.get", "path.info", "brush.presets.list")

/** Commands this file runs, so the coverage test checks the engine still has them. */
val PANEL_COMMANDS: Set<String> = setOf(
    "layer.select", "layer.setProps", "layer.new.layer", "layer.duplicate", "layer.delete",
    "tools.swapColors", "tools.defaultColors",
    "tools.decreaseBrushSize", "tools.increaseBrushSize", "tools.decreaseBrushHardness", "tools.increaseBrushHardness",
    // The canvas's tools (CanvasTools below).
    "paint.stroke", "paint.pencil", "paint.mixerBrush", "paint.colorReplacement", "paint.backgroundEraser", "paint.cloneStamp",
    "paint.patternStamp", "paint.healingBrush", "paint.spotHealing", "paint.remove", "paint.historyBrush", "paint.artHistoryBrush",
    "paint.blur", "paint.sharpen", "paint.smudge", "paint.dodge", "paint.burn", "paint.sponge", "paint.bucket", "paint.magicEraser",
    "paint.gradient", "paint.redEye", "select.rect", "select.lasso", "select.quick", "select.object", "select.magicWand", "select.deselect",
    "image.crop", "layer.translate", "shape.create", "type.create", "document.sampleColor", "tools.setColors", "tools.setBrush", "brush.get",
    "path.set", "path.info", "path.moveAnchors", "path.toSelection", "path.fill", "path.stroke", "path.delete",
    "type.hitTest", "type.info", "type.edit", "file.saveACopy",
    // The options bar, the colour wells and the panels' own controls.
    "brush.presets.list", "history.selectState", "layer.arrange.bringForward", "layer.arrange.sendBackward",
)

val FIRST_PANEL: String? = "layers"

/** As on the desktop: Properties, then Layers with Channels, then History. */
val PANEL_GROUPS: List<List<String>> = listOf(listOf("properties"), listOf("layers", "channels"), listOf("history"))

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.str(key: String): String? = (obj()?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.num(key: String): Double? = (obj()?.get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonElement?.long(key: String): Long? = (obj()?.get(key) as? JsonPrimitive)?.longOrNull
private fun JsonElement?.bool(key: String): Boolean? = (obj()?.get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.arr(key: String): List<JsonElement> = (obj()?.get(key) as? JsonArray).orEmpty()

private fun doc(state: JsonElement?) = state.obj()?.get("document.inspect")
private fun session(state: JsonElement?) = state.obj()?.get("session.inspect")

fun documentTitle(state: JsonElement?): String = doc(state).str("name") ?: APP_NAME

fun documentDirty(state: JsonElement?): Boolean {
    val s = session(state); val active = s.long("active") ?: return false
    return s.arr("documents").firstOrNull { it.long("index") == active }.bool("dirty") == true
}

private fun modeName(m: String?) = when (m?.lowercase()) {
    "rgb" -> "RGB"; "cmyk" -> "CMYK"; "lab" -> "Lab"; "gray", "grayscale" -> "Grayscale"; "bitmap" -> "Bitmap"; "indexed" -> "Indexed"; null -> ""; else -> m
}

fun statusSegments(state: JsonElement?): List<String> {
    val d = doc(state) ?: return listOf("No document")
    val w = d.long("width"); val h = d.long("height")
    return listOfNotNull(
        if (w != null && h != null) "$w × $h px" else null,
        listOfNotNull(modeName(d.str("mode")).ifEmpty { null }, d.long("depth")?.let { "$it bit" }).joinToString(" · ").ifEmpty { null },
        d.num("resolution")?.let { "${it.toInt()} ppi" },
        d.arr("layers").size.let { if (it == 1) "1 layer" else "$it layers" },
    )
}

// The desktop's tool slots, in its order: each slot shows one tool, its alternates behind it.
private val SLOTS: List<List<String>> = listOf(
    listOf("Move", "Artboard"),
    listOf("RectMarquee", "EllipseMarquee", "SingleRowMarquee", "SingleColumnMarquee"),
    listOf("Lasso", "PolygonLasso", "MagneticLasso"),
    listOf("ObjectSelection", "QuickSelection", "MagicWand"),
    listOf("Crop", "PerspectiveCrop", "Slice", "SliceSelect"),
    listOf("Frame"),
    listOf("Eyedropper", "ColorSampler", "Ruler", "Note", "Count"),
    listOf("SpotHealing", "Remove", "Healing", "Patch", "ContentAwareMove", "RedEye"),
    listOf("Brush", "Pencil", "ColorReplacement", "MixerBrush"),
    listOf("CloneStamp", "PatternStamp"),
    listOf("HistoryBrush", "ArtHistoryBrush"),
    listOf("Eraser", "BackgroundEraser", "MagicEraser"),
    listOf("Gradient", "PaintBucket"),
    listOf("Blur", "Sharpen", "Smudge"),
    listOf("Dodge", "Burn", "Sponge"),
    listOf("Pen", "FreeformPen", "CurvaturePen", "AddAnchorPoint", "DeleteAnchorPoint", "ConvertPoint"),
    listOf("Type", "VerticalType", "VerticalTypeMask", "HorizontalTypeMask"),
    listOf("PathSelection", "DirectSelection"),
    listOf("Rectangle", "EllipseShape", "Triangle", "Polygon", "Star", "Line", "CustomShape"),
    listOf("Hand", "RotateView"),
    listOf("Zoom"),
)

/** One glyph per slot, so a tool's alternates look like its slot (as on the desktop). */
private val SLOT_ICONS: Map<String, ImageVector> = mapOf(
    "Move" to Lucide.Move, "RectMarquee" to Lucide.SquareDashed, "Lasso" to Lucide.Lasso, "ObjectSelection" to Lucide.Wand,
    "Crop" to Lucide.Crop, "Frame" to Lucide.Frame, "Eyedropper" to Lucide.Pipette, "SpotHealing" to Lucide.Bandage,
    "Brush" to Lucide.Brush, "CloneStamp" to Lucide.Stamp, "HistoryBrush" to Lucide.History, "Eraser" to Lucide.Eraser,
    "Gradient" to Lucide.Blend, "Blur" to Lucide.Droplet, "Dodge" to Lucide.Sun, "Pen" to Lucide.PenTool, "Type" to Lucide.Type,
    "PathSelection" to Lucide.MousePointer2, "Rectangle" to Lucide.Shapes, "Hand" to Lucide.Hand, "Zoom" to Lucide.ZoomIn,
)

private fun railTool(id: String, slot: String): RailTool? {
    val t = TOOLS.firstOrNull { it.id == id } ?: return null
    return RailTool(t.id, t.title, icon = SLOT_ICONS[slot] ?: toolIcon(t.id))
}

/** The desktop's tool palette. Tools the generator found that no slot names are added at the end. */
fun loadTools(engine: EngineClient): List<RailTool> {
    val slotted = SLOTS.mapNotNull { slot ->
        val tools = slot.mapNotNull { railTool(it, slot.first()) }
        tools.firstOrNull()?.let { RailTool(it.id, it.label, it.shortcut, it.icon, tools.drop(1)) }
    }
    val named = SLOTS.flatten().toSet()
    return slotted + TOOLS.filter { it.id !in named }.map { RailTool(it.id, it.title, icon = toolIcon(it.id)) }
}

/** PhotoCraft's tools are the front end's state (the engine takes their strokes as commands), so picking one runs nothing. */
fun toolCommand(id: String): Pair<String, JsonObject>? = null

fun toolInHand(state: JsonElement?): String? = null

private val BRUSH_TOOLS = setOf("Brush", "Pencil", "ColorReplacement", "MixerBrush", "Eraser", "BackgroundEraser", "CloneStamp", "PatternStamp",
    "HistoryBrush", "ArtHistoryBrush", "SpotHealing", "Healing", "Remove", "Blur", "Sharpen", "Smudge", "Dodge", "Burn", "Sponge")

private val NONE = JsonObject(emptyMap())

/** The tools that put the foreground colour on the picture: their options bar shows it, to change it there. */
private val COLOUR_TOOLS = setOf("Brush", "Pencil", "MixerBrush", "ColorReplacement", "PaintBucket", "Gradient", "Type", "VerticalType",
    "Rectangle", "EllipseShape", "Triangle", "Polygon", "Star", "Line", "Eyedropper")

/** The tools that fill or select by how alike colours are. */
private val TOLERANCE_TOOLS = setOf("MagicWand", "PaintBucket", "MagicEraser")

/**
 * What the options bar sets that the engine keeps no session state for: these go with each command
 * the tool runs (a wand's tolerance, a gradient's style, new type's size).
 */
object ToolOptions {
    var tolerance by androidx.compose.runtime.mutableIntStateOf(32)
    var contiguous by androidx.compose.runtime.mutableStateOf(true)
    var gradientStyle by androidx.compose.runtime.mutableStateOf("linear")
    var typeSize by androidx.compose.runtime.mutableIntStateOf(24)
    var sides by androidx.compose.runtime.mutableIntStateOf(5)
    var lineWeight by androidx.compose.runtime.mutableIntStateOf(4)
    /** What a new selection does to the one there is, where no Shift or Alt says: replace, add, subtract, intersect. */
    var selectMode by androidx.compose.runtime.mutableStateOf("replace")
}

private val SELECT_MODES = listOf("replace" to "New", "add" to "Add", "subtract" to "Subtract", "intersect" to "Intersect")

/** The tools whose selection combines with the one there is. */
private val MODE_TOOLS = setOf("RectMarquee", "EllipseMarquee", "SingleRowMarquee", "SingleColumnMarquee", "Lasso", "PolygonLasso", "MagneticLasso", "ObjectSelection", "MagicWand")

private val GRADIENT_STYLES = listOf("linear" to "Linear", "radial" to "Radial", "angle" to "Angle", "reflected" to "Reflected", "diamond" to "Diamond")

/** The options bar: the tool in hand and what it paints with (it scrolls sideways where the window is narrow). */
@Composable
fun OptionsBar(tool: String, tools: List<RailTool>, state: JsonElement?, run: (String, JsonObject) -> Unit) {
    val t = tools.flatMap { listOf(it) + it.alternates }.firstOrNull { it.id == tool }
    Row(
        Modifier.fillMaxWidth().height(if (app.loki.kit.isTouchPlatform) 48.dp else 40.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        t?.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(t?.label ?: tool, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (tool in COLOUR_TOOLS) { VerticalDivider(Modifier.height(24.dp)); ColourWells(state, run, 22.dp, withButtons = true) }
        if (tool in PEN_TOOLS) PenOptions(state, run)
        if (tool in BRUSH_TOOLS) {
            val brush = state.obj()?.get("brush.get")
            fun set(key: String, v: Double) = run("tools.setBrush", buildJsonObject { put(key, v) })
            VerticalDivider(Modifier.height(24.dp))
            val presets = state.obj()?.get("brush.presets.list").arr("presets").mapNotNull { it.str("name") }
            if (presets.isNotEmpty()) ChoiceChip("Brush", null, presets.map { it to it }, { run("tools.setBrush", buildJsonObject { put("preset", it) }) })
            NumberChip("Size", brush.num("size"), 1.0, 1000.0, { set("size", it) }, unit = " px", log = true)
            NumberChip("Hardness", brush.num("hardness")?.times(100), 0.0, 100.0, { set("hardness", it / 100) }, unit = "%", step = 5.0)
            NumberChip("Opacity", brush.num("opacity")?.times(100), 1.0, 100.0, { set("opacity", it / 100) }, unit = "%", step = 5.0)
            NumberChip("Flow", brush.num("flow")?.times(100), 1.0, 100.0, { set("flow", it / 100) }, unit = "%", step = 5.0)
            // A pen's pressure controls the size and the opacity only when these are on (both off by default, as on the desktop).
            ToggleChip("Pressure: size", brush.bool("pressureSize") == true) { run("tools.setBrush", buildJsonObject { put("pressureSize", it) }) }
            ToggleChip("Pressure: opacity", brush.bool("pressureOpacity") == true) { run("tools.setBrush", buildJsonObject { put("pressureOpacity", it) }) }
        }
        if (tool == "Move" || tool in MODE_TOOLS) app.loki.kit.ModifierChips()
        if (tool in MODE_TOOLS) { VerticalDivider(Modifier.height(24.dp)); ChoiceChip("Mode", ToolOptions.selectMode, SELECT_MODES, { ToolOptions.selectMode = it }) }
        if (tool in TOLERANCE_TOOLS) {
            VerticalDivider(Modifier.height(24.dp))
            NumberChip("Tolerance", ToolOptions.tolerance.toDouble(), 0.0, 255.0, { ToolOptions.tolerance = it.toInt() }, step = 4.0)
            ToggleChip("Contiguous", ToolOptions.contiguous) { ToolOptions.contiguous = it }
        }
        if (tool == "Gradient") ChoiceChip("Style", ToolOptions.gradientStyle, GRADIENT_STYLES, { ToolOptions.gradientStyle = it })
        if (tool == "Type" || tool == "VerticalType") NumberChip("Size", ToolOptions.typeSize.toDouble(), 4.0, 400.0, { ToolOptions.typeSize = it.toInt() }, unit = " pt", log = true)
        if (tool == "Polygon" || tool == "Star") NumberChip(if (tool == "Star") "Points" else "Sides", ToolOptions.sides.toDouble(), 3.0, 24.0, { ToolOptions.sides = it.toInt() })
        if (tool == "Line") NumberChip("Weight", ToolOptions.lineWeight.toDouble(), 1.0, 200.0, { ToolOptions.lineWeight = it.toInt() }, unit = " px", log = true)
        if (doc(state).bool("hasSelection") == true && tool !in PEN_TOOLS) { VerticalDivider(Modifier.height(24.dp)); Chip("Deselect") { run("select.deselect", NONE) } }
    }
}

/** `[r, g, b, a]` in 0..1 as a colour; black for anything else. */
private fun colourOf(e: JsonElement?): Color {
    val c = (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat()?.coerceIn(0f, 1f) } ?: return Color.Black
    return if (c.size >= 3) Color(c[0], c[1], c[2], c.getOrElse(3) { 1f }) else Color.Black
}

/**
 * The foreground colour over the background one: a tap on either opens the colour picker for it.
 * [withButtons] adds default and swap, as under the tools on the desktop.
 */
@Composable
private fun ColourWells(state: JsonElement?, run: (String, JsonObject) -> Unit, well: androidx.compose.ui.unit.Dp, withButtons: Boolean) {
    val s = session(state)
    val fg = colourOf(s.obj()?.get("foreground")); val bg = colourOf(s.obj()?.get("background"))
    // Which colour the picker is open for: "foreground", "background" or neither.
    var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    picking?.let { which ->
        app.loki.kit.ColorPickerDialog(
            if (which == "foreground") "Foreground colour" else "Background colour", if (which == "foreground") fg else bg,
            onPick = { c -> picking = null; if (c != null) run("tools.setColors", buildJsonObject { put(which, app.loki.kit.hexOf(c)) }) },
            onDismiss = { picking = null },
        )
    }
    val step = well * 0.55f
    Box(Modifier.size(well + step)) {
        app.loki.kit.ColorWell(bg, "Background colour: tap to change", Modifier.offset(step, step).size(well)) { picking = "background" }
        app.loki.kit.ColorWell(fg, "Foreground colour: tap to change", Modifier.size(well)) { picking = "foreground" }
    }
    if (withButtons) {
        val size = if (app.loki.kit.isTouchPlatform) 36.dp else 24.dp
        IconButton(onClick = { run("tools.defaultColors", NONE) }, modifier = Modifier.size(size)) { Icon(Lucide.RotateCcw, contentDescription = "Default foreground and background colours", modifier = Modifier.size(14.dp)) }
        IconButton(onClick = { run("tools.swapColors", NONE) }, modifier = Modifier.size(size)) { Icon(Lucide.ArrowLeftRight, contentDescription = "Swap foreground and background colours", modifier = Modifier.size(14.dp)) }
    }
}

/** Beside the tools, always in reach: the foreground and background colours (a tap on one changes it). */
@Composable
fun ToolFooter(state: JsonElement?, run: (String, JsonObject) -> Unit) {
    Box(Modifier.padding(4.dp)) { ColourWells(state, run, if (app.loki.kit.isTouchPlatform) 28.dp else 24.dp, withButtons = false) }
}

@Composable
private fun PanelLines(lines: List<Pair<String, String>>, modifier: Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((k, v) in lines) Row {
            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
            Text(v, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun layerParams(id: Long) = buildJsonObject { put("layer", id) }

fun dockPanels(state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit): List<DockPanel> {
    val d = doc(state)
    return listOf(
        DockPanel("properties", "Properties", Lucide.SlidersHorizontal) { m ->
            if (d == null) Text("No document", Modifier.padding(12.dp)) else PanelLines(listOfNotNull(
                "Document" to (d.str("name") ?: ""),
                "Size" to "${d.long("width") ?: "?"} × ${d.long("height") ?: "?"} px",
                "Mode" to listOfNotNull(modeName(d.str("mode")), d.long("depth")?.let { "$it bit" }).joinToString(", "),
                d.num("resolution")?.let { "Resolution" to "${it.toInt()} ppi" },
                "Selection" to if (d.bool("hasSelection") == true) "Active" else "None",
                d.str("path")?.let { "File" to it },
            ), m)
        },
        DockPanel("layers", "Layers", Lucide.Layers) { m -> LayersPanel(d, run, m) },
        DockPanel("channels", "Channels", Lucide.Blend) { m ->
            val ch = d.obj()?.get("channels")
            val rows = listOfNotNull(ch.str("composite")?.let { it to "composite" }) +
                ch.arr("colors").map { (it.str("name") ?: "") to if (it.bool("visible") == false) "hidden" else "" } +
                ch.arr("alpha").map { (it.str("name") ?: "Alpha") to "alpha" }
            PanelLines(rows, m)
        },
        DockPanel("history", "History", Lucide.History) { m ->
            val steps = d.arr("history").mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: it.str("name") }
            LazyColumn(m) {
                itemsIndexed(steps) { i, step ->
                    // A tap goes back to that state, as a click on the desktop's History panel does.
                    Text(step, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == steps.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable(enabled = i != steps.lastIndex) { run("history.selectState", buildJsonObject { put("state", i) }) }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        },
    )
}

/** The blend modes the Layers panel offers, in the desktop's order (the engine's names, spaced for reading). */
private val BLENDS: List<Pair<String, String>> = listOf(
    "Normal", "Dissolve", "Darken", "Multiply", "ColorBurn", "LinearBurn", "DarkerColor", "Lighten", "Screen", "ColorDodge", "LinearDodge", "LighterColor",
    "Overlay", "SoftLight", "HardLight", "VividLight", "LinearLight", "PinLight", "HardMix", "Difference", "Exclusion", "Subtract", "Divide",
    "Hue", "Saturation", "Color", "Luminosity",
).map { it to it.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") }

/** Layers, top first: the eye shows or hides one, a tap selects it; new, duplicate and delete below. */
@Composable
private fun LayersPanel(d: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val layers = d.arr("layers")
    val current = layers.firstOrNull { it.bool("selected") == true }
    Column(modifier) {
        current?.long("id")?.let { id ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip("", current.str("blend") ?: "Normal", BLENDS, { run("layer.setProps", buildJsonObject { put("layer", id); put("blend", it) }) })
                NumberChip("Opacity", (current.num("opacity") ?: 1.0) * 100, 0.0, 100.0, { run("layer.setProps", buildJsonObject { put("layer", id); put("opacity", it / 100) }) }, unit = "%", step = 5.0)
            }
            HorizontalDivider()
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(layers) { _, l ->
                val id = l.long("id") ?: return@itemsIndexed
                val visible = l.bool("visible") != false
                val selected = l.bool("selected") == true
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { run("layer.select", layerParams(id)) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { run("layer.setProps", buildJsonObject { put("layer", id); put("visible", !visible) }) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (visible) Lucide.Eye else Lucide.EyeOff, contentDescription = if (visible) "Hide layer" else "Show layer", modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(l.str("name") ?: "Layer", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(l.str("kind"), l.str("blend")?.takeIf { it != "Normal" }, l.num("opacity")?.takeIf { it < 1.0 }?.let { "${(it * 100).toInt()}%" })
                        Text(detail.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { run("layer.arrange.bringForward", NONE) }, modifier = Modifier.size(36.dp)) { Icon(Lucide.ArrowUp, contentDescription = "Move layer up", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { run("layer.arrange.sendBackward", NONE) }, modifier = Modifier.size(36.dp)) { Icon(Lucide.ArrowDown, contentDescription = "Move layer down", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { run("layer.new.layer", NONE) }, modifier = Modifier.size(36.dp)) { Icon(Lucide.SquarePlus, contentDescription = "New layer", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { run("layer.duplicate", NONE) }, modifier = Modifier.size(36.dp)) { Icon(Lucide.CopyPlus, contentDescription = "Duplicate layer", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { run("layer.delete", NONE) }, modifier = Modifier.size(36.dp)) { Icon(Lucide.Trash2, contentDescription = "Delete layer", modifier = Modifier.size(18.dp)) }
        }
    }
}



// ---- The canvas ---------------------------------------------------------------------------------

/** PhotoCraft renders a document at most 1:1 (one screen pixel per document pixel), so zooming further in shows that picture enlarged. */
const val MAX_RENDER_SCALE = 1f
const val RENDER_OVERSAMPLE = 1f

/** The page is the active document: (0, 0, width, height) in document pixels. */
fun pageRect(state: JsonElement?): androidx.compose.ui.geometry.Rect? {
    val d = doc(state) ?: return null
    val w = d.num("width")?.toFloat() ?: return null; val h = d.num("height")?.toFloat() ?: return null
    return if (w > 0f && h > 0f) androidx.compose.ui.geometry.Rect(0f, 0f, w, h) else null
}

fun pageIndex(state: JsonElement?): Int = session(state).long("active")?.toInt() ?: 0

/** The tools that are a stroke: the engine paints the whole stroke, with each point's pressure and tilt, when the pen lifts. */
private val STROKES: Map<String, String> = mapOf(
    "Brush" to "paint.stroke", "Eraser" to "paint.stroke", "Pencil" to "paint.pencil", "MixerBrush" to "paint.mixerBrush",
    "ColorReplacement" to "paint.colorReplacement", "BackgroundEraser" to "paint.backgroundEraser", "CloneStamp" to "paint.cloneStamp",
    "PatternStamp" to "paint.patternStamp", "Healing" to "paint.healingBrush", "SpotHealing" to "paint.spotHealing", "Remove" to "paint.remove",
    "HistoryBrush" to "paint.historyBrush", "ArtHistoryBrush" to "paint.artHistoryBrush", "Blur" to "paint.blur", "Sharpen" to "paint.sharpen",
    "Smudge" to "paint.smudge", "Dodge" to "paint.dodge", "Burn" to "paint.burn", "Sponge" to "paint.sponge",
)

/** Tools drawn as a rectangle from where the press starts to where it ends. */
private val BOXES = setOf("RectMarquee", "EllipseMarquee", "Crop", "PerspectiveCrop", "ObjectSelection", "Rectangle", "EllipseShape", "Triangle", "Polygon", "Star")

/** Tools drawn as a line from the press to the release. */
private val LINES = setOf("Gradient", "Move", "Line")

/** Tools drawn as the path of the press. */
private val PATHS = setOf("Lasso", "PolygonLasso", "MagneticLasso", "QuickSelection")

/** The tools that build or edit the work path. */
val PEN_TOOLS = setOf("Pen", "FreeformPen", "CurvaturePen", "AddAnchorPoint", "DeleteAnchorPoint", "ConvertPoint", "PathSelection", "DirectSelection")

/** A selection's mode from the keys held, as on the desktop: Shift adds, Alt subtracts, both intersect. */
private fun selectMode(m: app.loki.kit.CanvasMods): String = when {
    m.shift && m.alt -> "intersect"; m.shift -> "add"; m.alt -> "subtract"
    // No keys held (a finger or a pen has none): what the options bar's Mode says.
    else -> ToolOptions.selectMode
}

private fun colourHex(c: Color): String {
    fun h(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toString(16).padStart(2, '0')
    return "#" + h(c.red) + h(c.green) + h(c.blue)
}

/** One knot of a path being drawn with the Pen: its anchor and two handles, in document pixels. */
class Knot(val x: Double, val y: Double, val inX: Double = x, val inY: Double = y, val outX: Double = x, val outY: Double = y)

/**
 * The path the Pen is drawing, shared by the canvas and the options bar (its Done and Cancel).
 * It lives here until it is finished, then goes to the engine as the work path in one step.
 */
object PenSession {
    val knots = androidx.compose.runtime.mutableStateListOf<Knot>()
    var curvature by androidx.compose.runtime.mutableStateOf(false)

    private fun xy(x: Double, y: Double) = JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))

    /** The knots with smooth handles through them, as the Curvature Pen sets them. */
    private fun curved(k: List<Knot>, closed: Boolean): List<Knot> = k.mapIndexed { i, p ->
        val prev = k.getOrNull(i - 1) ?: if (closed) k.last() else null
        val next = k.getOrNull(i + 1) ?: if (closed) k.first() else null
        if (prev == null || next == null) p else {
            val tx = (next.x - prev.x) / 6.0; val ty = (next.y - prev.y) / 6.0
            Knot(p.x, p.y, p.x - tx, p.y - ty, p.x + tx, p.y + ty)
        }
    }

    fun finish(closed: Boolean, run: (String, JsonObject) -> Unit) {
        val k = if (curvature) curved(knots.toList(), closed) else knots.toList()
        knots.clear()
        if (k.size < 2) return
        run("path.set", buildJsonObject {
            put("name", "work")
            put("path", buildJsonObject {
                put("subpaths", JsonArray(listOf(buildJsonObject {
                    put("closed", closed)
                    put("knots", JsonArray(k.map { n ->
                        buildJsonObject {
                            put("anchor", xy(n.x, n.y)); put("in", xy(n.inX, n.inY)); put("out", xy(n.outX, n.outY))
                            put("smooth", n.inX != n.x || n.inY != n.y || n.outX != n.x || n.outY != n.y)
                        }
                    }))
                })))
            })
        })
    }

    fun cancel() = knots.clear()
}

/** The options bar of the path tools: finish or drop the path being drawn, and what to do with the work path. */
@Composable
private fun PenOptions(state: JsonElement?, run: (String, JsonObject) -> Unit) {
    VerticalDivider(Modifier.height(24.dp))
    if (PenSession.knots.isNotEmpty()) {
        Chip("Done") { PenSession.finish(false, run) }
        Chip("Close path") { PenSession.finish(true, run) }
        Chip("Cancel") { PenSession.cancel() }
    } else if (state.obj()?.get("path.info") != null) {
        val work = buildJsonObject { put("name", "work") }
        Chip("Selection") { run("path.toSelection", work) }
        Chip("Fill") { run("path.fill", work) }
        Chip("Stroke") { run("path.stroke", buildJsonObject { put("name", "work"); put("tool", "brush") }) }
        Chip("Delete path") { run("path.delete", work) }
    } else {
        Text("Tap to add points, drag to pull out curves", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** The work path's knots from `path.info`: per subpath, the anchors with their handles. */
private fun workPath(state: JsonElement?): List<Pair<Boolean, List<Knot>>> {
    val p = state.obj()?.get("path.info").obj()?.get("path")
    return p.arr("subpaths").map { sp ->
        val closed = sp.bool("closed") == true
        closed to sp.arr("knots").mapNotNull { k ->
            fun xy(key: String?): Pair<Double, Double>? {
                val a = (if (key == null) k else k.obj()?.get(key)) as? JsonArray ?: return null
                val x = (a.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
                val y = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
                return x to y
            }
            val anchor = xy("anchor") ?: xy(null) ?: return@mapNotNull null
            val i = xy("in") ?: anchor; val o = xy("out") ?: anchor
            Knot(anchor.first, anchor.second, i.first, i.second, o.first, o.second)
        }
    }
}

/**
 * What a press on the canvas does with the tool in hand. Strokes are kept here, drawn at once as
 * wet ink over the picture, and sent to the engine whole when the pen lifts, so drawing never waits
 * on it. A pen's eraser end erases whatever tool is in hand; its side button (an S Pen's) samples a
 * colour, as an Alt-click does on the desktop. The Pen tools build the work path; the Type tools
 * open a text field where they are tapped.
 */
class CanvasTools {
    /** Bumped while a streamed gesture changes the document, to re-render; PhotoCraft sends whole strokes, so never. */
    var live by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    /** Text being typed on the canvas, if any. */
    var editing by androidx.compose.runtime.mutableStateOf<app.loki.kit.CanvasTextEdit?>(null)
    /** True while there is a selection to march around. */
    var animating by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var ants by androidx.compose.runtime.mutableStateOf(FloatArray(0))
    private val points = androidx.compose.runtime.mutableStateListOf<app.loki.kit.CanvasPoint>()
    private var hover by androidx.compose.runtime.mutableStateOf<app.loki.kit.CanvasPoint?>(null)
    private var active by androidx.compose.runtime.mutableStateOf<String?>(null)
    /** Which work-path anchor Direct Selection is dragging: (subpath, knot). */
    private var grabbed: Pair<Int, Int>? = null
    /** Where the stroke's ink goes first: the display's front buffer where the platform has one (ArtSurface sets it). */
    var ink: app.loki.kit.InkLayer = app.loki.kit.NoInkLayer
    /** True while committed ink waits for the engine's picture of the stroke; [inkCommits] counts strokes, to time a fallback clear. */
    var inkShown = false
    var inkCommits by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private var lastInk: app.loki.kit.CanvasPoint? = null
    /** The Move tool's box on the selected layers (document pixels: left, top, right, bottom), with handles a finger can hit. */
    private var frame by androidx.compose.runtime.mutableStateOf<List<Float>?>(null)
    /** The handle being dragged, where the press was, and where the box's corners are now. */
    private var xGrab by androidx.compose.runtime.mutableStateOf<app.loki.kit.BoxGrab?>(null)
    private var xStart = androidx.compose.ui.geometry.Offset.Zero
    private var xQuad by androidx.compose.runtime.mutableStateOf<List<androidx.compose.ui.geometry.Offset>?>(null)

    private fun frameBox(): app.loki.kit.TransformBox? = frame?.let { f ->
        app.loki.kit.TransformBox(listOf(
            androidx.compose.ui.geometry.Offset(f[0], f[1]), androidx.compose.ui.geometry.Offset(f[2], f[1]),
            androidx.compose.ui.geometry.Offset(f[2], f[3]), androidx.compose.ui.geometry.Offset(f[0], f[3]),
        ))
    }

    /**
     * The Move tool's handles: a press on one scales (Shift keeps the shape, Alt scales about the
     * centre) or, on the knob, turns (Shift in steps of 15°) the selected layers; the engine does it
     * once, when the finger lifts (`edit.transform`). True when the press was one's.
     */
    private fun boxInput(i: app.loki.kit.CanvasInput, ws: app.loki.kit.WorkspaceContext, view: app.loki.kit.CanvasView): Boolean {
        val p = i.points.lastOrNull() ?: return false
        val at = androidx.compose.ui.geometry.Offset(p.x.toFloat(), p.y.toFloat())
        val box = frameBox() ?: return false
        when (i.phase) {
            app.loki.kit.CanvasPhase.Down -> {
                val g = box.grabAt(at, view) ?: return false
                xGrab = g; xStart = box.point(g, view).let { if (g == app.loki.kit.BoxGrab.Rotate) at else it }
                xQuad = box.corners
                return true
            }
            app.loki.kit.CanvasPhase.Drag -> {
                val g = xGrab ?: return false
                xQuad = if (g == app.loki.kit.BoxGrab.Rotate) app.loki.kit.TransformBox.rotated(box.corners, xStart, at, snap = i.mods.shift)
                else app.loki.kit.TransformBox.scaled(box.corners, g, xStart, at, keepAspect = i.mods.shift, fromCenter = i.mods.alt)
                return true
            }
            app.loki.kit.CanvasPhase.Up -> {
                val q = xQuad; val f = frame
                xGrab = null; xQuad = null
                if (q != null && f != null && q.size == 4) ws.run("edit.transform", buildJsonObject {
                    put("rect", JsonArray(f.map { JsonPrimitive(kotlin.math.round(it.toDouble())) }))
                    put("quad", JsonArray(q.map { JsonArray(listOf(JsonPrimitive(it.x.toDouble()), JsonPrimitive(it.y.toDouble()))) }))
                }) {}
                return true
            }
            app.loki.kit.CanvasPhase.Cancel -> { val was = xGrab != null; xGrab = null; xQuad = null; return was }
            else -> return false
        }
    }

    /** The wet ink of a stroke at [p]: width in screen pixels and colour, following the brush and, where it says so, the pen's pressure. */
    private fun wet(tool: String, p: app.loki.kit.CanvasPoint, state: JsonElement?, zoom: Float): Pair<Float, Color> {
        val brush = state.obj()?.get("brush.get")
        val size = (brush.num("size") ?: 20.0).toFloat() * zoom
        val base = when (tool) {
            "Brush", "Pencil", "MixerBrush" -> colourOf(session(state).obj()?.get("foreground")).copy(alpha = (brush.num("opacity") ?: 1.0).toFloat().coerceIn(0.1f, 1f))
            "Eraser", "BackgroundEraser" -> Color.White.copy(alpha = 0.85f)
            else -> Color(0xFF4C9AFF).copy(alpha = 0.5f)
        }
        val width = (if (brush.bool("pressureSize") == true) size * p.pressure else size).coerceAtLeast(1f)
        val colour = if (brush.bool("pressureOpacity") == true) base.copy(alpha = base.alpha * p.pressure.coerceIn(0.05f, 1f)) else base
        return width to colour
    }

    /** The new pieces of the stroke, into the front buffer at once (the canvas overlay draws them where there is none). */
    private fun inkTo(points: List<app.loki.kit.CanvasPoint>, tool: String, state: JsonElement?, view: app.loki.kit.CanvasView) {
        if (!ink.active || tool !in STROKES) return
        for (q in points) {
            val p = lastInk ?: q
            val (w, c) = wet(tool, q, state, view.zoom)
            val a = view.toScreen(p.x, p.y); val b = view.toScreen(q.x, q.y)
            ink.draw(app.loki.kit.InkSegment(a.x, a.y, b.x, b.y, w, c))
            lastInk = q
        }
    }

    fun pans(tool: String) = tool == "Hand"

    /** A finger on a handle of the Move tool's box drags it, even where fingers otherwise only pan (a pen has been used). */
    fun claims(tool: String, x: Double, y: Double, view: app.loki.kit.CanvasView): Boolean =
        tool == "Move" && frameBox()?.grabAt(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()), view) != null

    fun onZoom(ws: app.loki.kit.WorkspaceContext, zoom: Float) {}

    /** The selection's outline, on a grid of about one screen pixel at this zoom. */
    fun onRevision(ws: app.loki.kit.WorkspaceContext, view: app.loki.kit.CanvasView) {
        if (ws.tool == "Move") ws.call("loki.transformFrame", NONE) { r ->
            frame = (r.obj()?.get("frame") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }?.takeIf { it.size == 4 }
        } else frame = null
        if (doc(ws.state).bool("hasSelection") != true) { ants = FloatArray(0); animating = false; return }
        val step = if (view.zoom > 0f) kotlin.math.max(1, (1f / view.zoom).toInt()) else 1
        ws.call("loki.selectionOutline", buildJsonObject { put("step", step) }) { r ->
            ants = (r.obj()?.get("segments") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }?.toFloatArray() ?: FloatArray(0)
            animating = ants.isNotEmpty()
        }
    }

    fun commitText(ws: app.loki.kit.WorkspaceContext, edit: app.loki.kit.CanvasTextEdit, text: String) {
        editing = null
        when {
            edit.target.isNotEmpty() -> ws.run("type.edit", buildJsonObject { put("layer", edit.target.toLongOrNull() ?: return@buildJsonObject); put("text", text) }) {}
            text.isNotBlank() -> ws.run("type.create", buildJsonObject {
                put("x", edit.x); put("y", edit.y); put("text", text); if (ws.tool == "VerticalType") put("orientation", "vertical")
                put("size", ToolOptions.typeSize); put("color", colourHex(colourOf(session(ws.state).obj()?.get("foreground"))))
            }) {}
        }
    }

    fun onInput(i: app.loki.kit.CanvasInput, ws: app.loki.kit.WorkspaceContext, view: app.loki.kit.CanvasView) {
        val pts = i.points
        val tool = ws.tool
        if (tool == "Move" && i.device != app.loki.kit.InputDevice.Eraser && boxInput(i, ws, view)) return
        if (tool in PEN_TOOLS && i.device != app.loki.kit.InputDevice.Eraser) { penInput(i, ws, view); return }
        when (i.phase) {
            app.loki.kit.CanvasPhase.Hover -> hover = pts.lastOrNull()
            app.loki.kit.CanvasPhase.Down -> {
                val p = pts.firstOrNull() ?: return
                if (i.barrel) { sample(ws, p); return }
                active = if (i.device == app.loki.kit.InputDevice.Eraser) "Eraser" else tool
                points.clear(); points.addAll(pts); hover = null
                lastInk = null; inkTo(pts, active.orEmpty(), ws.state, view)
            }
            app.loki.kit.CanvasPhase.Drag -> if (active != null) { points.addAll(pts); hover = pts.lastOrNull(); inkTo(pts, active.orEmpty(), ws.state, view) }
            app.loki.kit.CanvasPhase.Cancel -> { points.clear(); active = null; ink.cancel() }
            app.loki.kit.CanvasPhase.Up -> {
                val t = active ?: return
                points.addAll(pts)
                if (ink.active && t in STROKES) { inkTo(pts, t, ws.state, view); ink.commit(); inkShown = true; inkCommits++ }
                commit(t, points.toList(), i.mods, ws, view)
                points.clear(); active = null
            }
        }
    }

    /** The Pen tools: build the path here, then hand it to the engine; Direct Selection drags the work path's anchors. */
    private fun penInput(i: app.loki.kit.CanvasInput, ws: app.loki.kit.WorkspaceContext, view: app.loki.kit.CanvasView) {
        val p = i.points.lastOrNull() ?: return
        val near = 12f / view.zoom.coerceAtLeast(0.01f)
        val tool = ws.tool
        when (tool) {
            "Pen", "CurvaturePen" -> {
                PenSession.curvature = tool == "CurvaturePen"
                when (i.phase) {
                    app.loki.kit.CanvasPhase.Hover -> hover = p
                    app.loki.kit.CanvasPhase.Down -> {
                        val first = PenSession.knots.firstOrNull()
                        if (first != null && PenSession.knots.size >= 2 && kotlin.math.hypot(p.x - first.x, p.y - first.y) < near) {
                            PenSession.finish(true) { id, params -> ws.run(id, params) {} }
                        } else PenSession.knots += Knot(p.x, p.y)
                    }
                    app.loki.kit.CanvasPhase.Drag -> if (tool == "Pen") {
                        // Dragging after a click pulls out the point's handles, symmetric about it.
                        val k = PenSession.knots.lastOrNull() ?: return
                        PenSession.knots[PenSession.knots.lastIndex] = Knot(k.x, k.y, 2 * k.x - p.x, 2 * k.y - p.y, p.x, p.y)
                    }
                    else -> {}
                }
            }
            "FreeformPen" -> when (i.phase) {
                app.loki.kit.CanvasPhase.Down -> { PenSession.curvature = false; PenSession.knots.clear(); PenSession.knots += Knot(p.x, p.y) }
                app.loki.kit.CanvasPhase.Drag -> for (q in i.points) {
                    val last = PenSession.knots.lastOrNull()
                    if (last == null || kotlin.math.hypot(q.x - last.x, q.y - last.y) > 4f / view.zoom.coerceAtLeast(0.01f)) PenSession.knots += Knot(q.x, q.y)
                }
                app.loki.kit.CanvasPhase.Up -> PenSession.finish(false) { id, params -> ws.run(id, params) {} }
                app.loki.kit.CanvasPhase.Cancel -> PenSession.cancel()
                else -> {}
            }
            "DirectSelection", "PathSelection", "ConvertPoint", "AddAnchorPoint", "DeleteAnchorPoint" -> when (i.phase) {
                app.loki.kit.CanvasPhase.Down -> {
                    grabbed = null
                    workPath(ws.state).forEachIndexed { si, (_, knots) ->
                        knots.forEachIndexed { ki, k -> if (grabbed == null && kotlin.math.hypot(p.x - k.x, p.y - k.y) < near) grabbed = si to ki }
                    }
                    points.clear(); points += p
                    if (grabbed == null && tool == "PathSelection") grabbed = -1 to -1
                }
                app.loki.kit.CanvasPhase.Drag -> points.addAll(i.points)
                app.loki.kit.CanvasPhase.Up -> {
                    val g = grabbed ?: return
                    val a = points.firstOrNull() ?: return
                    val anchors = if (g.first < 0) workPath(ws.state).flatMapIndexed { si, (_, k) -> k.indices.map { si to it } } else listOf(g)
                    ws.run("path.moveAnchors", buildJsonObject {
                        put("name", "work")
                        put("anchors", JsonArray(anchors.map { (si, ki) -> JsonArray(listOf(JsonPrimitive(si), JsonPrimitive(ki))) }))
                        put("move", JsonArray(listOf(JsonPrimitive(p.x - a.x), JsonPrimitive(p.y - a.y))))
                    }) {}
                    grabbed = null; points.clear()
                }
                else -> {}
            }
        }
    }

    private fun sample(ws: app.loki.kit.WorkspaceContext, p: app.loki.kit.CanvasPoint) {
        ws.run("document.sampleColor", buildJsonObject { put("x", p.x); put("y", p.y) }) { rgba ->
            val c = (rgba as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() } ?: return@run
            if (c.size >= 3) ws.run("tools.setColors", buildJsonObject { put("foreground", colourHex(Color(c[0], c[1], c[2]))) }) {}
        }
    }

    private fun commit(tool: String, pts: List<app.loki.kit.CanvasPoint>, mods: app.loki.kit.CanvasMods, ws: app.loki.kit.WorkspaceContext, view: app.loki.kit.CanvasView) {
        val a = pts.firstOrNull() ?: return
        val b = pts.last()
        val x0 = minOf(a.x, b.x); val y0 = minOf(a.y, b.y); val w = kotlin.math.abs(b.x - a.x); val h = kotlin.math.abs(b.y - a.y)
        // A press that hardly moved (in screen pixels) is a click: a selection tool then deselects, a crop does nothing.
        val click = w * view.zoom < 4 && h * view.zoom < 4
        fun rect() = JsonArray(listOf(x0, y0, w, h).map { JsonPrimitive(it) })
        fun xy(p: app.loki.kit.CanvasPoint) = JsonArray(listOf(JsonPrimitive(p.x), JsonPrimitive(p.y)))
        fun at(cmd: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = ws.run(cmd, buildJsonObject { put("x", b.x); put("y", b.y); extra() }) {}
        val stroke = STROKES[tool]
        when {
            stroke != null -> ws.run(stroke, buildJsonObject {
                put("points", JsonArray(pts.map { p ->
                    JsonArray(listOf(p.x, p.y, p.pressure.toDouble(), (p.tiltX ?: 0f).toDouble(), (p.tiltY ?: 0f).toDouble(), (p.rotation ?: 0f).toDouble(), (p.timeMs - a.timeMs).toDouble()).map { JsonPrimitive(it) })
                }))
                if (tool == "Eraser") put("erase", true)
            }) {}
            tool == "RectMarquee" || tool == "EllipseMarquee" ->
                if (click) ws.run("select.deselect", NONE) {}
                else ws.run("select.rect", buildJsonObject {
                    put("x", x0.toInt()); put("y", y0.toInt()); put("width", w.toInt()); put("height", h.toInt())
                    put("mode", selectMode(mods)); if (tool == "EllipseMarquee") put("ellipse", true)
                }) {}
            tool == "SingleRowMarquee" || tool == "SingleColumnMarquee" -> ws.run("select.rect", buildJsonObject {
                put("x", b.x.toInt()); put("y", b.y.toInt()); put("width", 1); put("height", 1)
                put("line", if (tool == "SingleRowMarquee") "row" else "column"); put("mode", selectMode(mods))
            }) {}
            tool in setOf("Lasso", "PolygonLasso", "MagneticLasso") ->
                if (pts.size < 3) ws.run("select.deselect", NONE) {}
                else ws.run("select.lasso", buildJsonObject { put("points", JsonArray(pts.map(::xy))); put("mode", selectMode(mods)) }) {}
            tool == "QuickSelection" -> ws.run("select.quick", buildJsonObject {
                put("points", JsonArray(pts.map(::xy))); put("mode", if (mods.alt) "subtract" else "add")
            }) {}
            tool == "ObjectSelection" -> ws.run("select.object", buildJsonObject {
                if (click) put("point", xy(b)) else put("rect", rect()); put("mode", selectMode(mods))
            }) {}
            tool == "MagicWand" -> at("select.magicWand") { put("mode", selectMode(mods)); put("tolerance", ToolOptions.tolerance); put("contiguous", ToolOptions.contiguous) }
            tool == "Crop" || tool == "PerspectiveCrop" -> if (!click) ws.run("image.crop", buildJsonObject {
                put("x", x0.toInt()); put("y", y0.toInt()); put("width", w.toInt()); put("height", h.toInt())
            }) {}
            tool == "Eyedropper" || tool == "ColorSampler" -> sample(ws, b)
            tool == "PaintBucket" -> at("paint.bucket") { put("tolerance", ToolOptions.tolerance); put("contiguous", ToolOptions.contiguous) }
            tool == "MagicEraser" -> at("paint.magicEraser") { put("tolerance", ToolOptions.tolerance); put("contiguous", ToolOptions.contiguous) }
            tool == "RedEye" -> at("paint.redEye")
            tool == "Gradient" -> if (!click) ws.run("paint.gradient", buildJsonObject { put("from", xy(a)); put("to", xy(b)); put("style", ToolOptions.gradientStyle) }) {}
            tool == "Move" -> if (!click) ws.run("layer.translate", buildJsonObject {
                put("dx", kotlin.math.round(b.x - a.x).toInt()); put("dy", kotlin.math.round(b.y - a.y).toInt())
            }) {}
            tool in setOf("Rectangle", "EllipseShape", "Triangle", "Polygon", "Star") -> if (!click) ws.run("shape.create", buildJsonObject {
                put("kind", when (tool) { "EllipseShape" -> "ellipse"; "Triangle", "Polygon" -> "polygon"; "Star" -> "star"; else -> "rect" })
                put("rect", rect()); if (tool == "Triangle") put("sides", 3) else if (tool == "Polygon" || tool == "Star") put("sides", ToolOptions.sides)
            }) {}
            tool == "Line" -> if (!click) ws.run("shape.create", buildJsonObject { put("kind", "line"); put("from", xy(a)); put("to", xy(b)); put("weight", ToolOptions.lineWeight) }) {}
            tool in setOf("Type", "VerticalType", "HorizontalTypeMask", "VerticalTypeMask") -> {
                // A tap on text edits it; anywhere else starts new text there.
                fun ask(cmd: String, p: JsonObject, then: (JsonElement?) -> Unit) = ws.call("engine.execute", buildJsonObject { put("command", cmd); put("params", p) }, then)
                ask("type.hitTest", buildJsonObject { put("x", b.x); put("y", b.y) }) { hit ->
                    val layer = hit.long("layer")
                    if (hit.bool("inside") == true && layer != null) {
                        ask("type.info", buildJsonObject { put("layer", layer) }) { info ->
                            editing = app.loki.kit.CanvasTextEdit(b.x, b.y, info.str("text") ?: "", layer.toString())
                        }
                    } else editing = app.loki.kit.CanvasTextEdit(b.x, b.y, "")
                }
            }
            tool == "Zoom" -> view.zoomBy(if (mods.alt) 0.8f else 1.25f, view.toScreen(b.x, b.y))
            else -> ws.status("The ${TOOLS.firstOrNull { it.id == tool }?.title ?: tool} does not work on this canvas yet")
        }
    }

    /** The overlay: marching ants, the stroke or shape being drawn, the Pen's path, and the brush under a hovering pen or mouse. */
    fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(view: app.loki.kit.CanvasView, ws: app.loki.kit.WorkspaceContext?, phase: Float) {
        val state = ws?.state
        // Marching ants: a white line under black dashes that move along it.
        val seg = ants
        if (seg.size >= 4) {
            val ant = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), phase % 8f)
            var k = 0
            while (k + 3 < seg.size) {
                val a = view.toScreen(seg[k].toDouble(), seg[k + 1].toDouble()); val b = view.toScreen(seg[k + 2].toDouble(), seg[k + 3].toDouble())
                drawLine(Color.White, a, b, strokeWidth = 1f)
                drawLine(Color.Black, a, b, strokeWidth = 1f, pathEffect = ant)
                k += 4
            }
        }
        val accent = Color(0xFF4C9AFF)
        // The Move tool's box: where the layers are, or where the drag in hand will put them.
        if (ws?.tool == "Move") (xQuad?.let { app.loki.kit.TransformBox(it) } ?: frameBox())?.let { b -> with(b) { draw(view, accent, xGrab) } }
        val tool = active ?: ws?.tool.orEmpty()
        // The work path while a path tool is in hand, and the path the Pen is drawing.
        if (ws?.tool in PEN_TOOLS) {
            fun drawKnots(knots: List<Knot>, closed: Boolean, colour: Color) {
                val path = androidx.compose.ui.graphics.Path()
                knots.forEachIndexed { idx, k ->
                    val p = view.toScreen(k.x, k.y)
                    if (idx == 0) path.moveTo(p.x, p.y) else {
                        val prev = knots[idx - 1]
                        val c1 = view.toScreen(prev.outX, prev.outY); val c2 = view.toScreen(k.inX, k.inY)
                        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
                    }
                }
                if (closed && knots.size > 1) {
                    val last = knots.last(); val first = knots.first()
                    val c1 = view.toScreen(last.outX, last.outY); val c2 = view.toScreen(first.inX, first.inY); val p = view.toScreen(first.x, first.y)
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
                }
                drawPath(path, colour, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                for (k in knots) {
                    val p = view.toScreen(k.x, k.y)
                    drawRect(Color.White, p - androidx.compose.ui.geometry.Offset(3f, 3f), androidx.compose.ui.geometry.Size(6f, 6f))
                    drawRect(colour, p - androidx.compose.ui.geometry.Offset(3f, 3f), androidx.compose.ui.geometry.Size(6f, 6f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    for ((hx, hy) in listOf(k.inX to k.inY, k.outX to k.outY)) if (hx != k.x || hy != k.y) {
                        val h = view.toScreen(hx, hy); drawLine(colour, p, h, strokeWidth = 1f); drawCircle(colour, 3f, h)
                    }
                }
            }
            for ((closed, knots) in workPath(state)) drawKnots(knots, closed, accent.copy(alpha = 0.8f))
            val pen = PenSession.knots.toList()
            if (pen.isNotEmpty()) {
                drawKnots(pen, false, accent)
                hover?.let { h -> val last = pen.last(); drawLine(accent.copy(alpha = 0.6f), view.toScreen(last.x, last.y), view.toScreen(h.x, h.y), strokeWidth = 1f) }
            }
        }
        val brush = state.obj()?.get("brush.get")
        val size = (brush.num("size") ?: 20.0).toFloat() * view.zoom
        val pts = points.toList()
        if (active != null && pts.isNotEmpty()) {
            val a = view.toScreen(pts.first().x, pts.first().y); val b = view.toScreen(pts.last().x, pts.last().y)
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            when {
                // Wet ink in the overlay, where the front buffer did not already draw it.
                tool in STROKES -> if (!ink.active) {
                    for (k in 1 until pts.size) {
                        val p = pts[k - 1]; val q = pts[k]
                        val (w, c) = wet(tool, q, state, view.zoom)
                        drawLine(c, view.toScreen(p.x, p.y), view.toScreen(q.x, q.y), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    if (pts.size == 1) { val (w, c) = wet(tool, pts[0], state, view.zoom); drawCircle(c, w / 2f, a) }
                }
                tool in BOXES || tool == "SingleRowMarquee" || tool == "SingleColumnMarquee" -> {
                    val tl = androidx.compose.ui.geometry.Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                    val sz = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
                    if (tool == "EllipseMarquee" || tool == "EllipseShape") drawOval(accent, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f, pathEffect = dash))
                    else drawRect(accent, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f, pathEffect = dash))
                }
                tool in LINES -> drawLine(accent, a, b, strokeWidth = 1.5f)
                tool in PATHS -> {
                    for (k in 1 until pts.size) drawLine(accent, view.toScreen(pts[k - 1].x, pts[k - 1].y), view.toScreen(pts[k].x, pts[k].y), strokeWidth = 1.5f)
                    if (tool != "QuickSelection") drawLine(accent.copy(alpha = 0.5f), b, a, strokeWidth = 1f, pathEffect = dash)
                }
            }
        }
        // The brush's outline where a pen hovers or the mouse rests, as the desktop's brush cursor.
        val h = hover
        if (active == null && h != null && (ws?.tool in STROKES)) {
            val c = view.toScreen(h.x, h.y)
            drawCircle(Color.White, size / 2f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            drawCircle(Color.Black, size / 2f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(0.75f))
        }
    }
}

// ---- File actions -------------------------------------------------------------------------------

// Open and Save are the desktop front end's (its File menu runs the engine session's doc.open and
// doc.save), so the app adds them to File itself, beside the engine's own Save a Copy, Close…
val APP_ACTIONS: List<app.loki.kit.RibbonItem> = listOf(
    app.loki.kit.RibbonItem("app.open", "Open…", ""),
    app.loki.kit.RibbonItem("app.save", "Save", ""),
    app.loki.kit.RibbonItem("app.saveAs", "Save As…", ""),
    app.loki.kit.RibbonItem("app.exportPng", "Export as PNG…", ""),
)
val APP_ACTION_IDS: Set<String> = APP_ACTIONS.map { it.id }.toSet()

fun withAppActions(tabs: List<app.loki.kit.RibbonTab>): List<app.loki.kit.RibbonTab> = app.loki.kit.addToFileMenu(tabs, APP_ACTIONS)

/** The document's name without an extension, for a file name to suggest. */
private fun baseName(state: JsonElement?) = (doc(state).str("name") ?: "Untitled").substringBeforeLast('.').ifBlank { "Untitled" }

suspend fun appAction(id: String, state: JsonElement?, io: app.loki.kit.FileIo) {
    val path = doc(state).str("path")
    suspend fun saveTo(p: String, command: String, params: JsonObject) {
        if (io.call(command, params) != null) { io.publish(p); io.refresh(); io.status("Saved ${p.substringAfterLast('/')}") }
    }
    when (id) {
        "app.open" -> {
            val p = io.pick(false, "") ?: return
            if (io.call("doc.open", buildJsonObject { put("path", p) }) != null) io.refresh()
        }
        "app.save" -> if (path != null) saveTo(path, "doc.save", JsonObject(emptyMap())) else appAction("app.saveAs", state, io)
        "app.saveAs" -> {
            // PSD keeps the layers and opens anywhere; the extension picked decides the format.
            val p = io.pick(true, "${baseName(state)}.psd") ?: return
            saveTo(p, "doc.save", buildJsonObject { put("path", p) })
        }
        "app.exportPng" -> {
            val p = io.pick(true, "${baseName(state)}.png") ?: return
            saveTo(p, "engine.execute", buildJsonObject { put("command", "file.saveACopy"); put("params", buildJsonObject { put("path", p) }) })
        }
    }
}
