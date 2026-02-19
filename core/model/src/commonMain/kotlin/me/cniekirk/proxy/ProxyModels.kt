package me.cniekirk.proxy

import kotlinx.serialization.Serializable

@Serializable
data class ProxySettings(
    val host: String = "127.0.0.1",
    val port: Int = 9090,
    val sslDecryptionEnabled: Boolean = false,
    val maxBodyCaptureBytes: Long = 1_048_576,
)

@Serializable
data class CertificateState(
    val generated: Boolean = false,
    val fingerprint: String? = null,
    val createdAtEpochMillis: Long? = null,
)

@Serializable
data class AppSettings(
    val proxy: ProxySettings = ProxySettings(),
    val certificate: CertificateState = CertificateState(),
)
