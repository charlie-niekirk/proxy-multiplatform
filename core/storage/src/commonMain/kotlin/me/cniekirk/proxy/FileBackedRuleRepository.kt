package me.cniekirk.proxy

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FileBackedRuleRepository : RuleRepository {
    private val dataStore: DataStore<RulesStore> = DataStoreFactory.create(
        serializer = RulesStoreSerializer,
        produceFile = {
            File(FILE_PATH).also { it.parentFile?.mkdirs() }
        },
    )

    override val rules: Flow<List<RuleDefinition>> = dataStore.data
        .map { store -> store.rules.sortedBy { rule -> rule.priority } }

    override suspend fun upsertRule(rule: RuleDefinition) {
        dataStore.updateData { current ->
            val withoutExisting = current.rules.filterNot { existing -> existing.id == rule.id }
            current.copy(
                rules = (withoutExisting + rule).sortedBy { candidate -> candidate.priority },
            )
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        dataStore.updateData { current ->
            current.copy(
                rules = current.rules.filterNot { rule -> rule.id == ruleId },
            )
        }
    }

    override suspend fun updateRules(transform: (List<RuleDefinition>) -> List<RuleDefinition>) {
        dataStore.updateData { current ->
            current.copy(
                rules = transform(current.rules).sortedBy { rule -> rule.priority },
            )
        }
    }

    private companion object {
        const val FILE_PATH = "rules.json"
    }
}
