package me.cniekirk.proxy

interface ProxyRuntimeService {
    suspend fun start()
    suspend fun stop()
}