package me.cniekirk.proxy

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@Inject
@ViewModelKey(RulesViewModel::class)
@ContributesIntoMap(scope = AppScope::class, binding = binding<ViewModel>())
class RulesViewModel(
    private val ruleRepository: RuleRepository,
) : ViewModel(), ContainerHost<RulesState, Nothing> {

    override val container = container<RulesState, Nothing>(RulesState()) {
        observeRules()
    }

    fun saveRule(rule: RuleDefinition) = intent {
        runCatching {
            ruleRepository.upsertRule(rule)
        }.onFailure { error ->
            reduce { state.copy(actionError = error.message.orEmpty()) }
        }
    }

    fun deleteRule(ruleId: String) = intent {
        runCatching {
            ruleRepository.deleteRule(ruleId)
        }.onFailure { error ->
            reduce { state.copy(actionError = error.message.orEmpty()) }
        }
    }

    fun toggleRule(ruleId: String, enabled: Boolean) = intent {
        runCatching {
            ruleRepository.updateRules { rules ->
                rules.map { rule ->
                    if (rule.id == ruleId) {
                        rule.copy(enabled = enabled)
                    } else {
                        rule
                    }
                }
            }
        }.onFailure { error ->
            reduce { state.copy(actionError = error.message.orEmpty()) }
        }
    }

    fun clearActionError() = intent {
        reduce { state.copy(actionError = null) }
    }

    private fun observeRules() = intent {
        ruleRepository.rules.collect { rules ->
            reduce {
                state.copy(
                    rules = rules.sortedWith(
                        compareBy<RuleDefinition> { rule -> rule.priority }
                            .thenBy { rule -> rule.name.lowercase() }
                            .thenBy { rule -> rule.id },
                    ),
                )
            }
        }
    }
}
