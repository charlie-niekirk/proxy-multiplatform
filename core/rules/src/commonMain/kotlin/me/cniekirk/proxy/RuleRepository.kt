package me.cniekirk.proxy

import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    val rules: Flow<List<RuleDefinition>>

    suspend fun upsertRule(rule: RuleDefinition)

    suspend fun deleteRule(ruleId: String)

    suspend fun updateRules(transform: (List<RuleDefinition>) -> List<RuleDefinition>)
}
