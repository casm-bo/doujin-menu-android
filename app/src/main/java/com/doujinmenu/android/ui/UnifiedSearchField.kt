package com.doujinmenu.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun UnifiedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onFocus: () -> Unit,
    onSubmit: () -> Unit,
) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val focusManager = LocalFocusManager.current
    fun submit() { focusManager.clearFocus(); onSubmit() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        placeholder = { Text(placeholder, maxLines = 1) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }, modifier = Modifier.semantics { contentDescription = "검색 실행" }) {
                Canvas(Modifier.size(20.dp)) {
                    drawCircle(iconColor, size.width * 0.28f, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(size.width * 0.09f))
                    drawLine(iconColor, Offset(size.width * 0.63f, size.height * 0.63f), Offset(size.width * 0.88f, size.height * 0.88f), size.width * 0.1f)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
