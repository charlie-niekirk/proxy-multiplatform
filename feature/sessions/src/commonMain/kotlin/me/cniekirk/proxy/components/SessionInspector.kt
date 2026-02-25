package me.cniekirk.proxy.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                val entries = buildList {
                    add("Method" to session.request.method)
                    add("URL" to session.request.url)
                    add("Captured (UTC)" to formatCapturedTimeUtc(session.request.timestampEpochMillis))
                    add("Body Type" to session.request.bodyType.name)
                    add("Body Size" to formatBytes(session.request.bodySizeBytes))
                    add("Headers" to session.request.headers.size.toString())

                    val requestRuleCount = session.appliedRules.count { trace -> trace.appliedToRequest }
                    add("Applied Request Rules" to requestRuleCount.toString())
                    if (requestRuleCount > 0) {
                        add("Request Rule Trace" to formatAppliedRuleTrace(session.appliedRules, requestScope = true))
                    }
                }
                MetadataTable(entries = entries)
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
                    val responseRuleCount = session.appliedRules.count { trace -> trace.appliedToResponse }
                    add("Applied Response Rules" to responseRuleCount.toString())
                    if (responseRuleCount > 0) {
                        add("Response Rule Trace" to formatAppliedRuleTrace(session.appliedRules, requestScope = false))
                    }
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

private fun formatAppliedRuleTrace(
    traces: List<AppliedRuleTrace>,
    requestScope: Boolean,
): String {
    val scopedTraces = traces.filter { trace ->
        if (requestScope) trace.appliedToRequest else trace.appliedToResponse
    }
    if (scopedTraces.isEmpty()) {
        return "None"
    }
    return scopedTraces.joinToString(separator = "\n") { trace ->
        val mutationSummary = trace.mutations.joinToString(separator = ", ")
            .ifBlank { "matched without action details" }
        "${trace.ruleName}: $mutationSummary"
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
