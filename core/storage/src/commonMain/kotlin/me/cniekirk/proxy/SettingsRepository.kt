package me.cniekirk.proxy

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateProxySettings(transform: (ProxySettings) -> ProxySettings)
    suspend fun updateCertificateState(transform: (CertificateState) -> CertificateState)
}
