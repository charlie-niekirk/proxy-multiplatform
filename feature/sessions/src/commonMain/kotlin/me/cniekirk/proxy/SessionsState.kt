package me.cniekirk.proxy

data class SessionsState(
    val sessions: List<CapturedSession> = emptyList(),
    val selectedSessionId: String? = null,
    val listeningAddress: String? = null,
    val isListening: Boolean = false,
    val runtimeError: String? = null,
)
