package me.cniekirk.proxy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.CapturedSession

private const val COLUMN_WEIGHT_ID = 0.8f
private const val COLUMN_WEIGHT_METHOD = 1.1f
private const val COLUMN_WEIGHT_URL = 4.8f
private const val COLUMN_WEIGHT_STATUS = 1.4f
private const val COLUMN_WEIGHT_CODE = 2.0f
private const val COLUMN_WEIGHT_TIME = 1.6f
private const val COLUMN_WEIGHT_DURATION = 1.4f
private const val COLUMN_WEIGHT_REQUEST_BYTES = 1.4f
private const val COLUMN_WEIGHT_RESPONSE_BYTES = 1.4f

@Composable
internal fun RequestsTableCard(
    sessions: List<CapturedSession>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No HTTP sessions captured yet. Configure your client to use this proxy and send HTTP traffic.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SessionTableHeader()
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items = sessions, key = { _, session -> session.id }) { index, session ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SessionTableRow(
                                modifier = Modifier.animateItem(),
                                session = session,
                                isSelected = session.id == selectedSessionId,
                                isEvenRow = index % 2 == 0,
                                onClick = { onSelectSession(session.id) },
                            )
                            if (index != sessions.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableHeaderCell("ID", COLUMN_WEIGHT_ID)
        TableHeaderCell("Method", COLUMN_WEIGHT_METHOD)
        TableHeaderCell("URL", COLUMN_WEIGHT_URL)
        TableHeaderCell("Status", COLUMN_WEIGHT_STATUS)
        TableHeaderCell("Code", COLUMN_WEIGHT_CODE)
        TableHeaderCell("Time", COLUMN_WEIGHT_TIME)
        TableHeaderCell("Duration", COLUMN_WEIGHT_DURATION, textAlign = TextAlign.End)
        TableHeaderCell("Request", COLUMN_WEIGHT_REQUEST_BYTES, textAlign = TextAlign.End)
        TableHeaderCell("Response", COLUMN_WEIGHT_RESPONSE_BYTES, textAlign = TextAlign.End)
    }
}

@Composable
private fun SessionTableRow(
    session: CapturedSession,
    isSelected: Boolean,
    isEvenRow: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rowColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isEvenRow -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableBodyCell(displaySessionId(session.id), COLUMN_WEIGHT_ID)
        TableBodyCell(session.request.method.uppercase(), COLUMN_WEIGHT_METHOD, fontWeight = FontWeight.SemiBold)
        TableBodyCell(session.request.url, COLUMN_WEIGHT_URL)
        TableBodyCell(sessionLifecycleStatus(session), COLUMN_WEIGHT_STATUS)

        Box(
            modifier = Modifier
                .weight(COLUMN_WEIGHT_CODE)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ResponseCodeBadge(session = session)
        }

        TableBodyCell(formatCapturedTimeUtc(session.request.timestampEpochMillis), COLUMN_WEIGHT_TIME)
        TableBodyCell(
            formatDuration(session.durationMillis),
            COLUMN_WEIGHT_DURATION,
            textAlign = TextAlign.End,
        )
        TableBodyCell(
            formatBytes(session.request.bodySizeBytes),
            COLUMN_WEIGHT_REQUEST_BYTES,
            textAlign = TextAlign.End,
        )
        TableBodyCell(
            formatBytes(session.response?.bodySizeBytes),
            COLUMN_WEIGHT_RESPONSE_BYTES,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(end = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = textAlign,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowScope.TableBodyCell(
    text: String,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(end = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}

@Composable
private fun ResponseCodeBadge(session: CapturedSession) {
    val response = session.response
    val (containerColor, textColor, label) = when {
        response != null -> {
            val colors = statusCodeColors(response.statusCode)
            Triple(colors.first, colors.second, responseCodeText(response))
        }

        session.error != null -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Error",
        )

        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Pending",
        )
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
