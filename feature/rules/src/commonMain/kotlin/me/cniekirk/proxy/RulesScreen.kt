package me.cniekirk.proxy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.ui.CompactButton
import me.cniekirk.proxy.ui.CompactDropdown
import me.cniekirk.proxy.ui.CompactTextField
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import proxy.feature.rules.generated.resources.*
import kotlin.random.Random

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    onCloseRequest: () -> Unit,
) {
    val state by viewModel.collectAsState()
    var selectedRuleId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(RuleDraft.empty()) }
    var localValidationError by remember { mutableStateOf<RuleValidationError?>(null) }

    LaunchedEffect(state.rules) {
        if (state.rules.isEmpty()) {
            selectedRuleId = null
            draft = RuleDraft.empty()
            return@LaunchedEffect
        }

        val selectedStillExists = selectedRuleId?.let { selectedId ->
            state.rules.any { rule -> rule.id == selectedId }
        } == true
        if (!selectedStillExists) {
            val firstRule = state.rules.first()
            selectedRuleId = firstRule.id
            draft = RuleDraft.fromRule(firstRule)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeaderBar(
            ruleCount = state.rules.size,
            onNewRule = {
                selectedRuleId = null
                draft = RuleDraft.empty()
                localValidationError = null
            },
            onCloseRequest = onCloseRequest,
        )

        state.actionError?.let { message ->
            val resolvedMessage = message.ifBlank {
                stringResource(Res.string.rules_error_generic_action_failed)
            }
            ErrorBanner(
                message = resolvedMessage,
                onDismiss = viewModel::clearActionError,
            )
        }
        localValidationError?.let { validationError ->
            ErrorBanner(
                message = validationError.resolveMessage(),
                onDismiss = { localValidationError = null },
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuleListPane(
                rules = state.rules,
                selectedRuleId = selectedRuleId,
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight(),
                onSelectRule = { selectedRule ->
                    selectedRuleId = selectedRule.id
                    draft = RuleDraft.fromRule(selectedRule)
                    localValidationError = null
                },
                onToggleRule = viewModel::toggleRule,
            )

            RuleEditorPane(
                draft = draft,
                isEditingExistingRule = selectedRuleId != null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onDraftChanged = { updatedDraft ->
                    draft = updatedDraft
                },
                onSave = {
                    val validationError = validateDraft(draft)
                    if (validationError != null) {
                        localValidationError = validationError
                        return@RuleEditorPane
                    }

                    val resolvedRuleId = selectedRuleId ?: generateRuleId()
                    val normalizedPriority = draft.priorityText.toIntOrNull() ?: 100
                    val rule = draft.toRule(
                        id = resolvedRuleId,
                        priority = normalizedPriority,
                    )
                    viewModel.saveRule(rule)
                    selectedRuleId = resolvedRuleId
                    draft = RuleDraft.fromRule(rule)
                    localValidationError = null
                },
                onDelete = {
                    val selectedId = selectedRuleId ?: return@RuleEditorPane
                    viewModel.deleteRule(selectedId)
                    selectedRuleId = null
                    draft = RuleDraft.empty()
                    localValidationError = null
                },
            )
        }
    }
}

@Composable
private fun HeaderBar(
    ruleCount: Int,
    onNewRule: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.rules_header_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.rules_header_configured_count, ruleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactButton(
                    label = stringResource(Res.string.rules_action_new_rule),
                    onClick = onNewRule,
                )
                CompactButton(
                    label = stringResource(Res.string.rules_action_close),
                    onClick = onCloseRequest,
                )
            }
        }
    }
}

