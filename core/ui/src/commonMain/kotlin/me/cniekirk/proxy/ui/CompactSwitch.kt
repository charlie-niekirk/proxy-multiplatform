package me.cniekirk.proxy.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor = when {
        checked && enabled -> MaterialTheme.colorScheme.primary
        checked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        enabled -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val trackBorderColor = when {
        checked && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        checked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val thumbColor = when {
        checked && enabled -> MaterialTheme.colorScheme.onPrimary
        checked -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) CompactSwitchCheckedThumbOffset else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "compact_switch_thumb_offset",
    )

    Surface(
        modifier = modifier
            .size(width = CompactSwitchTrackWidth, height = CompactSwitchTrackHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = RoundedCornerShape(percent = 50),
        color = trackColor,
        border = BorderStroke(1.dp, trackBorderColor),
    ) {
        Box(modifier = Modifier.padding(CompactSwitchTrackPadding)) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(CompactSwitchThumbSize)
                    .background(color = thumbColor, shape = CircleShape),
            )
        }
    }
}

private val CompactSwitchTrackWidth = 30.dp
private val CompactSwitchTrackHeight = 18.dp
private val CompactSwitchTrackPadding = 2.dp
private val CompactSwitchThumbSize = 12.dp
private val CompactSwitchCheckedThumbOffset = 14.dp
