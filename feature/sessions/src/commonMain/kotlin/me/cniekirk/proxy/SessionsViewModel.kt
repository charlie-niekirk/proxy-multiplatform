package me.cniekirk.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@Inject
@ViewModelKey(SessionsViewModel::class)
@ContributesIntoMap(scope = AppScope::class, binding = binding<ViewModel>())
class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    private val proxyRuntimeService: ProxyRuntimeService,
    private val settingsRepository: SettingsRepository,
) : ViewModel(), ContainerHost<SessionsState, Nothing> {

    override val container = container<SessionsState, Nothing>(SessionsState()) {
        startProxyRuntime()
        observeSessions()
    }

    fun selectSession(sessionId: String) = intent {
        reduce { state.copy(selectedSessionId = sessionId) }
    }

    fun clearSessions() = intent {
        sessionRepository.clear()
    }

    fun clearRuntimeError() = intent {
        reduce { state.copy(runtimeError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            proxyRuntimeService.stop()
        }
    }

    private fun startProxyRuntime() = intent {
        val proxySettings = settingsRepository.settings.first().proxy
        val listeningAddress = "${proxySettings.host}:${proxySettings.port}"
        runCatching { proxyRuntimeService.start() }
            .onSuccess {
                reduce {
                    state.copy(
                        listeningAddress = listeningAddress,
                        isListening = true,
                        runtimeError = null,
                    )
                }
            }
            .onFailure { error ->
                reduce {
                    state.copy(
                        listeningAddress = listeningAddress,
                        isListening = false,
                        runtimeError = error.message ?: "Failed to start proxy runtime",
                    )
                }
            }
    }

    private fun observeSessions() = intent {
        sessionRepository.sessions.collect { sessions ->
            reduce {
                val selectedSessionId = state.selectedSessionId
                    ?.takeIf { selectedId -> sessions.any { it.id == selectedId } }
                    ?: sessions.firstOrNull()?.id

                state.copy(
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                )
            }
        }
    }
}
