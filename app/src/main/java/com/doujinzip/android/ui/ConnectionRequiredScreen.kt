package com.doujinzip.android.ui

import com.doujinzip.android.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionRequiredScreen(
    contentPadding: PaddingValues,
    onConnect: () -> Unit,
    title: String? = null,
    description: String? = null,
    buttonLabel: String? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title ?: strings[R.string.ui_pc_connection_required], style = MaterialTheme.typography.headlineSmall)
            Text(
                description ?: strings[R.string.ui_connect_to_the_desktop_app_to_use_pc_search_and_remote_downloa],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onConnect) { Text(buttonLabel ?: strings[R.string.ui_connect_to_pc]) }
        }
    }
}
