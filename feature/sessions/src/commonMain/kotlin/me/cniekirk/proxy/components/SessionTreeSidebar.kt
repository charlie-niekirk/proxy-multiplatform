package me.cniekirk.proxy.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.SessionHostPathTree
import me.cniekirk.proxy.SessionHostTree
import me.cniekirk.proxy.SessionPathTree
import me.cniekirk.proxy.SessionTreeFilter
import me.cniekirk.proxy.UnknownSessionHost
import org.jetbrains.compose.resources.stringResource
import proxy.feature.sessions.generated.resources.*

private val FlashColor = Color(0xFF22C55E)

@Composable
internal fun SessionTreeSidebar(
    tree: SessionHostPathTree,
    selectedFilter: SessionTreeFilter,
    expandedNodeKeys: Set<String>,
    flashTokenByNodeKey: Map<String, Long>,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onFilterSelected: (SessionTreeFilter) -> Unit,
    onNodeToggled: (String) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.sessions_sidebar_title),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SessionTreeNodeRow(
                        label = stringResource(Res.string.sessions_filter_all),
                        count = totalCount,
                        depth = 0,
                        hasChildren = false,
                        isExpanded = false,
                        isSelected = selectedFilter.isAll,
                        onSelect = { onFilterSelected(SessionTreeFilter()) },
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }

                items(
                    items = tree.hosts,
                    key = { host -> host.key },
                ) { host ->
                    SessionHostNode(
                        host = host,
                        selectedFilter = selectedFilter,
                        expandedNodeKeys = expandedNodeKeys,
                        flashTokenByNodeKey = flashTokenByNodeKey,
                        onFilterSelected = onFilterSelected,
                        onNodeToggled = onNodeToggled,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHostNode(
    host: SessionHostTree,
    selectedFilter: SessionTreeFilter,
    expandedNodeKeys: Set<String>,
    flashTokenByNodeKey: Map<String, Long>,
    onFilterSelected: (SessionTreeFilter) -> Unit,
    onNodeToggled: (String) -> Unit,
) {
    val isExpanded = host.key in expandedNodeKeys
    val hostLabel = if (host.host == UnknownSessionHost) {
        stringResource(Res.string.sessions_unknown_host)
    } else {
        host.host
    }
    SessionTreeNodeRow(
        label = hostLabel,
        count = host.count,
        depth = 0,
        hasChildren = host.children.isNotEmpty(),
        isExpanded = isExpanded,
        isSelected = selectedFilter == SessionTreeFilter(host = host.host),
        flashToken = flashTokenByNodeKey[host.key],
        onSelect = {
            onFilterSelected(SessionTreeFilter(host = host.host))
            if (host.children.isNotEmpty() && !isExpanded) {
                onNodeToggled(host.key)
            }
        },
        onToggleExpansion = if (host.children.isNotEmpty()) {
            { onNodeToggled(host.key) }
        } else {
            null
        },
    )

    if (host.children.isNotEmpty()) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 140)) +
                fadeIn(animationSpec = tween(durationMillis = 90)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 140)) +
                fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Column {
                host.children.forEach { child ->
                    key(child.key) {
                        SessionPathNode(
                            pathNode = child,
                            host = host.host,
                            depth = 1,
                            selectedFilter = selectedFilter,
                            expandedNodeKeys = expandedNodeKeys,
                            flashTokenByNodeKey = flashTokenByNodeKey,
                            onFilterSelected = onFilterSelected,
                            onNodeToggled = onNodeToggled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPathNode(
    pathNode: SessionPathTree,
    host: String,
    depth: Int,
    selectedFilter: SessionTreeFilter,
    expandedNodeKeys: Set<String>,
    flashTokenByNodeKey: Map<String, Long>,
    onFilterSelected: (SessionTreeFilter) -> Unit,
    onNodeToggled: (String) -> Unit,
) {
    val isExpanded = pathNode.key in expandedNodeKeys
    val filter = SessionTreeFilter(
        host = host,
        pathPrefix = pathNode.pathPrefix,
    )

    SessionTreeNodeRow(
        label = pathNode.segment,
        count = pathNode.count,
        depth = depth,
        hasChildren = pathNode.children.isNotEmpty(),
        isExpanded = isExpanded,
        isSelected = selectedFilter == filter,
        flashToken = flashTokenByNodeKey[pathNode.key],
        onSelect = {
            onFilterSelected(filter)
            if (pathNode.children.isNotEmpty() && !isExpanded) {
                onNodeToggled(pathNode.key)
            }
        },
        onToggleExpansion = if (pathNode.children.isNotEmpty()) {
            { onNodeToggled(pathNode.key) }
        } else {
            null
        },
    )

    if (pathNode.children.isNotEmpty()) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 140)) +
                fadeIn(animationSpec = tween(durationMillis = 90)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 140)) +
                fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Column {
                pathNode.children.forEach { child ->
                    key(child.key) {
                        SessionPathNode(
                            pathNode = child,
                            host = host,
                            depth = depth + 1,
                            selectedFilter = selectedFilter,
                            expandedNodeKeys = expandedNodeKeys,
                            flashTokenByNodeKey = flashTokenByNodeKey,
                            onFilterSelected = onFilterSelected,
                            onNodeToggled = onNodeToggled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTreeNodeRow(
    label: String,
    count: Int,
    depth: Int,
    hasChildren: Boolean,
    isExpanded: Boolean,
    isSelected: Boolean,
    flashToken: Long? = null,
    onSelect: () -> Unit,
    onToggleExpansion: (() -> Unit)? = null,
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flashToken) {
        if (flashToken == null) {
            return@LaunchedEffect
        }
        flashAlpha.snapTo(0.42f)
        flashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 700),
        )
    }

    val blendedBackground = if (flashAlpha.value > 0f) {
        FlashColor.copy(alpha = flashAlpha.value).compositeOver(rowBackground)
    } else {
        rowBackground
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "SessionTreeChevron",
    )
    val indicatorStartPadding = (8 + (depth * 12)).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(blendedBackground)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = indicatorStartPadding)
                .width(12.dp)
                .clickable(enabled = hasChildren && onToggleExpansion != null) {
                    onToggleExpansion?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (hasChildren) {
                Text(
                    text = ">",
                    modifier = Modifier.rotate(chevronRotation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
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
}
