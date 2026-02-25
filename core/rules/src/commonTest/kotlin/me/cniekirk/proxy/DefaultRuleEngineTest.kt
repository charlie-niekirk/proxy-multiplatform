package me.cniekirk.proxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultRuleEngineTest {
    private val engine = DefaultRuleEngine()

    @Test
    fun `wildcard path matcher applies request header mutation`() {
        val rule = RuleDefinition(
            id = "rule-1",
            name = "Inject request header",
            priority = 1,
            matcher = RuleMatcher(
                scheme = RuleMatchField(mode = RuleMatchMode.EXACT, value = "https"),
                host = RuleMatchField(mode = RuleMatchMode.EXACT, value = "api.asos.com"),
                path = RuleMatchField(mode = RuleMatchMode.WILDCARD, value = "/prd/*"),
                port = RuleMatchField(mode = RuleMatchMode.EXACT, value = "443"),
            ),
            actions = listOf(
                RuleAction(
                    id = "action-1",
                    target = RuleTarget.REQUEST,
                    type = RuleActionType.SET_HEADER,
                    headerName = "X-Debug",
                    headerValue = "true",
                ),
            ),
        )

        val result = engine.applyRequestRules(
            rules = listOf(rule),
            request = RuleHttpRequest(
                method = "GET",
                matchContext = RuleMatchContext(
                    scheme = "https",
                    host = "api.asos.com",
                    path = "/prd/items",
                    port = 443,
                ),
                headers = emptyList(),
                bodyBytes = ByteArray(0),
            ),
        )

        assertTrue(result.value.headers.any { it.name == "X-Debug" && it.value == "true" })
        assertEquals(1, result.traces.size)
        assertEquals(RuleTarget.REQUEST, result.traces.first().target)
    }

    @Test
    fun `response body replacement rewrites length and encoding headers`() {
        val rule = RuleDefinition(
            id = "rule-2",
            name = "Replace response body",
            priority = 1,
            matcher = RuleMatcher(
                host = RuleMatchField(mode = RuleMatchMode.EXACT, value = "example.com"),
                path = RuleMatchField(mode = RuleMatchMode.EXACT, value = "/v1/data"),
            ),
            actions = listOf(
                RuleAction(
                    id = "action-2",
                    target = RuleTarget.RESPONSE,
                    type = RuleActionType.REPLACE_BODY,
                    bodyValue = """{"status":"ok"}""",
                    contentType = "application/json",
                ),
            ),
        )

        val result = engine.applyResponseRules(
            rules = listOf(rule),
            requestMatchContext = RuleMatchContext(
                scheme = "https",
                host = "example.com",
                path = "/v1/data",
                port = 443,
            ),
            response = RuleHttpResponse(
                statusCode = 200,
                reasonPhrase = "OK",
                headers = listOf(
                    HeaderEntry("Content-Encoding", "gzip"),
                    HeaderEntry("Transfer-Encoding", "chunked"),
                    HeaderEntry("Content-Length", "999"),
                ),
                bodyBytes = "old".encodeToByteArray(),
            ),
        )

        val contentLengthHeader = result.value.headers.firstOrNull {
            it.name.equals("Content-Length", ignoreCase = true)
        }
        val contentEncodingHeader = result.value.headers.firstOrNull {
            it.name.equals("Content-Encoding", ignoreCase = true)
        }
        val transferEncodingHeader = result.value.headers.firstOrNull {
            it.name.equals("Transfer-Encoding", ignoreCase = true)
        }
        val contentTypeHeader = result.value.headers.firstOrNull {
            it.name.equals("Content-Type", ignoreCase = true)
        }

        assertEquals("""{"status":"ok"}""", result.value.bodyBytes.decodeToString())
        assertNotNull(contentLengthHeader)
        assertEquals(result.value.bodyBytes.size.toString(), contentLengthHeader.value)
        assertEquals("application/json", contentTypeHeader?.value)
        assertFalse(contentEncodingHeader != null)
        assertFalse(transferEncodingHeader != null)
        assertEquals(RuleTarget.RESPONSE, result.traces.single().target)
    }

    @Test
    fun `invalid regex never matches`() {
        val rule = RuleDefinition(
            id = "rule-3",
            name = "Invalid regex",
            matcher = RuleMatcher(
                host = RuleMatchField(mode = RuleMatchMode.REGEX, value = "(*invalid"),
            ),
            actions = listOf(
                RuleAction(
                    id = "action-3",
                    target = RuleTarget.REQUEST,
                    type = RuleActionType.SET_HEADER,
                    headerName = "X-Test",
                    headerValue = "1",
                ),
            ),
        )

        val result = engine.applyRequestRules(
            rules = listOf(rule),
            request = RuleHttpRequest(
                method = "GET",
                matchContext = RuleMatchContext(
                    scheme = "https",
                    host = "api.example.com",
                    path = "/",
                    port = 443,
                ),
                headers = emptyList(),
                bodyBytes = ByteArray(0),
            ),
        )

        assertTrue(result.value.headers.isEmpty())
        assertTrue(result.traces.isEmpty())
    }
}
