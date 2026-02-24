package me.cniekirk.proxy

import kotlinx.serialization.Serializable

@Serializable
data class ProxySettings(
    val host: String = "0.0.0.0",
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

data class HeaderEntry(
    val name: String,
    val value: String,
)

enum class CapturedBodyType {
    Json,
    Xml,
    Html,
    PlainText,
}

data class CapturedRequest(
    val method: String,
    val url: String,
    val headers: List<HeaderEntry>,
    val body: String?,
    val bodyType: CapturedBodyType = detectCapturedBodyType(body = body, headers = headers),
    val bodySizeBytes: Long,
    val timestampEpochMillis: Long,
)

data class CapturedResponse(
    val statusCode: Int,
    val reasonPhrase: String?,
    val headers: List<HeaderEntry>,
    val body: String?,
    val imageBytes: ByteArray? = null,
    val bodyType: CapturedBodyType = detectCapturedBodyType(body = body, headers = headers),
    val bodySizeBytes: Long,
    val timestampEpochMillis: Long,
)

data class CapturedSession(
    val id: String,
    val request: CapturedRequest,
    val response: CapturedResponse?,
    val error: String?,
    val durationMillis: Long?,
)

fun detectCapturedBodyType(
    body: String?,
    headers: List<HeaderEntry>,
): CapturedBodyType {
    if (body.isNullOrBlank()) {
        return CapturedBodyType.PlainText
    }

    val contentType = headers
        .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.lowercase()
        .orEmpty()
    val trimmedBody = body.trimStart()

    return when {
        "json" in contentType -> CapturedBodyType.Json
        "xml" in contentType -> CapturedBodyType.Xml
        "html" in contentType -> CapturedBodyType.Html
        trimmedBody.startsWith("{") || trimmedBody.startsWith("[") -> CapturedBodyType.Json
        trimmedBody.startsWith("<!DOCTYPE html", ignoreCase = true) -> CapturedBodyType.Html
        trimmedBody.startsWith("<") -> CapturedBodyType.Xml
        else -> CapturedBodyType.PlainText
    }
}

fun isImageContentType(headers: List<HeaderEntry>): Boolean {
    val contentType = headers
        .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()

    return contentType.startsWith("image/")
}
