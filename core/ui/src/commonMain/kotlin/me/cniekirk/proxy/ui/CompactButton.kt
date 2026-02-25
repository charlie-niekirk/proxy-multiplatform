package me.cniekirk.proxy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class CompactButtonStyle {
    Outlined,
    Text,
    Filled,
}

@Composable
fun CompactButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CompactButtonStyle = CompactButtonStyle.Outlined,
) {
    val (containerColor, contentColor, borderColor) = when (style) {
        CompactButtonStyle.Outlined -> {
            Triple(
                first = if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                second = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                third = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.35f),
            )
        }

        CompactButtonStyle.Text -> {
            Triple(
                first = Color.Transparent,
                second = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                third = Color.Transparent,
            )
        }

        CompactButtonStyle.Filled -> {
            Triple(
                first = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                },
                second = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                third = if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                },
            )
        }
    }

    Surface(
        modifier = modifier.then(
            if (enabled) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = borderColor
            .takeUnless { color -> color == Color.Transparent }
            ?.let { color -> BorderStroke(1.dp, color) },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (style == CompactButtonStyle.Text) 6.dp else 10.dp,
                vertical = 4.dp,
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}
