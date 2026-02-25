package me.cniekirk.proxy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    minLines: Int = if (singleLine) 1 else 3,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.75f else 0.35f),
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerField()
                    }
                },
            )
        }
    }
}
