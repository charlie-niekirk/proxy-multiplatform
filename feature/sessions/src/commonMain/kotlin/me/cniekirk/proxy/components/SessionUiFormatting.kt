package me.cniekirk.proxy.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import me.cniekirk.proxy.CapturedResponse
import me.cniekirk.proxy.CapturedSession

private data class UrlSegments(
    val protocol: String?,
    val host: String,
    val path: String,
    val suffix: String,
)

internal fun buildColorizedUrlText(
    url: String,
    protocolColor: Color,
    hostColor: Color,
    pathColor: Color,
    suffixColor: Color,
) = buildAnnotatedString {
    val segments = splitUrlSegments(url)
    segments.protocol?.let { protocol ->
        withStyle(SpanStyle(color = protocolColor)) {
            append(protocol)
            append("://")
        }
    }
    withStyle(SpanStyle(color = hostColor, fontWeight = FontWeight.SemiBold)) {
        append(segments.host)
    }
    withStyle(SpanStyle(color = pathColor)) {
        append(segments.path)
    }
    if (segments.suffix.isNotEmpty()) {
        withStyle(SpanStyle(color = suffixColor)) {
            append(segments.suffix)
        }
    }
}

internal fun responseCodeText(response: CapturedResponse): String {
    val reasonPhrase = response.reasonPhrase
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: defaultReasonPhrase(response.statusCode)

    return if (reasonPhrase.isNotEmpty()) {
        "${response.statusCode} $reasonPhrase"
    } else {
        response.statusCode.toString()
    }
}

internal fun statusCodeColors(statusCode: Int): Pair<Color, Color> {
    return when (statusCode / 100) {
        2 -> Color(0xFFDCFCE7) to Color(0xFF166534)
        3 -> Color(0xFFF1F5F9) to Color(0xFF334155)
        4 -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
        5 -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> Color(0xFFE2E8F0) to Color(0xFF334155)
    }
}

internal fun sessionLifecycleStatus(session: CapturedSession): String {
    return when {
        session.response != null -> "Completed"
        session.error != null -> "Error"
        else -> "Pending"
    }
}

internal fun formatDuration(durationMillis: Long?): String {
    val value = durationMillis ?: return "-"
    if (value < 1_000) {
        return "$value ms"
    }

    val wholeSeconds = value / 1_000
    val hundredths = (value % 1_000) / 10
    return "$wholeSeconds.${hundredths.toString().padStart(2, '0')} s"
}

internal fun formatBytes(value: Long?): String {
    val bytes = value ?: return "-"
    if (bytes < 1024) {
        return "$bytes B"
    }

    val kib = bytes / 1024.0
    if (kib < 1024.0) {
        return "${formatWithSingleDecimal(kib)} KB"
    }

    val mib = kib / 1024.0
    return "${formatWithSingleDecimal(mib)} MB"
}

internal fun formatCapturedTimeUtc(timestampEpochMillis: Long): String {
    val millisecondsPerDay = 24L * 60L * 60L * 1000L
    val msOfDay = ((timestampEpochMillis % millisecondsPerDay) + millisecondsPerDay) % millisecondsPerDay

    val hours = (msOfDay / 3_600_000L).toInt()
    val minutes = ((msOfDay % 3_600_000L) / 60_000L).toInt()
    val seconds = ((msOfDay % 60_000L) / 1_000L).toInt()
    val millis = (msOfDay % 1_000L).toInt()

    return "${hours.toString().padStart(2, '0')}:" +
        "${minutes.toString().padStart(2, '0')}:" +
        "${seconds.toString().padStart(2, '0')}." +
        millis.toString().padStart(3, '0')
}

internal fun displaySessionId(sessionId: String): String {
    if (sessionId.length <= 6) {
        return sessionId
    }
    return sessionId.takeLast(6)
}

private fun splitUrlSegments(url: String): UrlSegments {
    val schemeDelimiter = "://"
    val hasScheme = url.contains(schemeDelimiter)
    val protocol = if (hasScheme) url.substringBefore(schemeDelimiter) else null
    val withoutScheme = if (hasScheme) {
        url.substringAfter(schemeDelimiter)
    } else {
        url
    }

    val boundaryIndex = withoutScheme.indexOfAny(charArrayOf('/', '?', '#'))
    val rawHost = if (boundaryIndex >= 0) {
        withoutScheme.substring(0, boundaryIndex)
    } else {
        withoutScheme
    }
    val remainder = if (boundaryIndex >= 0) {
        withoutScheme.substring(boundaryIndex)
    } else {
        ""
    }

    val host = rawHost
        .substringAfter('@', rawHost)
        .ifBlank { "(unknown)" }
    val suffixStartIndex = remainder.indexOfAny(charArrayOf('?', '#'))
    val path = when {
        remainder.isEmpty() -> "/"
        suffixStartIndex < 0 -> remainder
        suffixStartIndex == 0 -> "/"
        else -> remainder.substring(0, suffixStartIndex)
    }
    val suffix = when {
        remainder.isEmpty() -> ""
        suffixStartIndex < 0 -> ""
        else -> remainder.substring(suffixStartIndex)
    }

    return UrlSegments(
        protocol = protocol,
        host = host,
        path = path,
        suffix = suffix,
    )
}

private fun defaultReasonPhrase(statusCode: Int): String {
    return when (statusCode) {
        100 -> "Continue"
        101 -> "Switching Protocols"
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        206 -> "Partial Content"
        300 -> "Multiple Choices"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        418 -> "I'm a Teapot"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> ""
    }
}

private fun formatWithSingleDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val decimal = rounded % 10
    return "$whole.$decimal"
}
