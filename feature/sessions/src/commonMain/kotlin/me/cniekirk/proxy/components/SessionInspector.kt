package me.cniekirk.proxy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.neoutils.highlight.compose.extension.spanStyle
import com.neoutils.highlight.compose.remember.rememberAnnotatedString
import com.neoutils.highlight.compose.remember.rememberHighlight
import me.cniekirk.proxy.AppliedRuleTrace
import me.cniekirk.proxy.CapturedBodyType
import me.cniekirk.proxy.CapturedSession
import me.cniekirk.proxy.HeaderEntry
import me.cniekirk.proxy.JsonTreeView
import me.cniekirk.proxy.WebSocketDirection
import me.cniekirk.proxy.WebSocketMessage
import me.cniekirk.proxy.WebSocketOpcode
import org.jetbrains.compose.resources.stringResource
import proxy.feature.sessions.generated.resources.*

@Composable
internal fun SessionInspector(
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
                    text = stringResource(Res.string.sessions_inspector_empty),
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
        session.error != null -> stringResource(Res.string.sessions_status_error)
        else -> stringResource(Res.string.sessions_status_pending)
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
    val isWs = session.isWebSocketSession()
    val availableTabs = remember(isWs) {
        if (isWs) SessionDetailTab.entries.toList()
        else SessionDetailTab.entries.filter { it != SessionDetailTab.Websocket }
    }
    DetailPane(
        title = stringResource(Res.string.sessions_request_title),
        subtitle = stringResource(
            Res.string.sessions_request_subtitle,
            session.request.method,
            session.request.url,
        ),
        tabs = availableTabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    ) { tab ->
        when (tab) {
            SessionDetailTab.Overview -> {
                val entries = buildList {
                    add(stringResource(Res.string.sessions_metadata_method) to session.request.method)
                    add(stringResource(Res.string.sessions_metadata_url) to session.request.url)
                    add(
                        stringResource(Res.string.sessions_metadata_captured_utc) to
                            formatCapturedTimeUtc(session.request.timestampEpochMillis),
                    )
                    add(stringResource(Res.string.sessions_metadata_body_type) to session.request.bodyType.name)
                    add(
                        stringResource(Res.string.sessions_metadata_body_size) to
                            formatBytes(session.request.bodySizeBytes),
                    )
                    add(
                        stringResource(Res.string.sessions_metadata_headers) to
                            session.request.headers.size.toString(),
                    )

                    val requestRuleCount = session.appliedRules.count { trace -> trace.appliedToRequest }
                    add(
                        stringResource(Res.string.sessions_metadata_applied_request_rules) to
                            requestRuleCount.toString(),
                    )
                    if (requestRuleCount > 0) {
                        add(
                            stringResource(Res.string.sessions_metadata_request_rule_trace) to
                                formatAppliedRuleTrace(
                                    traces = session.appliedRules,
                                    requestScope = true,
                                ),
                        )
                    }
                }
                MetadataTable(entries = entries)
            }

            SessionDetailTab.Headers -> HeaderTable(session.request.headers)
            SessionDetailTab.Cookies -> RequestCookieTable(session.request.headers)
            SessionDetailTab.Body -> {
                BodyBlock(
                    body = session.request.body,
                    bodyType = session.request.bodyType,
                )
            }
            SessionDetailTab.Websocket -> WebSocketMessageTable(session.webSocketMessages)
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
    val isWs = session.isWebSocketSession()
    val availableTabs = remember(isWs) {
        if (isWs) SessionDetailTab.entries.toList()
        else SessionDetailTab.entries.filter { it != SessionDetailTab.Websocket }
    }
    DetailPane(
        title = stringResource(Res.string.sessions_response_title),
        subtitle = response?.let { capturedResponse -> responseCodeText(capturedResponse) }
            ?: stringResource(Res.string.sessions_response_no_response_subtitle),
        tabs = availableTabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    ) { tab ->
        if (tab == SessionDetailTab.Websocket) {
            WebSocketMessageTable(session.webSocketMessages)
            return@DetailPane
        }

        if (response == null) {
            when (tab) {
                SessionDetailTab.Overview -> {
                    val entries = buildList {
                        add(
                            stringResource(Res.string.sessions_metadata_status) to
                                stringResource(Res.string.sessions_status_pending),
                        )
                        session.error?.let {
                            add(stringResource(Res.string.sessions_metadata_error) to it)
                        }
                    }
                    MetadataTable(entries)
                }

                SessionDetailTab.Headers,
                SessionDetailTab.Cookies,
                SessionDetailTab.Body,
                SessionDetailTab.Websocket,
                -> Text(stringResource(Res.string.sessions_no_upstream_response))
            }
            return@DetailPane
        }

        when (tab) {
            SessionDetailTab.Overview -> {
                val entries = buildList {
                    add(
                        stringResource(Res.string.sessions_metadata_status) to
                            responseCodeText(response),
                    )
                    add(
                        stringResource(Res.string.sessions_metadata_captured_utc) to
                            formatCapturedTimeUtc(response.timestampEpochMillis),
                    )
                    add(
                        stringResource(Res.string.sessions_metadata_duration) to
                            formatDuration(session.durationMillis),
                    )
                    add(stringResource(Res.string.sessions_metadata_body_type) to response.bodyType.name)
                    add(
                        stringResource(Res.string.sessions_metadata_body_size) to
                            formatBytes(response.bodySizeBytes),
                    )
                    add(
                        stringResource(Res.string.sessions_metadata_headers) to
                            response.headers.size.toString(),
                    )
                    val responseRuleCount = session.appliedRules.count { trace -> trace.appliedToResponse }
                    add(
                        stringResource(Res.string.sessions_metadata_applied_response_rules) to
                            responseRuleCount.toString(),
                    )
                    if (responseRuleCount > 0) {
                        add(
                            stringResource(Res.string.sessions_metadata_response_rule_trace) to
                                formatAppliedRuleTrace(
                                    traces = session.appliedRules,
                                    requestScope = false,
                                ),
                        )
                    }
                    session.error?.let {
                        add(stringResource(Res.string.sessions_metadata_warning) to it)
                    }
                }
                MetadataTable(entries = entries)
            }

            SessionDetailTab.Headers -> HeaderTable(response.headers)
            SessionDetailTab.Cookies -> SetCookieTable(response.headers)
            SessionDetailTab.Body -> {
                BodyBlock(
                    body = response.body,
                    imageBytes = response.imageBytes,
                    bodyType = response.bodyType,
                )
            }
            SessionDetailTab.Websocket -> WebSocketMessageTable(session.webSocketMessages)
        }
    }
}

@Composable
private fun DetailPane(
    title: String,
    subtitle: String,
    tabs: List<SessionDetailTab>,
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
                tabs = tabs,
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
    tabs: List<SessionDetailTab>,
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
        tabs.forEach { tab ->
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
                    text = sessionDetailTabTitle(tab),
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
private fun sessionDetailTabTitle(tab: SessionDetailTab): String {
    return when (tab) {
        SessionDetailTab.Overview -> stringResource(Res.string.sessions_detail_tab_overview)
        SessionDetailTab.Headers -> stringResource(Res.string.sessions_detail_tab_headers)
        SessionDetailTab.Cookies -> stringResource(Res.string.sessions_detail_tab_cookies)
        SessionDetailTab.Body -> stringResource(Res.string.sessions_detail_tab_body)
        SessionDetailTab.Websocket -> stringResource(Res.string.sessions_detail_tab_websocket)
    }
}

private fun CapturedSession.isWebSocketSession(): Boolean {
    return webSocketMessages.isNotEmpty() ||
        (
            request.headers.any { it.name.equals("Upgrade", ignoreCase = true) && it.value.equals("websocket", ignoreCase = true) } &&
                response?.statusCode == 101
        )
}

@Composable
private fun WebSocketMessageTable(messages: List<WebSocketMessage>) {
    if (messages.isEmpty()) {
        Text(stringResource(Res.string.sessions_websocket_no_messages))
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
                    text = stringResource(Res.string.sessions_websocket_table_direction),
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_websocket_table_opcode),
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_websocket_table_size),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_websocket_table_payload),
                    modifier = Modifier.weight(2.3f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            messages.forEachIndexed { index, message ->
                val rowColor = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                }
                val directionLabel = when (message.direction) {
                    WebSocketDirection.ClientToServer ->
                        stringResource(Res.string.sessions_websocket_direction_client_to_server)
                    WebSocketDirection.ServerToClient ->
                        stringResource(Res.string.sessions_websocket_direction_server_to_client)
                }
                val directionColor = when (message.direction) {
                    WebSocketDirection.ClientToServer -> Color(0xFF1E40AF)
                    WebSocketDirection.ServerToClient -> Color(0xFF166534)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = directionLabel,
                        modifier = Modifier.weight(1.2f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = directionColor,
                    )
                    Text(
                        text = message.opcode.name,
                        modifier = Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = formatBytes(message.payloadSizeBytes.toLong()),
                        modifier = Modifier.weight(0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = message.payloadText,
                        modifier = Modifier.weight(2.3f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataTable(entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) {
        Text(stringResource(Res.string.sessions_table_no_metadata))
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
                    text = stringResource(Res.string.sessions_table_key),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_table_value),
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
        Text(stringResource(Res.string.sessions_table_no_headers))
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
                    text = stringResource(Res.string.sessions_table_header_name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_table_value),
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
private fun RequestCookieTable(headers: List<HeaderEntry>) {
    val cookies = parseRequestCookies(headers)
    if (cookies.isEmpty()) {
        Text(stringResource(Res.string.sessions_table_no_request_cookies))
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
                    text = stringResource(Res.string.sessions_table_cookie_name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_table_cookie_value),
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cookies.forEachIndexed { index, cookie ->
                val rowColor = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                }
                CookieContextMenuArea(
                    name = cookie.name,
                    value = cookie.value,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowColor)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cookie.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = cookie.value,
                            modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetCookieTable(headers: List<HeaderEntry>) {
    val setCookies = parseSetCookies(headers)
    if (setCookies.isEmpty()) {
        Text(stringResource(Res.string.sessions_table_no_set_cookies))
        return
    }
    val noAttributes = stringResource(Res.string.sessions_metadata_none)

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
                    text = stringResource(Res.string.sessions_table_cookie_name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_table_cookie_value),
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.sessions_table_cookie_attributes),
                    modifier = Modifier.weight(1.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            setCookies.forEachIndexed { index, cookie ->
                val rowColor = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                }
                CookieContextMenuArea(
                    name = cookie.name,
                    value = cookie.value,
                    attributes = cookie.attributes,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowColor)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cookie.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = cookie.value,
                            modifier = Modifier.weight(1.2f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = cookie.attributes.ifBlank { noAttributes },
                            modifier = Modifier.weight(1.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun CookieContextMenuArea(
    name: String,
    value: String,
    attributes: String? = null,
    content: @Composable () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val copyNameLabel = stringResource(Res.string.sessions_cookie_action_copy_name)
    val copyValueLabel = stringResource(Res.string.sessions_cookie_action_copy_value)
    val copyAttributesLabel = stringResource(Res.string.sessions_cookie_action_copy_attributes)

    ContextMenuArea(
        items = {
            buildList {
                add(
                    ContextMenuItem(copyNameLabel) {
                        clipboardManager.setText(AnnotatedString(name))
                    },
                )
                add(
                    ContextMenuItem(copyValueLabel) {
                        clipboardManager.setText(AnnotatedString(value))
                    },
                )
                if (attributes != null) {
                    add(
                        ContextMenuItem(copyAttributesLabel) {
                            clipboardManager.setText(AnnotatedString(attributes))
                        },
                    )
                }
            }
        },
    ) {
        content()
    }
}

private data class RequestCookieEntry(
    val name: String,
    val value: String,
)

private data class SetCookieEntry(
    val name: String,
    val value: String,
    val attributes: String,
)

private fun parseRequestCookies(headers: List<HeaderEntry>): List<RequestCookieEntry> {
    return headers.asSequence()
        .filter { header -> header.name.equals("Cookie", ignoreCase = true) }
        .flatMap { header ->
            header.value.split(';')
                .asSequence()
                .mapNotNull { cookiePair ->
                    val pair = cookiePair.trim()
                    if (pair.isEmpty()) {
                        return@mapNotNull null
                    }
                    val separatorIndex = pair.indexOf('=')
                    if (separatorIndex < 0) {
                        return@mapNotNull RequestCookieEntry(name = pair, value = "")
                    }
                    val name = pair.substring(0, separatorIndex).trim()
                    if (name.isEmpty()) {
                        return@mapNotNull null
                    }
                    RequestCookieEntry(
                        name = name,
                        value = pair.substring(separatorIndex + 1).trim(),
                    )
                }
        }
        .toList()
}

private fun parseSetCookies(headers: List<HeaderEntry>): List<SetCookieEntry> {
    return headers.asSequence()
        .filter { header -> header.name.equals("Set-Cookie", ignoreCase = true) }
        .mapNotNull { header ->
            val parts = header.value.split(';')
                .map { part -> part.trim() }
                .filter { part -> part.isNotEmpty() }
            if (parts.isEmpty()) {
                return@mapNotNull null
            }

            val cookiePair = parts.first()
            val separatorIndex = cookiePair.indexOf('=')
            val name = if (separatorIndex >= 0) {
                cookiePair.substring(0, separatorIndex).trim()
            } else {
                cookiePair
            }
            if (name.isEmpty()) {
                return@mapNotNull null
            }
            val value = if (separatorIndex >= 0) {
                cookiePair.substring(separatorIndex + 1).trim()
            } else {
                ""
            }
            SetCookieEntry(
                name = name,
                value = value,
                attributes = parts.drop(1).joinToString(separator = "; "),
            )
        }
        .toList()
}

@Composable
private fun formatAppliedRuleTrace(
    traces: List<AppliedRuleTrace>,
    requestScope: Boolean,
): String {
    val emptyLabel = stringResource(Res.string.sessions_metadata_none)
    val emptyMutationLabel = stringResource(Res.string.sessions_metadata_matched_without_details)
    val traceEntryTemplate = stringResource(
        Res.string.sessions_rule_trace_entry,
        "%1\$s",
        "%2\$s",
    )

    val scopedTraces = traces.filter { trace ->
        if (requestScope) trace.appliedToRequest else trace.appliedToResponse
    }
    if (scopedTraces.isEmpty()) {
        return emptyLabel
    }
    return scopedTraces.joinToString(separator = "\n") { trace ->
        val mutationSummary = trace.mutations.joinToString(separator = ", ")
            .ifBlank { emptyMutationLabel }
        traceEntryTemplate.format(trace.ruleName, mutationSummary)
    }
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
            contentDescription = stringResource(Res.string.sessions_content_description_response_image),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        )
        return
    }

    if (body.isNullOrBlank()) {
        Text(stringResource(Res.string.sessions_table_no_body))
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
