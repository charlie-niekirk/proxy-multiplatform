package me.cniekirk.proxy

import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val sessions: StateFlow<List<CapturedSession>>
    fun clear()
}
