package app.loki.photothere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/** A number the dialog sets with a slider and a field. [decimals] 0 sends an integer, as the engine's params doc asks. */
class AdjustSlider(val key: String, val label: String, val min: Double, val max: Double, val default: Double, val decimals: Int = 0, val log: Boolean = false) {
    /** Where [v] sits on the track, 0..1: a logarithmic slider gives equal room to each doubling (gamma 0.5 to 1 is as wide as 1 to 2). */
    fun position(v: Double): Double = (if (log) (ln(v.coerceIn(min, max)) - ln(min)) / (ln(max) - ln(min)) else (v.coerceIn(min, max) - min) / (max - min))

    fun valueAt(position: Double): Double {
        val p = position.coerceIn(0.0, 1.0)
        return if (log) exp(ln(min) + p * (ln(max) - ln(min))) else min + p * (max - min)
    }
}

class AdjustToggle(val key: String, val label: String)

/** The controls of one Image › Adjustments command, from its params doc (see `engine-commands.json`). */
class AdjustSpec(val command: String, val sliders: List<AdjustSlider>, val toggles: List<AdjustToggle> = emptyList())

/**
 * Adjustments that get a dialog of their own (sliders, a preview) instead of the generic form. Every key and range is the
 * command's own: a test checks them against the params doc, so a change in the engine shows up there.
 */
val ADJUST_SPECS: Map<String, AdjustSpec> = listOf(
    AdjustSpec(
        "image.adjustments.brightnessContrast",
        listOf(AdjustSlider("brightness", "Brightness", -150.0, 150.0, 0.0), AdjustSlider("contrast", "Contrast", -50.0, 100.0, 0.0)),
        listOf(AdjustToggle("legacy", "Use Legacy")),
    ),
    AdjustSpec(
        "image.adjustments.hueSaturation",
        listOf(AdjustSlider("hue", "Hue", -180.0, 180.0, 0.0), AdjustSlider("saturation", "Saturation", -100.0, 100.0, 0.0), AdjustSlider("lightness", "Lightness", -100.0, 100.0, 0.0)),
    ),
    AdjustSpec(
        "image.adjustments.levels",
        listOf(
            AdjustSlider("inBlack", "Input black", 0.0, 253.0, 0.0), AdjustSlider("gamma", "Gamma", 0.01, 9.99, 1.0, decimals = 2, log = true),
            AdjustSlider("inWhite", "Input white", 2.0, 255.0, 255.0),
            AdjustSlider("outBlack", "Output black", 0.0, 255.0, 0.0), AdjustSlider("outWhite", "Output white", 0.0, 255.0, 255.0),
        ),
    ),
).associateBy { it.command }

/** The command's params for these control values, or null when every one is at its default (nothing to apply). */
fun adjustParams(spec: AdjustSpec, values: Map<String, Double>, toggles: Map<String, Boolean>): JsonObject? {
    val atDefault = spec.sliders.all { (values[it.key] ?: it.default) == it.default } && spec.toggles.none { toggles[it.key] == true }
    if (atDefault) return null
    return buildJsonObject {
        for (s in spec.sliders) {
            val v = (values[s.key] ?: s.default).coerceIn(s.min, s.max)
            if (s.decimals == 0) put(s.key, v.roundToLong()) else put(s.key, v)
        }
        for (t in spec.toggles) put(t.key, toggles[t.key] == true)
    }
}

/**
 * The live preview: the engine has no preview command a client can use, so each change undoes the last preview and applies
 * the new settings; OK keeps the last one (one history step), Cancel undoes it. Calls are serialised, and once closed the
 * session ignores a change that was still queued. [apply] says whether the command ran; [undo] undoes one step.
 */
class PreviewSession(private val apply: suspend (JsonObject) -> Boolean, private val undo: suspend () -> Unit) {
    private val lock = Mutex()
    private var closed = false

    /** Whether a preview step is on the document right now. */
    var applied = false
        private set

    private suspend fun replace(params: JsonObject?) {
        if (applied) { undo(); applied = false }
        if (params != null) applied = apply(params)
    }

    /** Show [params] (null: show the untouched image). */
    suspend fun show(params: JsonObject?) = lock.withLock { if (!closed) replace(params) }

    /** OK: leave [params] applied, as one step. */
    suspend fun commit(params: JsonObject?) = lock.withLock { if (!closed) { replace(params); closed = true; applied = false } }

    /** Cancel: the document as it was. */
    suspend fun cancel() = lock.withLock { if (!closed) { replace(null); closed = true } }
}

private fun format(v: Double, decimals: Int) = if (decimals == 0) v.roundToLong().toString() else round(v, decimals).toString()

@OptIn(FlowPreview::class)
@Composable
fun AdjustDialog(
    title: String, subtitle: String, spec: AdjustSpec,
    apply: suspend (JsonObject) -> Boolean, undo: suspend () -> Unit, onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = remember { PreviewSession(apply, undo) }
    val values = remember { mutableStateMapOf<String, Double>().also { m -> spec.sliders.forEach { m[it.key] = it.default } } }
    val toggles = remember { mutableStateMapOf<String, Boolean>() }
    var preview by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    // Each pause in dragging shows the new settings on the canvas; a slider is never applied once per pixel.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(values.toMap(), toggles.toMap(), preview) }.debounce(150).collect { (v, t, on) ->
            session.show(if (on) adjustParams(spec, v, t) else null)
        }
    }
    fun cancel() { if (!busy) { busy = true; scope.launch { session.cancel(); onClose() } } }
    SheetDialog(
        title, subtitle, onDismiss = ::cancel, maxWidth = 460,
        buttons = {
            TextButton(onClick = { spec.sliders.forEach { values[it.key] = it.default }; toggles.clear() }) { Text("Reset") }
            TextButton(onClick = ::cancel) { Text("Cancel") }
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch { session.commit(adjustParams(spec, values.toMap(), toggles.toMap())); onClose() }
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in spec.sliders) {
                val value = values[s.key] ?: s.default
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.labelLarge)
                        Slider(
                            s.position(value).toFloat(), { values[s.key] = round(s.valueAt(it.toDouble()), s.decimals).coerceIn(s.min, s.max) },
                            valueRange = 0f..1f,
                        )
                    }
                    var text by remember(value) { mutableStateOf(format(value, s.decimals)) }
                    OutlinedTextField(
                        text, { t -> text = t; t.toDoubleOrNull()?.let { values[s.key] = it.coerceIn(s.min, s.max) } },
                        singleLine = true, modifier = Modifier.width(92.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            for (t in spec.toggles) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(toggles[t.key] == true, { toggles[t.key] = it })
                Text(t.label)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(preview, { preview = it })
                Text("Preview")
            }
        }
    }
}

private fun round(v: Double, decimals: Int): Double = if (decimals == 0) v.roundToLong().toDouble() else (v * 10.0.pow(decimals)).roundToLong() / 10.0.pow(decimals)
