package me.cniekirk.proxy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.SessionsState
import me.cniekirk.proxy.ui.CompactButton
import org.jetbrains.compose.resources.stringResource
import proxy.feature.sessions.generated.resources.*

@Composable
internal fun SessionsToolbar(
    state: SessionsState,
    onClearSessions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.sessions_toolbar_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            val listeningAddress = state.listeningAddress
            if (listeningAddress != null) {
                val statusText = if (state.isListening) {
                    stringResource(
                        Res.string.sessions_toolbar_listening_on,
                        listeningAddress,
                    )
                } else {
                    stringResource(
                        Res.string.sessions_toolbar_failed_listen,
                        listeningAddress,
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        CompactButton(
            label = stringResource(Res.string.sessions_toolbar_clear),
            enabled = state.sessions.isNotEmpty(),
            onClick = onClearSessions,
        )
    }
}

@Composable
internal fun RuntimeErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    val resolvedMessage = message.ifBlank {
        stringResource(Res.string.sessions_runtime_error_unknown)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.sessions_runtime_error_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            CompactButton(
                label = stringResource(Res.string.sessions_action_dismiss),
                onClick = onDismiss,
            )
        }
    }
}
