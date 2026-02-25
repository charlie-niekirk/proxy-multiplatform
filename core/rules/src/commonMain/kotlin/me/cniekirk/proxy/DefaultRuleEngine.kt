package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
@Inject
class DefaultRuleEngine : RuleEngine {

    override fun applyRequestRules(
        rules: List<RuleDefinition>,
        request: RuleHttpRequest,
    ): RuleMutationResult<RuleHttpRequest> {
        var mutatedHeaders = request.headers
        var mutatedBody = request.bodyBytes
        val traces = mutableListOf<RuleExecutionTrace>()

        orderedEnabledRules(rules).forEach { rule ->
            if (!rule.matcher.matches(request.matchContext)) {
                return@forEach
            }

            val actions = rule.actions.filter { action -> action.target == RuleTarget.REQUEST }
            if (actions.isEmpty()) {
                return@forEach
            }

            val mutationState = MutationState(
                headers = mutatedHeaders.toMutableList(),
                bodyBytes = mutatedBody,
            )
            val mutations = applyActions(
                actions = actions,
                state = mutationState,
            )
            if (mutations.isNotEmpty()) {
                mutatedHeaders = mutationState.headers.toList()
                mutatedBody = mutationState.bodyBytes
                traces += RuleExecutionTrace(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    target = RuleTarget.REQUEST,
                    mutations = mutations,
                )
            }
        }

        return RuleMutationResult(
            value = request.copy(
                headers = mutatedHeaders,
                bodyBytes = mutatedBody,
            ),
            traces = traces,
        )
    }

    override fun applyResponseRules(
        rules: List<RuleDefinition>,
        requestMatchContext: RuleMatchContext,
        response: RuleHttpResponse,
    ): RuleMutationResult<RuleHttpResponse> {
        var mutatedHeaders = response.headers
        var mutatedBody = response.bodyBytes
        val traces = mutableListOf<RuleExecutionTrace>()

        orderedEnabledRules(rules).forEach { rule ->
            if (!rule.matcher.matches(requestMatchContext)) {
                return@forEach
            }

            val actions = rule.actions.filter { action -> action.target == RuleTarget.RESPONSE }
            if (actions.isEmpty()) {
                return@forEach
            }

            val mutationState = MutationState(
                headers = mutatedHeaders.toMutableList(),
                bodyBytes = mutatedBody,
            )
            val mutations = applyActions(
                actions = actions,
                state = mutationState,
            )
            if (mutations.isNotEmpty()) {
                mutatedHeaders = mutationState.headers.toList()
                mutatedBody = mutationState.bodyBytes
                traces += RuleExecutionTrace(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    target = RuleTarget.RESPONSE,
                    mutations = mutations,
                )
            }
        }

        return RuleMutationResult(
            value = response.copy(
                headers = mutatedHeaders,
                bodyBytes = mutatedBody,
            ),
            traces = traces,
        )
    }

    private fun orderedEnabledRules(rules: List<RuleDefinition>): List<RuleDefinition> {
        return rules
            .filter { rule -> rule.enabled }
            .sortedWith(
                compareBy<RuleDefinition> { rule -> rule.priority }
                    .thenBy { rule -> rule.name.lowercase() }
                    .thenBy { rule -> rule.id },
            )
    }

    private fun applyActions(
        actions: List<RuleAction>,
        state: MutationState,
    ): List<String> {
        val mutations = mutableListOf<String>()
        actions.forEach { action ->
            when (action.type) {
                RuleActionType.SET_HEADER -> {
                    val headerName = action.headerName?.trim().orEmpty()
                    if (headerName.isBlank()) {
                        return@forEach
                    }

                    val headerValue = action.headerValue.orEmpty()
                    val existingCount = state.headers.removeHeader(name = headerName)
                    state.headers += HeaderEntry(name = headerName, value = headerValue)
                    val prefix = if (existingCount > 0) "Updated" else "Set"
                    mutations += "$prefix header $headerName"
                }

                RuleActionType.REMOVE_HEADER -> {
                    val headerName = action.headerName?.trim().orEmpty()
                    if (headerName.isBlank()) {
                        return@forEach
                    }
                    val removedCount = state.headers.removeHeader(name = headerName)
                    if (removedCount > 0) {
                        mutations += "Removed header $headerName"
                    }
                }

                RuleActionType.REPLACE_BODY -> {
                    val replacementText = action.bodyValue.orEmpty()
                    state.bodyBytes = replacementText.encodeToByteArray()

                    state.headers.removeHeader(name = CONTENT_LENGTH_HEADER)
                    state.headers.removeHeader(name = TRANSFER_ENCODING_HEADER)
                    state.headers.removeHeader(name = CONTENT_ENCODING_HEADER)
                    state.headers += HeaderEntry(
                        name = CONTENT_LENGTH_HEADER,
                        value = state.bodyBytes.size.toString(),
                    )
                    val contentType = action.contentType?.trim().orEmpty()
                    if (contentType.isNotEmpty()) {
                        state.headers.removeHeader(name = CONTENT_TYPE_HEADER)
                        state.headers += HeaderEntry(
                            name = CONTENT_TYPE_HEADER,
                            value = contentType,
                        )
                    }

                    mutations += "Replaced body (${state.bodyBytes.size} bytes)"
                }
            }
        }
        return mutations
    }

    private fun RuleMatcher.matches(context: RuleMatchContext): Boolean {
        return scheme.matchesValue(candidate = context.scheme, ignoreCase = true) &&
            host.matchesValue(candidate = context.host, ignoreCase = true) &&
            path.matchesValue(candidate = context.path, ignoreCase = false) &&
            port.matchesValue(candidate = context.port.toString(), ignoreCase = false)
    }

    private fun RuleMatchField.matchesValue(
        candidate: String,
        ignoreCase: Boolean,
    ): Boolean {
        val pattern = value.trim()
        return when (mode) {
            RuleMatchMode.ANY -> true
            RuleMatchMode.EXACT -> pattern.isNotEmpty() && pattern.equals(candidate, ignoreCase = ignoreCase)
            RuleMatchMode.WILDCARD -> {
                if (pattern.isEmpty()) {
                    false
                } else {
                    val regex = pattern.wildcardRegex(ignoreCase = ignoreCase)
                    regex.matches(candidate)
                }
            }

            RuleMatchMode.REGEX -> {
                if (pattern.isEmpty()) {
                    false
                } else {
                    val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    runCatching { Regex(pattern, options) }
                        .getOrNull()
                        ?.matches(candidate)
                        ?: false
                }
            }
        }
    }

    private fun String.wildcardRegex(ignoreCase: Boolean): Regex {
        val escapedPattern = buildString(capacity = this@wildcardRegex.length * 2) {
            append("^")
            this@wildcardRegex.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '\\',
                    '.',
                    '^',
                    '$',
                    '|',
                    '(',
                    ')',
                    '[',
                    ']',
                    '{',
                    '}',
                    '+',
                    -> append("\\").append(char)

                    else -> append(char)
                }
            }
            append("$")
        }
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(escapedPattern, options)
    }

    private fun MutableList<HeaderEntry>.removeHeader(name: String): Int {
        val beforeCount = size
        removeAll { header -> header.name.equals(name, ignoreCase = true) }
        return beforeCount - size
    }

    private data class MutationState(
        val headers: MutableList<HeaderEntry>,
        var bodyBytes: ByteArray,
    )

    private companion object {
        const val CONTENT_LENGTH_HEADER = "Content-Length"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val CONTENT_ENCODING_HEADER = "Content-Encoding"
        const val TRANSFER_ENCODING_HEADER = "Transfer-Encoding"
    }
}
