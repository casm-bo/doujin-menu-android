package com.doujinmenu.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class TagStyle { DEFAULT, FEMALE, MALE }

internal data class TagDisplayInfo(val text: String, val style: TagStyle)

internal fun tagDisplayInfo(name: String, type: String? = null): TagDisplayInfo {
    val prefix = name.substringBefore(':', missingDelimiterValue = "").lowercase()
    val style = when {
        prefix == "female" || type.equals("female", ignoreCase = true) -> TagStyle.FEMALE
        prefix == "male" || type.equals("male", ignoreCase = true) -> TagStyle.MALE
        else -> TagStyle.DEFAULT
    }
    return TagDisplayInfo(
        text = if (prefix == "female" || prefix == "male") name.substringAfter(':') else name,
        style = style,
    )
}

internal fun tagSearchFacet(name: String, type: String? = null): String {
    val prefix = name.substringBefore(':', missingDelimiterValue = "").lowercase()
    val namespace = when {
        prefix == "female" || prefix == "male" -> prefix
        type.equals("female", ignoreCase = true) -> "female"
        type.equals("male", ignoreCase = true) -> "male"
        else -> "tag"
    }
    val value = if (prefix == "female" || prefix == "male") name.substringAfter(':') else name
    return "$namespace:$value"
}

@Composable
internal fun CompactTagRow(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tags.forEach { TagPill(tagDisplayInfo(it)) }
    }
}

@Composable
internal fun TagPill(info: TagDisplayInfo, modifier: Modifier = Modifier) {
    val (containerColor, contentColor) = tagColors(info.style)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            info.text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun tagColors(style: TagStyle): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (style) {
        TagStyle.FEMALE -> if (dark) Color(0xFF9D174D) to Color(0xFFFCE7F3)
        else Color(0xFFFCE7F3) to Color(0xFF9D174D)
        TagStyle.MALE -> if (dark) Color(0xFF1E40AF) to Color(0xFFDBEAFE)
        else Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        TagStyle.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