@Composable
private fun RuleListPane(
    rules: List<RuleDefinition>,
    selectedRuleId: String?,
    onSelectRule: (RuleDefinition) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.rules_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = rules, key = { rule -> rule.id }) { rule ->
                    val isSelected = selectedRuleId == rule.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable { onSelectRule(rule) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = rule.name.ifBlank {
                                    stringResource(Res.string.rules_unnamed_rule)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    onToggleRule(rule.id, enabled)
                                },
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Text(
                            text = describeRuleMatcher(rule.matcher),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                Res.string.rules_rule_summary,
                                rule.actions.size,
                                rule.priority,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
private fun RuleEditorPane(
    draft: RuleDraft,
    isEditingExistingRule: Boolean,
    onDraftChanged: (RuleDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionCard(title = stringResource(Res.string.rules_section_rule_details)) {
                    CompactTextField(
                        value = draft.name,
                        onValueChange = { value ->
                            onDraftChanged(draft.copy(name = value))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.rules_field_rule_name),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactTextField(
                            value = draft.priorityText,
                            onValueChange = { value ->
                                onDraftChanged(draft.copy(priorityText = value))
                            },
                            modifier = Modifier.width(120.dp),
                            label = stringResource(Res.string.rules_field_priority),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.rules_toggle_enabled),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(
                                checked = draft.enabled,
                                onCheckedChange = { value ->
                                    onDraftChanged(draft.copy(enabled = value))
                                },
                            )
                        }
                    }
                }

                SectionCard(title = stringResource(Res.string.rules_section_matchers)) {
                    MatchFieldEditor(
                        label = stringResource(Res.string.rules_matcher_scheme),
                        field = draft.scheme,
                        placeholder = stringResource(Res.string.rules_matcher_placeholder_scheme),
                        onChanged = { updated ->
                            onDraftChanged(draft.copy(scheme = updated))
                        },
                    )
                    MatchFieldEditor(
                        label = stringResource(Res.string.rules_matcher_host),
                        field = draft.host,
                        placeholder = stringResource(Res.string.rules_matcher_placeholder_host),
                        onChanged = { updated ->
                            onDraftChanged(draft.copy(host = updated))
                        },
                    )
                    MatchFieldEditor(
                        label = stringResource(Res.string.rules_matcher_path),
                        field = draft.path,
                        placeholder = stringResource(Res.string.rules_matcher_placeholder_path),
                        onChanged = { updated ->
                            onDraftChanged(draft.copy(path = updated))
                        },
                    )
                    MatchFieldEditor(
                        label = stringResource(Res.string.rules_matcher_port),
                        field = draft.port,
                        placeholder = stringResource(Res.string.rules_matcher_placeholder_port),
                        onChanged = { updated ->
                            onDraftChanged(draft.copy(port = updated))
                        },
                    )
                }

                SectionCard(title = stringResource(Res.string.rules_section_actions)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        draft.actions.forEachIndexed { index, action ->
                            ActionEditorCard(
                                index = index,
                                action = action,
                                onChanged = { updated ->
                                    onDraftChanged(
                                        draft.copy(
                                            actions = draft.actions.replaceAt(
                                                index = index,
                                                value = updated,
                                            ),
                                        ),
                                    )
                                },
                                onRemove = {
                                    onDraftChanged(
                                        draft.copy(
                                            actions = draft.actions.filterIndexed { actionIndex, _ ->
                                                actionIndex != index
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                        CompactButton(
                            label = stringResource(Res.string.rules_action_add_action),
                            onClick = {
                                onDraftChanged(
                                    draft.copy(actions = draft.actions + RuleActionDraft.empty()),
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditingExistingRule) {
                    CompactButton(
                        label = stringResource(Res.string.rules_action_delete_rule),
                        onClick = onDelete,
                    )
                } else {
                    Box(modifier = Modifier.size(1.dp))
                }
                CompactButton(
                    label = if (isEditingExistingRule) {
                        stringResource(Res.string.rules_action_save_changes)
                    } else {
                        stringResource(Res.string.rules_action_create_rule)
                    },
                    onClick = onSave,
                )
            }
        }
    }
}

@Composable
private fun MatchFieldEditor(
    label: String,
    field: RuleMatchFieldDraft,
    placeholder: String,
    onChanged: (RuleMatchFieldDraft) -> Unit,
) {
    val modeLabels = rememberRuleMatchModeLabels()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp),
        )
        CompactDropdown(
            selected = field.mode,
            values = RuleMatchMode.entries.toList(),
            itemLabel = { mode -> modeLabels.getValue(mode) },
            modifier = Modifier.width(120.dp),
            onSelected = { mode ->
                onChanged(field.copy(mode = mode))
            },
        )
        CompactTextField(
            value = field.value,
            onValueChange = { value ->
                onChanged(field.copy(value = value))
            },
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            enabled = field.mode != RuleMatchMode.ANY,
            singleLine = true,
        )
    }
}

@Composable
private fun ActionEditorCard(
    index: Int,
    action: RuleActionDraft,
    onChanged: (RuleActionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val targetLabels = mapOf(
        RuleTarget.REQUEST to stringResource(Res.string.rules_target_request),
        RuleTarget.RESPONSE to stringResource(Res.string.rules_target_response),
    )
    val actionTypeLabels = mapOf(
        RuleActionType.SET_HEADER to stringResource(Res.string.rules_action_type_set_header),
        RuleActionType.REMOVE_HEADER to stringResource(Res.string.rules_action_type_remove_header),
        RuleActionType.REPLACE_BODY to stringResource(Res.string.rules_action_type_replace_body),
    )

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.rules_action_card_title, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                CompactButton(
                    label = stringResource(Res.string.rules_action_remove),
                    onClick = onRemove,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactDropdown(
                    selected = action.target,
                    values = RuleTarget.entries.toList(),
                    itemLabel = { target -> targetLabels.getValue(target) },
                    modifier = Modifier.width(140.dp),
                    onSelected = { target ->
                        onChanged(action.copy(target = target))
                    },
                )
                CompactDropdown(
                    selected = action.type,
                    values = RuleActionType.entries.toList(),
                    itemLabel = { type -> actionTypeLabels.getValue(type) },
                    modifier = Modifier.width(160.dp),
                    onSelected = { type ->
                        onChanged(action.copy(type = type))
                    },
                )
            }

            when (action.type) {
                RuleActionType.SET_HEADER -> {
                    CompactTextField(
                        value = action.headerName,
                        onValueChange = { value ->
                            onChanged(action.copy(headerName = value))
                        },
                        label = stringResource(Res.string.rules_field_header_name),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    CompactTextField(
                        value = action.headerValue,
                        onValueChange = { value ->
                            onChanged(action.copy(headerValue = value))
                        },
                        label = stringResource(Res.string.rules_field_header_value),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                RuleActionType.REMOVE_HEADER -> {
                    CompactTextField(
                        value = action.headerName,
                        onValueChange = { value ->
                            onChanged(action.copy(headerName = value))
                        },
                        label = stringResource(Res.string.rules_field_header_name),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                RuleActionType.REPLACE_BODY -> {
                    CompactTextField(
                        value = action.bodyValue,
                        onValueChange = { value ->
                            onChanged(action.copy(bodyValue = value))
                        },
                        label = stringResource(Res.string.rules_field_replacement_body),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                    )
                    CompactTextField(
                        value = action.contentType,
                        onValueChange = { value ->
                            onChanged(action.copy(contentType = value))
                        },
                        label = stringResource(Res.string.rules_field_content_type_optional),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            CompactButton(
                label = stringResource(Res.string.rules_action_dismiss),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun RuleValidationError.resolveMessage(): String {
    return when (this) {
        RuleValidationError.NameRequired -> stringResource(Res.string.rules_validation_name_required)
        RuleValidationError.PriorityMustBeInteger -> {
            stringResource(Res.string.rules_validation_priority_integer)
        }

        is RuleValidationError.MatcherRequiresValue -> {
            stringResource(
                Res.string.rules_validation_matcher_requires_value,
                matcherFieldLabel(field),
            )
        }

        is RuleValidationError.MatcherInvalidRegex -> {
            stringResource(
                Res.string.rules_validation_matcher_invalid_regex,
                matcherFieldLabel(field),
            )
        }

        RuleValidationError.PortExactOutOfRange -> stringResource(Res.string.rules_validation_port_range)
        RuleValidationError.AtLeastOneActionRequired -> stringResource(Res.string.rules_validation_add_action)
        is RuleValidationError.ActionHeaderNameRequired -> {
            stringResource(
                Res.string.rules_validation_action_header_required,
                actionNumber,
            )
        }
    }
}

@Composable
private fun matcherFieldLabel(field: RuleMatcherFieldKey): String {
    return when (field) {
        RuleMatcherFieldKey.Scheme -> stringResource(Res.string.rules_matcher_scheme)
        RuleMatcherFieldKey.Host -> stringResource(Res.string.rules_matcher_host)
        RuleMatcherFieldKey.Path -> stringResource(Res.string.rules_matcher_path)
        RuleMatcherFieldKey.Port -> stringResource(Res.string.rules_matcher_port)
    }
}

@Composable
private fun rememberRuleMatchModeLabels(): Map<RuleMatchMode, String> {
    val any = stringResource(Res.string.rules_matcher_mode_any)
    val exact = stringResource(Res.string.rules_matcher_mode_exact)
    val wildcard = stringResource(Res.string.rules_matcher_mode_wildcard)
    val regex = stringResource(Res.string.rules_matcher_mode_regex)
    return remember(any, exact, wildcard, regex) {
        mapOf(
            RuleMatchMode.ANY to any,
            RuleMatchMode.EXACT to exact,
            RuleMatchMode.WILDCARD to wildcard,
            RuleMatchMode.REGEX to regex,
        )
    }
}

private fun validateDraft(draft: RuleDraft): RuleValidationError? {
    if (draft.name.isBlank()) {
        return RuleValidationError.NameRequired
    }

    val priority = draft.priorityText.toIntOrNull()
    if (priority == null) {
        return RuleValidationError.PriorityMustBeInteger
    }

    val matcherFields = listOf(
        RuleMatcherFieldKey.Scheme to draft.scheme,
        RuleMatcherFieldKey.Host to draft.host,
        RuleMatcherFieldKey.Path to draft.path,
        RuleMatcherFieldKey.Port to draft.port,
    )
    matcherFields.forEach { (fieldKey, field) ->
        if (field.mode != RuleMatchMode.ANY && field.value.isBlank()) {
            return RuleValidationError.MatcherRequiresValue(fieldKey)
        }
        if (field.mode == RuleMatchMode.REGEX && field.value.isNotBlank()) {
            val isValidRegex = runCatching { Regex(field.value) }.isSuccess
            if (!isValidRegex) {
                return RuleValidationError.MatcherInvalidRegex(fieldKey)
            }
        }
    }

    if (draft.port.mode == RuleMatchMode.EXACT && draft.port.value.isNotBlank()) {
        val port = draft.port.value.toIntOrNull()
        if (port == null || port !in 1..65535) {
            return RuleValidationError.PortExactOutOfRange
        }
    }

    if (draft.actions.isEmpty()) {
        return RuleValidationError.AtLeastOneActionRequired
    }

    draft.actions.forEachIndexed { index, action ->
        when (action.type) {
            RuleActionType.SET_HEADER,
            RuleActionType.REMOVE_HEADER,
            -> {
                if (action.headerName.isBlank()) {
                    return RuleValidationError.ActionHeaderNameRequired(actionNumber = index + 1)
                }
            }

            RuleActionType.REPLACE_BODY -> {
                // Empty body is valid; this lets the user explicitly clear the body.
            }
        }
    }

    return null
}

@Composable
private fun describeRuleMatcher(matcher: RuleMatcher): String {
    val anyLabel = stringResource(Res.string.rules_matcher_summary_any)
    val modeLabels = rememberRuleMatchModeLabels()

    return buildString {
        append(
            describeField(
                label = stringResource(Res.string.rules_matcher_scheme),
                field = matcher.scheme,
                anyLabel = anyLabel,
                modeLabels = modeLabels,
            ),
        )
        append(" • ")
        append(
            describeField(
                label = stringResource(Res.string.rules_matcher_host),
                field = matcher.host,
                anyLabel = anyLabel,
                modeLabels = modeLabels,
            ),
        )
        append(" • ")
        append(
            describeField(
                label = stringResource(Res.string.rules_matcher_path),
                field = matcher.path,
                anyLabel = anyLabel,
                modeLabels = modeLabels,
            ),
        )
        append(" • ")
        append(
            describeField(
                label = stringResource(Res.string.rules_matcher_port),
                field = matcher.port,
                anyLabel = anyLabel,
                modeLabels = modeLabels,
            ),
        )
    }
}

@Composable
private fun describeField(
    label: String,
    field: RuleMatchField,
    anyLabel: String,
    modeLabels: Map<RuleMatchMode, String>,
): String {
    return if (field.mode == RuleMatchMode.ANY) {
        stringResource(
            Res.string.rules_matcher_summary_any_field,
            label,
            anyLabel,
        )
    } else {
        stringResource(
            Res.string.rules_matcher_summary_field_value,
            label,
            modeLabels.getValue(field.mode),
            field.value,
        )
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> {
    return mapIndexed { currentIndex, current ->
        if (currentIndex == index) value else current
    }
}

private fun generateRuleId(): String {
    return "rule-${Random.nextLong().toString(16)}"
}

private fun generateActionId(): String {
    return "action-${Random.nextLong().toString(16)}"
}

private enum class RuleMatcherFieldKey {
    Scheme,
    Host,
    Path,
    Port,
}

private sealed interface RuleValidationError {
    data object NameRequired : RuleValidationError
    data object PriorityMustBeInteger : RuleValidationError
    data class MatcherRequiresValue(val field: RuleMatcherFieldKey) : RuleValidationError
    data class MatcherInvalidRegex(val field: RuleMatcherFieldKey) : RuleValidationError
    data object PortExactOutOfRange : RuleValidationError
    data object AtLeastOneActionRequired : RuleValidationError
    data class ActionHeaderNameRequired(val actionNumber: Int) : RuleValidationError
}

private data class RuleDraft(
    val name: String,
    val enabled: Boolean,
    val priorityText: String,
    val scheme: RuleMatchFieldDraft,
    val host: RuleMatchFieldDraft,
    val path: RuleMatchFieldDraft,
    val port: RuleMatchFieldDraft,
    val actions: List<RuleActionDraft>,
) {
    fun toRule(id: String, priority: Int): RuleDefinition {
        return RuleDefinition(
            id = id,
            name = name.trim(),
            enabled = enabled,
            priority = priority,
            matcher = RuleMatcher(
                scheme = scheme.toModel(),
                host = host.toModel(),
                path = path.toModel(),
                port = port.toModel(),
            ),
            actions = actions.map { action ->
                RuleAction(
                    id = action.id,
                    target = action.target,
                    type = action.type,
                    headerName = action.headerName.trim().ifBlank { null },
                    headerValue = action.headerValue,
                    bodyValue = action.bodyValue,
                    contentType = action.contentType.trim().ifBlank { null },
                )
            },
        )
    }

    companion object {
        fun fromRule(rule: RuleDefinition): RuleDraft {
            return RuleDraft(
                name = rule.name,
                enabled = rule.enabled,
                priorityText = rule.priority.toString(),
                scheme = RuleMatchFieldDraft.fromModel(rule.matcher.scheme),
                host = RuleMatchFieldDraft.fromModel(rule.matcher.host),
                path = RuleMatchFieldDraft.fromModel(rule.matcher.path),
                port = RuleMatchFieldDraft.fromModel(rule.matcher.port),
                actions = rule.actions.map { action ->
                    RuleActionDraft(
                        id = action.id,
                        target = action.target,
                        type = action.type,
                        headerName = action.headerName.orEmpty(),
                        headerValue = action.headerValue.orEmpty(),
                        bodyValue = action.bodyValue.orEmpty(),
                        contentType = action.contentType.orEmpty(),
                    )
                },
            )
        }

        fun empty(): RuleDraft {
            return RuleDraft(
                name = "",
                enabled = true,
                priorityText = "100",
                scheme = RuleMatchFieldDraft.empty(),
                host = RuleMatchFieldDraft.empty(),
                path = RuleMatchFieldDraft.empty(),
                port = RuleMatchFieldDraft.empty(),
                actions = listOf(RuleActionDraft.empty()),
            )
        }
    }
}

private data class RuleMatchFieldDraft(
    val mode: RuleMatchMode,
    val value: String,
) {
    fun toModel(): RuleMatchField {
        return RuleMatchField(
            mode = mode,
            value = value,
        )
    }

    companion object {
        fun fromModel(field: RuleMatchField): RuleMatchFieldDraft {
            return RuleMatchFieldDraft(
                mode = field.mode,
                value = field.value,
            )
        }

        fun empty(): RuleMatchFieldDraft {
            return RuleMatchFieldDraft(
                mode = RuleMatchMode.ANY,
                value = "",
            )
        }
    }
}

private data class RuleActionDraft(
    val id: String,
    val target: RuleTarget,
    val type: RuleActionType,
    val headerName: String,
    val headerValue: String,
    val bodyValue: String,
    val contentType: String,
) {
    companion object {
        fun empty(): RuleActionDraft {
            return RuleActionDraft(
                id = generateActionId(),
                target = RuleTarget.REQUEST,
                type = RuleActionType.SET_HEADER,
                headerName = "",
                headerValue = "",
                bodyValue = "",
                contentType = "",
            )
        }
    }
}
