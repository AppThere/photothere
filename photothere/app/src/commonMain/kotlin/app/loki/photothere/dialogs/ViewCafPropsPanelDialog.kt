package app.loki.photothere.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * View caf props panel: translated from `dialogs/caf_ui/view.rs` (fn `caf_props_panel`, line 118) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: (not found)
 *   params:  (unknown)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewCafPropsPanelDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf(false) }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf(false) }
    var f3 by remember { mutableStateOf(false) }
    var f4 by remember { mutableStateOf(false) }
    SheetDialog(
        "View caf props panel", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                put("ui", f0)
                if (f1.isNotBlank()) put("ui", f1)
                put("ui", f2)
                put("ui", f3)
                put("ui", f4)
                }
                /* TODO: which command this dialog runs was not found */
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { f0 = !f0 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f0, { f0 = it })
                Text("Ui")
            }
            OutlinedTextField(f1, { f1 = it }, label = { Text("Color") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable { f2 = !f2 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f2, { f2 = it })
                Text("Indicates")
            }
            Row(Modifier.fillMaxWidth().clickable { f3 = !f3 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f3, { f3 = it })
                Text("Rotation Adaptation")
            }
            Row(Modifier.fillMaxWidth().clickable { f4 = !f4 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f4, { f4 = it })
                Text("Ui")
            }
        }
    }
}
