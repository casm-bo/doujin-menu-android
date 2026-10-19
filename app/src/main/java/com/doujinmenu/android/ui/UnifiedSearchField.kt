package com.doujinmenu.android.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
                ActionIcon(ActionSymbol.SEARCH)
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
