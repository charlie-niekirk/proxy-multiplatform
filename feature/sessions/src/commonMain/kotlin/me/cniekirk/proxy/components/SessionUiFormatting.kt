package me.cniekirk.proxy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import me.cniekirk.proxy.CapturedResponse
import me.cniekirk.proxy.CapturedSession
import org.jetbrains.compose.resources.stringResource
import proxy.feature.sessions.generated.resources.*

private data class UrlSegments(
    val protocol: String?,
    val host: String,
    val path: String,
    val suffix: String,
)

@Composable
internal fun buildColorizedUrlText(
    url: String,
    protocolColor: Color,
    hostColor: Color,
    pathColor: Color,
    suffixColor: Color,
) = buildAnnotatedString {
    val segments = splitUrlSegments(
        url = url,
        unknownHostLabel = stringResource(Res.string.sessions_unknown_host),
    )
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

@Composable
internal fun responseCodeText(response: CapturedResponse): String {
    val reasonPhrase = response.reasonPhrase
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    return if (reasonPhrase != null) {
        stringResource(
            Res.string.sessions_response_code,
            response.statusCode,
            reasonPhrase,
        )
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

@Composable
internal fun sessionLifecycleStatus(session: CapturedSession): String {
    return when {
        session.response != null -> stringResource(Res.string.sessions_status_completed)
        session.error != null -> stringResource(Res.string.sessions_status_error)
        else -> stringResource(Res.string.sessions_status_pending)
    }
}

@Composable
internal fun formatDuration(durationMillis: Long?): String {
    val value = durationMillis ?: return stringResource(Res.string.sessions_value_unavailable)
    if (value < 1_000) {
        return stringResource(Res.string.sessions_duration_milliseconds, value)
    }

    val wholeSeconds = value / 1_000
    val hundredths = (value % 1_000) / 10
    return stringResource(
        Res.string.sessions_duration_seconds,
        wholeSeconds,
        hundredths,
    )
}

@Composable
internal fun formatBytes(value: Long?): String {
    val bytes = value ?: return stringResource(Res.string.sessions_value_unavailable)
    if (bytes < 1024) {
        return stringResource(Res.string.sessions_size_bytes, bytes)
    }

    val kib = bytes / 1024.0
    if (kib < 1024.0) {
        return stringResource(
            Res.string.sessions_size_kilobytes,
            formatWithSingleDecimal(kib),
        )
    }

    val mib = kib / 1024.0
    return stringResource(
        Res.string.sessions_size_megabytes,
        formatWithSingleDecimal(mib),
    )
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

private fun splitUrlSegments(
    url: String,
    unknownHostLabel: String,
): UrlSegments {
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
        .ifBlank { unknownHostLabel }
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

private fun formatWithSingleDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val decimal = rounded % 10
    return "$whole.$decimal"
}
