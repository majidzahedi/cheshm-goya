package ir.cheshmgoya.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { TextButton(onClick = onBack) { Text("→ بازگشت") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    HorizontalDivider()
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, steps: Int = 0, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ${format(local)}", fontSize = 18.sp)
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range, steps = steps)
    }
}

@Composable
fun <T> RadioGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        options.forEach { o ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = o == selected, onClick = { onSelect(o) }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = o == selected, onClick = { onSelect(o) })
                Text(label(o), fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun TextSetting(label: String, value: String, secret: Boolean = false, phone: Boolean = false, onSave: (String) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = local, onValueChange = { local = it }, label = { Text(label) }, singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else if (secret) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSave(local.trim()) }, enabled = local != value, modifier = Modifier.padding(start = 8.dp)) { Text("ذخیره") }
    }
}
