package me.cniekirk.proxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.components.RequestsTableCard
import me.cniekirk.proxy.components.RuntimeErrorBanner
import me.cniekirk.proxy.components.SessionDetailTab
import me.cniekirk.proxy.components.SessionInspector
import me.cniekirk.proxy.components.SessionTreeSidebar
import me.cniekirk.proxy.components.SessionsToolbar
import org.orbitmvi.orbit.compose.collectAsState

private val SIDEBAR_WIDTH = 230.dp

@Composable
fun SessionsScreen(viewModel: SessionsViewModel) {
    val state by viewModel.collectAsState()
    val sessionTree = remember(state.sessions) { buildSessionHostPathTree(state.sessions) }
    var selectedFilter by remember { mutableStateOf(SessionTreeFilter()) }
    var expandedNodeKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var observedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasInitializedSessionTracking by remember { mutableStateOf(false) }
    var flashTokenByNodeKey by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(state.sessions, selectedFilter) {
        if (!selectedFilter.isAll && state.sessions.none { session -> selectedFilter.matches(session) }) {
            selectedFilter = SessionTreeFilter()
        }
    }

    LaunchedEffect(sessionTree.nodeKeys) {
        expandedNodeKeys = expandedNodeKeys.intersect(sessionTree.nodeKeys)
        flashTokenByNodeKey = flashTokenByNodeKey.filterKeys { nodeKey -> nodeKey in sessionTree.nodeKeys }
    }

    LaunchedEffect(state.sessions) {
        val currentSessionIds = state.sessions.mapTo(linkedSetOf()) { session -> session.id }
        if (!hasInitializedSessionTracking) {
            observedSessionIds = currentSessionIds
            hasInitializedSessionTracking = true
            return@LaunchedEffect
        }

        val newSessions = state.sessions.filter { session -> session.id !in observedSessionIds }
        if (newSessions.isNotEmpty()) {
            val updatedFlashTokens = flashTokenByNodeKey.toMutableMap()
            newSessions.forEach { session ->
                val nodeKey = resolveSessionFlashNodeKey(
                    session = session,
                    tree = sessionTree,
                    expandedNodeKeys = expandedNodeKeys,
                ) ?: return@forEach
                updatedFlashTokens[nodeKey] = (updatedFlashTokens[nodeKey] ?: 0L) + 1L
            }
            flashTokenByNodeKey = updatedFlashTokens
        }

        observedSessionIds = currentSessionIds
    }

    val visibleSessions = remember(state.sessions, selectedFilter) {
        state.sessions.filter { session -> selectedFilter.matches(session) }
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
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SessionTreeSidebar(
                tree = sessionTree,
                selectedFilter = selectedFilter,
                expandedNodeKeys = expandedNodeKeys,
                flashTokenByNodeKey = flashTokenByNodeKey,
                totalCount = state.sessions.size,
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .fillMaxHeight(),
                onFilterSelected = { filter ->
                    selectedFilter = filter
                },
                onNodeToggled = { nodeKey ->
                    expandedNodeKeys = if (nodeKey in expandedNodeKeys) {
                        expandedNodeKeys - nodeKey
                    } else {
                        expandedNodeKeys + nodeKey
                    }
                },
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
