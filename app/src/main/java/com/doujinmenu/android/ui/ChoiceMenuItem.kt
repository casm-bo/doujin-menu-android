package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ChoiceMenuItem(label: String, checked: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { RadioButton(selected = checked, onClick = null, modifier = Modifier.size(20.dp), enabled = enabled) },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { selected = checked },
    )
}
