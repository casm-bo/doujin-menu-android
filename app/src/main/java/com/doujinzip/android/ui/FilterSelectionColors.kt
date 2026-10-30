package com.doujinzip.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
internal fun activeFilterButtonColors(selected: Boolean) = ButtonDefaults.outlinedButtonColors(
    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
)

@Composable
internal fun activeFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
