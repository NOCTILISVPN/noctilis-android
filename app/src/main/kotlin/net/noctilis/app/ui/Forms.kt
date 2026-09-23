package net.noctilis.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Шапка внутреннего экрана: стрелка назад + заголовок (как .sub в кабинете). */
@Composable
fun NHeader(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = p.accent, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
        NHeading(title, 22)
    }
}

/** Поле ввода в стиле кабинета. */
@Composable
fun NField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, minLines: Int = 1) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines,
        placeholder = { Text(placeholder, color = p.muted, fontFamily = BodyFont) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
            focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
            focusedContainerColor = p.card2, unfocusedContainerColor = p.card2,
        ),
        shape = RoundedCornerShape(14.dp), modifier = modifier,
    )
}

/** Строка «ключ — значение» (.kv). */
@Composable
fun NKv(k: String, v: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.muted, fontFamily = BodyFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = p.text, fontFamily = BodyFont, fontSize = 14.sp)
    }
}
