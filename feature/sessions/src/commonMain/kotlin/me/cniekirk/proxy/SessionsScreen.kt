package me.cniekirk.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.sebastianneubauer.jsontree.JsonTree
import com.neoutils.highlight.compose.extension.spanStyle
import com.neoutils.highlight.compose.remember.rememberAnnotatedString
import com.neoutils.highlight.compose.remember.rememberHighlight
import org.orbitmvi.orbit.compose.collectAsState

private const val COLUMN_WEIGHT_ID = 0.8f
private const val COLUMN_WEIGHT_METHOD = 1.1f
private const val COLUMN_WEIGHT_URL = 4.8f
private const val COLUMN_WEIGHT_STATUS = 1.4f
private const val COLUMN_WEIGHT_CODE = 2.0f
private const val COLUMN_WEIGHT_TIME = 1.6f
private const val COLUMN_WEIGHT_DURATION = 1.4f
private const val COLUMN_WEIGHT_REQUEST_BYTES = 1.4f
private const val COLUMN_WEIGHT_RESPONSE_BYTES = 1.4f
private const val LOG_PREFIX = "[SessionsScreen]"

private enum class SessionDetailTab(val title: String) {
    Overview("Overview"),
    Headers("Headers"),
    Body("Body"),
}

@Composable
fun SessionsScreen(viewModel: SessionsViewModel) {
    val state by viewModel.collectAsState()
    val selectedSession = state.sessions.firstOrNull { it.id == state.selectedSessionId }

    var requestTab by remember(selectedSession?.id) { mutableStateOf(SessionDetailTab.Overview) }
    var responseTab by remember(selectedSession?.id) { mutableStateOf(SessionDetailTab.Overview) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionsToolbar(
            state = state,
            onClearSessions = viewModel::clearSessions,
        )

        state.runtimeError?.let { runtimeError ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Proxy runtime failed to start", fontWeight = FontWeight.SemiBold)
                    Text(runtimeError)
                    Button(onClick = viewModel::clearRuntimeError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        RequestsTableCard(
            sessions = state.sessions,
            selectedSessionId = state.selectedSessionId,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f),
            onSelectSession = viewModel::selectSession,
        )

        SessionInspector(
            session = selectedSession,
            requestTab = requestTab,
            onRequestTabChange = { requestTab = it },
            responseTab = responseTab,
            onResponseTabChange = { responseTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.56f),
        )
    }
}

@Composable
private fun SessionsToolbar(
    state: SessionsState,
    onClearSessions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Captured Requests", style = MaterialTheme.typography.titleMedium)
            val listeningAddress = state.listeningAddress
            if (listeningAddress != null) {
                val statusText = if (state.isListening) {
                    "Listening on $listeningAddress"
                } else {
                    "Failed to listen on $listeningAddress"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        Button(onClick = onClearSessions, enabled = state.sessions.isNotEmpty()) {
            Text("Clear")
        }
    }
}

@Composable
private fun RequestsTableCard(
    sessions: List<CapturedSession>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No HTTP sessions captured yet. Configure your client to use this proxy and send HTTP traffic.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SessionTableHeader()
                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items = sessions, key = { _, session -> session.id }) { index, session ->
                        SessionTableRow(
                            modifier = Modifier.animateItem(),
                            session = session,
                            isSelected = session.id == selectedSessionId,
                            isEvenRow = index % 2 == 0,
                            onClick = { onSelectSession(session.id) },
                        )
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        isEvenRow -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
            .padding(end = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
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
            .padding(end = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}

@Composable
private fun SessionInspector(
    session: CapturedSession?,
    requestTab: SessionDetailTab,
    onRequestTabChange: (SessionDetailTab) -> Unit,
    responseTab: SessionDetailTab,
    onResponseTabChange: (SessionDetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        if (session == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Select a captured request to inspect request and response details.")
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RequestDetailPane(
                    session = session,
                    selectedTab = requestTab,
                    onTabSelected = onRequestTabChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                ResponseDetailPane(
                    session = session,
                    selectedTab = responseTab,
                    onTabSelected = onResponseTabChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun RequestDetailPane(
    session: CapturedSession,
    selectedTab: SessionDetailTab,
    onTabSelected: (SessionDetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailPane(
        title = "Request",
        subtitle = "${session.request.method} ${session.request.url}",
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    ) { tab ->
        when (tab) {
            SessionDetailTab.Overview -> {
                MetadataTable(
                    entries = listOf(
                        "Method" to session.request.method,
                        "URL" to session.request.url,
                        "Captured (UTC)" to formatCapturedTimeUtc(session.request.timestampEpochMillis),
                        "Body Type" to session.request.bodyType.name,
                        "Body Size" to formatBytes(session.request.bodySizeBytes),
                        "Headers" to session.request.headers.size.toString(),
                    ),
                )
            }

            SessionDetailTab.Headers -> HeaderTable(session.request.headers)
            SessionDetailTab.Body -> {
                BodyBlock(
                    body = session.request.body,
                    bodyType = session.request.bodyType,
                )
            }
        }
    }
}

@Composable
private fun ResponseDetailPane(
    session: CapturedSession,
    selectedTab: SessionDetailTab,
    onTabSelected: (SessionDetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val response = session.response
    DetailPane(
        title = "Response",
        subtitle = response?.let(::responseCodeText) ?: "No response captured",
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    ) { tab ->
        if (response == null) {
            when (tab) {
                SessionDetailTab.Overview -> {
                    val entries = buildList {
                        add("Status" to "Pending")
                        session.error?.let { add("Error" to it) }
                    }
                    MetadataTable(entries)
                }

                SessionDetailTab.Headers,
                SessionDetailTab.Body,
                -> Text("No upstream response was captured.")
            }
            return@DetailPane
        }

        when (tab) {
            SessionDetailTab.Overview -> {
                val entries = buildList {
                    add("Status" to responseCodeText(response))
                    add("Captured (UTC)" to formatCapturedTimeUtc(response.timestampEpochMillis))
                    add("Duration" to formatDuration(session.durationMillis))
                    add("Body Type" to response.bodyType.name)
                    add("Body Size" to formatBytes(response.bodySizeBytes))
                    add("Headers" to response.headers.size.toString())
                    session.error?.let { add("Warning" to it) }
                }
                MetadataTable(entries = entries)
            }

            SessionDetailTab.Headers -> HeaderTable(response.headers)
            SessionDetailTab.Body -> {
                BodyBlock(
                    body = response.body,
                    imageBytes = response.imageBytes,
                    bodyType = response.bodyType,
                )
            }
        }
    }
}

@Composable
private fun DetailPane(
    title: String,
    subtitle: String,
    selectedTab: SessionDetailTab,
    onTabSelected: (SessionDetailTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SessionDetailTab) -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val tabs = SessionDetailTab.entries
            SecondaryTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                tabs = {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = { Text(tab.title) },
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content(selectedTab)
            }
        }
    }
}

@Composable
private fun MetadataTable(entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) {
        Text("No metadata")
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Key",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Value",
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEachIndexed { index, (key, value) ->
                val rowColor = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = key,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = value,
                        modifier = Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderTable(headers: List<HeaderEntry>) {
    if (headers.isEmpty()) {
        Text("No headers")
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Header",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Value",
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            headers.forEachIndexed { index, header ->
                val rowColor = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = header.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = header.value,
                        modifier = Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
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
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun statusCodeColors(statusCode: Int): Pair<Color, Color> {
    return when (statusCode / 100) {
        2 -> Color(0xFFDCFCE7) to Color(0xFF166534)
        3 -> Color(0xFFF1F5F9) to Color(0xFF334155)
        4 -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
        5 -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> Color(0xFFE2E8F0) to Color(0xFF334155)
    }
}

private fun sessionLifecycleStatus(session: CapturedSession): String {
    return when {
        session.response != null -> "Completed"
        session.error != null -> "Error"
        else -> "Pending"
    }
}

private fun responseCodeText(response: CapturedResponse): String {
    val reasonPhrase = response.reasonPhrase
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: defaultReasonPhrase(response.statusCode)

    return if (reasonPhrase.isNotEmpty()) {
        "${response.statusCode} $reasonPhrase"
    } else {
        response.statusCode.toString()
    }
}

private fun defaultReasonPhrase(statusCode: Int): String {
    return when (statusCode) {
        100 -> "Continue"
        101 -> "Switching Protocols"
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        206 -> "Partial Content"
        300 -> "Multiple Choices"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        418 -> "I'm a Teapot"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> ""
    }
}

private fun formatDuration(durationMillis: Long?): String {
    val value = durationMillis ?: return "-"
    if (value < 1_000) {
        return "$value ms"
    }

    val wholeSeconds = value / 1_000
    val hundredths = (value % 1_000) / 10
    return "$wholeSeconds.${hundredths.toString().padStart(2, '0')} s"
}

private fun formatBytes(value: Long?): String {
    val bytes = value ?: return "-"
    if (bytes < 1024) {
        return "$bytes B"
    }

    val kib = bytes / 1024.0
    if (kib < 1024.0) {
        return "${formatWithSingleDecimal(kib)} KB"
    }

    val mib = kib / 1024.0
    return "${formatWithSingleDecimal(mib)} MB"
}

private fun formatWithSingleDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val decimal = rounded % 10
    return "$whole.$decimal"
}

private fun formatCapturedTimeUtc(timestampEpochMillis: Long): String {
    val millisecondsPerDay = 24L * 60L * 60L * 1000L
    val msOfDay = ((timestampEpochMillis % millisecondsPerDay) + millisecondsPerDay) % millisecondsPerDay

    val hours = (msOfDay / 3_600_000L).toInt()
    val minutes = ((msOfDay % 3_600_000L) / 60_000L).toInt()
    val seconds = ((msOfDay % 60_000L) / 1_000L).toInt()
    val millis = (msOfDay % 1_000L).toInt()

    return "${hours.toString().padStart(2, '0')}:" +
        "${minutes.toString().padStart(2, '0')}:" +
        "${seconds.toString().padStart(2, '0')}." +
        millis.toString().padStart(3, '0')
}

private fun displaySessionId(sessionId: String): String {
    if (sessionId.length <= 6) {
        return sessionId
    }
    return sessionId.takeLast(6)
}

@Composable
private fun BodyBlock(
    body: String?,
    imageBytes: ByteArray? = null,
    bodyType: CapturedBodyType,
) {
    if (imageBytes != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageBytes)
                .build(),
            contentDescription = "Captured response image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        )
        return
    }

    if (body.isNullOrBlank()) {
        Text("No body")
        return
    }

    if (bodyType == CapturedBodyType.Json) {
        JsonBodyTree(body)
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val highlight = rememberHighlight(
        bodyType,
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        colorScheme.onSurfaceVariant,
    ) {
        when (bodyType) {
            CapturedBodyType.Xml,
            CapturedBodyType.Html,
            -> {
                spanStyle {
                    "<!--[\\s\\S]*?-->".toRegex().fully(
                        SpanStyle(
                            color = colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }
                spanStyle {
                    "</?\\s*[A-Za-z0-9:_-]+".toRegex().fully(
                        SpanStyle(
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                spanStyle {
                    "\\s[A-Za-z_:][-A-Za-z0-9_:.]*(?=\\s*=)".toRegex().fully(
                        SpanStyle(color = colorScheme.secondary),
                    )
                }
                spanStyle {
                    "\"[^\"]*\"|'[^']*'".toRegex().fully(
                        SpanStyle(color = colorScheme.tertiary),
                    )
                }
                spanStyle {
                    "/?>".toRegex().fully(
                        SpanStyle(color = colorScheme.onSurfaceVariant),
                    )
                }
            }

            CapturedBodyType.PlainText -> Unit
            CapturedBodyType.Json -> Unit
        }
    }

    Text(
        text = highlight.rememberAnnotatedString(body),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun JsonBodyTree(body: String) {
    var showRawText by remember(body) { mutableStateOf(false) }
    if (showRawText) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    JsonTree(
        json = body,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
        onLoading = {
            Text(
                text = "Loading JSON...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onError = {
            println("ERROR: ${it.localizedMessage}")
            showRawText = true
        },
    )
}
