package me.cniekirk.proxy

interface RuleEngine {
    fun applyRequestRules(
        rules: List<RuleDefinition>,
        request: RuleHttpRequest,
    ): RuleMutationResult<RuleHttpRequest>

    fun applyResponseRules(
        rules: List<RuleDefinition>,
        requestMatchContext: RuleMatchContext,
        response: RuleHttpResponse,
    ): RuleMutationResult<RuleHttpResponse>
}

data class RuleMatchContext(
    val scheme: String,
    val host: String,
    val path: String,
    val port: Int,
)

data class RuleHttpRequest(
    val method: String,
    val matchContext: RuleMatchContext,
    val headers: List<HeaderEntry>,
    val bodyBytes: ByteArray,
)

data class RuleHttpResponse(
    val statusCode: Int,
    val reasonPhrase: String?,
    val headers: List<HeaderEntry>,
    val bodyBytes: ByteArray,
)

data class RuleMutationResult<T>(
    val value: T,
    val traces: List<RuleExecutionTrace>,
)

data class RuleExecutionTrace(
    val ruleId: String,
    val ruleName: String,
    val target: RuleTarget,
    val mutations: List<String>,
)
