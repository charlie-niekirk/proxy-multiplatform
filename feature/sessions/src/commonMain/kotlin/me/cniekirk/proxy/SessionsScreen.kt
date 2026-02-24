package me.cniekirk.proxy

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
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
private val SIDEBAR_WIDTH = 230.dp

private data class DomainSummary(
    val host: String,
    val count: Int,
)

private data class UrlSegments(
    val protocol: String?,
    val host: String,
    val path: String,
    val suffix: String,
)

private enum class SessionDetailTab(val title: String) {
    Overview("Overview"),
    Headers("Headers"),
    Body("Body"),
}

@Composable
fun SessionsScreen(viewModel: SessionsViewModel) {
    val state by viewModel.collectAsState()
    val domainSummaries = remember(state.sessions) { buildDomainSummaries(state.sessions) }
    var selectedDomain by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(domainSummaries, selectedDomain) {
        if (selectedDomain != null && domainSummaries.none { summary -> summary.host == selectedDomain }) {
            selectedDomain = null
        }
    }

    val visibleSessions = remember(state.sessions, selectedDomain) {
        if (selectedDomain == null) {
            state.sessions
        } else {
            state.sessions.filter { session -> extractRequestHost(session.request.url) == selectedDomain }
        }
    }
    val selectedSession = visibleSessions.firstOrNull { session -> session.id == state.selectedSessionId }
        ?: visibleSessions.firstOrNull()

    LaunchedEffect(selectedSession?.id, state.selectedSessionId) {
        val selectedId = selectedSession?.id ?: return@LaunchedEffect
        if (selectedId != state.selectedSessionId) {
            viewModel.selectSession(selectedId)
        }
    }

    var requestTab by remember(selectedSession?.id) { mutableStateOf(SessionDetailTab.Overview) }
    var responseTab by remember(selectedSession?.id) { mutableStateOf(SessionDetailTab.Overview) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SessionsToolbar(
            state = state,
            onClearSessions = viewModel::clearSessions,
        )

        state.runtimeError?.let { runtimeError ->
            RuntimeErrorBanner(
                message = runtimeError,
                onDismiss = viewModel::clearRuntimeError,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DomainSidebar(
                domains = domainSummaries,
                selectedDomain = selectedDomain,
                totalCount = state.sessions.size,
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .fillMaxHeight(),
                onDomainSelected = { selectedDomain = it },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RequestsTableCard(
                    sessions = visibleSessions,
                    selectedSessionId = selectedSession?.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f),
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
                        .weight(0.58f),
                )
            }
        }
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
            Text(
                text = "Captured Requests",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val listeningAddress = state.listeningAddress
            if (listeningAddress != null) {
                val statusText = if (state.isListening) {
                    "Listening on $listeningAddress"
                } else {
                    "Failed to listen on $listeningAddress"
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

        CompactActionButton(
            label = "Clear",
            enabled = state.sessions.isNotEmpty(),
            onClick = onClearSessions,
        )
    }
}

@Composable
private fun RuntimeErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
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
                    text = "Proxy runtime failed to start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            CompactActionButton(
                label = "Dismiss",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DomainSidebar(
    domains: List<DomainSummary>,
    selectedDomain: String?,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onDomainSelected: (String?) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Domains",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    DomainSidebarRow(
                        label = "All",
                        count = totalCount,
                        isSelected = selectedDomain == null,
                        onClick = { onDomainSelected(null) },
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }

                items(
                    items = domains,
                    key = { summary -> summary.host },
                ) { domain ->
                    DomainSidebarRow(
                        label = domain.host,
                        count = domain.count,
                        isSelected = selectedDomain == domain.host,
                        onClick = { onDomainSelected(domain.host) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainSidebarRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        if (session == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Select a captured request to inspect request and response details.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                SessionSummaryBar(
                    session = session,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    RequestDetailPane(
                        session = session,
                        selectedTab = requestTab,
                        onTabSelected = onRequestTabChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
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
}

@Composable
private fun SessionSummaryBar(
    session: CapturedSession,
    modifier: Modifier = Modifier,
) {
    val methodText = session.request.method.uppercase()
    val response = session.response
    val statusLabel = when {
        response != null -> responseCodeText(response)
        session.error != null -> "Error"
        else -> "Pending"
    }
    val neutralBadgeContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val neutralBadgeText = MaterialTheme.colorScheme.onSurfaceVariant
    val (statusBadgeContainer, statusBadgeTextColor) = when {
        response != null -> statusCodeColors(response.statusCode)
        session.error != null -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val protocolColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hostColor = MaterialTheme.colorScheme.primary
    val pathColor = Color(0xFF2F8B57)
    val suffixColor = MaterialTheme.colorScheme.onSurfaceVariant
    val urlText = buildColorizedUrlText(
        url = session.request.url,
        protocolColor = protocolColor,
        hostColor = hostColor,
        pathColor = pathColor,
        suffixColor = suffixColor,
    )

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SessionSummaryBadge(
            text = methodText,
            containerColor = neutralBadgeContainer,
            textColor = neutralBadgeText,
        )
        SessionSummaryBadge(
            text = statusLabel,
            containerColor = statusBadgeContainer,
            textColor = statusBadgeTextColor,
        )
        Text(
            text = urlText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionSummaryBadge(
    text: String,
    containerColor: Color,
    textColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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

            CompactTabStrip(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content(selectedTab)
            }
        }
    }
}

@Composable
private fun CompactTabStrip(
    selectedTab: SessionDetailTab,
    onTabSelected: (SessionDetailTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SessionDetailTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                    } else {
                        Color.Transparent
                    },
                ),
                modifier = Modifier.clickable { onTabSelected(tab) },
            ) {
                Text(
                    text = tab.title,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
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
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
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
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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
private fun CompactActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.35f),
        ),
        modifier = if (enabled) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun buildDomainSummaries(sessions: List<CapturedSession>): List<DomainSummary> {
    val countsByDomain = linkedMapOf<String, Int>()
    sessions.forEach { session ->
        val host = extractRequestHost(session.request.url)
        countsByDomain[host] = (countsByDomain[host] ?: 0) + 1
    }

    return countsByDomain.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { entry -> entry.value }.thenBy { entry -> entry.key })
        .map { entry ->
            DomainSummary(
                host = entry.key,
                count = entry.value,
            )
        }
}

private fun extractRequestHost(url: String): String {
    val authority = url
        .substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val hostAndPort = authority.substringAfter('@', authority)
    val host = when {
        hostAndPort.startsWith("[") -> hostAndPort.substringAfter('[').substringBefore(']')
        else -> hostAndPort.substringBefore(':')
    }.trim().lowercase()

    return host.ifEmpty { "(unknown)" }
}

private fun buildColorizedUrlText(
    url: String,
    protocolColor: Color,
    hostColor: Color,
    pathColor: Color,
    suffixColor: Color,
) = buildAnnotatedString {
    val segments = splitUrlSegments(url)
    segments.protocol?.let { protocol ->
        withStyle(SpanStyle(color = protocolColor)) {
            append(protocol)
            append("://")
        }
    }
    withStyle(SpanStyle(color = hostColor, fontWeight = FontWeight.SemiBold)) {
        append(segments.host)
    }
    withStyle(SpanStyle(color = pathColor)) {
        append(segments.path)
    }
    if (segments.suffix.isNotEmpty()) {
        withStyle(SpanStyle(color = suffixColor)) {
            append(segments.suffix)
        }
    }
}

private fun splitUrlSegments(url: String): UrlSegments {
    val schemeDelimiter = "://"
    val hasScheme = url.contains(schemeDelimiter)
    val protocol = if (hasScheme) url.substringBefore(schemeDelimiter) else null
    val withoutScheme = if (hasScheme) {
        url.substringAfter(schemeDelimiter)
    } else {
        url
    }

    val boundaryIndex = withoutScheme.indexOfAny(charArrayOf('/', '?', '#'))
    val rawHost = if (boundaryIndex >= 0) {
        withoutScheme.substring(0, boundaryIndex)
    } else {
        withoutScheme
    }
    val remainder = if (boundaryIndex >= 0) {
        withoutScheme.substring(boundaryIndex)
    } else {
        ""
    }

    val host = rawHost
        .substringAfter('@', rawHost)
        .ifBlank { "(unknown)" }
    val suffixStartIndex = remainder.indexOfAny(charArrayOf('?', '#'))
    val path = when {
        remainder.isEmpty() -> "/"
        suffixStartIndex < 0 -> remainder
        suffixStartIndex == 0 -> "/"
        else -> remainder.substring(0, suffixStartIndex)
    }
    val suffix = when {
        remainder.isEmpty() -> ""
        suffixStartIndex < 0 -> ""
        else -> remainder.substring(suffixStartIndex)
    }

    return UrlSegments(
        protocol = protocol,
        host = host,
        path = path,
        suffix = suffix,
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
    JsonTreeView(
        json = body,
        modifier = Modifier.fillMaxWidth(),
    )
}
