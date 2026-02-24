package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemorySessionRepository : SessionRepository {

    private val _sessions = MutableStateFlow<List<CapturedSession>>(emptyList())

    override val sessions: StateFlow<List<CapturedSession>> = _sessions.asStateFlow()

    override fun clear() {
        _sessions.value = emptyList()
    }

    internal fun addSession(session: CapturedSession) {
        upsertSession(session)
    }

    internal fun upsertSession(session: CapturedSession) {
        _sessions.update { existing ->
            val existingIndex = existing.indexOfFirst { current -> current.id == session.id }
            val withoutCurrent = if (existingIndex >= 0) {
                existing.filterIndexed { index, _ -> index != existingIndex }
            } else {
                existing
            }

            buildList(capacity = (withoutCurrent.size + 1).coerceAtMost(MAX_SESSIONS)) {
                add(session)
                addAll(withoutCurrent.take(MAX_SESSIONS - 1))
            }
        }
    }

    private companion object {
        const val MAX_SESSIONS = 500
    }
}
