package com.doujinmenu.android.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.doujinmenu.android.R

internal enum class WorkCardSize(val columns: Int, val listCoverWidth: Int, val listScale: Float) {
    LARGE(2, 140, 1.18f), MEDIUM(3, 104, 1f), SMALL(4, 76, .86f);
    companion object { fun fromColumns(columns: Int) = entries.firstOrNull { it.columns == columns } ?: LARGE }
}

internal val LocalWorkCardSize = staticCompositionLocalOf { WorkCardSize.MEDIUM }

@Composable
internal fun WorkViewOptions(grid: Boolean, columns: Int, onMode: (Boolean) -> Unit, onSize: (Int) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    ChoiceMenuItem(strings[R.string.ui_list_view], !grid, onClick = { onMode(false) })
    ChoiceMenuItem(strings[R.string.ui_grid_view], grid, onClick = { onMode(true) })
    HorizontalDivider()
    listOf(2 to R.string.ui_large_2_columns, 3 to R.string.ui_medium_3_columns, 4 to R.string.ui_small_4_columns).forEach { (size, label) ->
        ChoiceMenuItem(strings[label], columns == size, onClick = { onSize(size) })
    }
}
