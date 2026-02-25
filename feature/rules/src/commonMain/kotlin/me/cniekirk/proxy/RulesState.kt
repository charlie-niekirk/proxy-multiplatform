package me.cniekirk.proxy

data class RulesState(
    val rules: List<RuleDefinition> = emptyList(),
    val actionError: String? = null,
)
