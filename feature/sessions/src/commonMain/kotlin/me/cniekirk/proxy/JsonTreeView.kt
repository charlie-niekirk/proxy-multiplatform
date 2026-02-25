package me.cniekirk.proxy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.cniekirk.proxy.ui.CompactButton
import me.cniekirk.proxy.ui.CompactTextField

@Composable
internal fun JsonTreeView(
    json: String,
    modifier: Modifier = Modifier,
) {
    println("JSON: $json")
    val parseResult = remember(json) {
        runCatching { JsonTreeParser.parseToJsonElement(json) }
    }
    val parsedElement = parseResult.getOrNull()
    val monoTextStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    if (parsedElement == null) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Unable to parse JSON. Showing raw text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            parseResult.exceptionOrNull()?.localizedMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = json,
                style = monoTextStyle,
            )
        }
        return
    }

    val rootNode = remember(parsedElement) {
        buildTreeNode(
            element = parsedElement,
            id = "\$",
            parentId = null,
            depth = 0,
            key = null,
            index = null,
        )
    }
    val orderedNodes = remember(rootNode) { flattenTreeNodes(rootNode) }
    val parentById = remember(orderedNodes) {
        orderedNodes.associate { node -> node.id to node.parentId }
    }
    val rowTextById = remember(orderedNodes) {
        orderedNodes.associate { node -> node.id to nodeText(node) }
    }
    val expandedNodes = remember(rootNode) {
        mutableStateMapOf<String, Boolean>().apply {
            initializeExpansionMap(rootNode, this)
        }
    }
    val bringIntoViewByNode = remember(rootNode) { mutableMapOf<String, BringIntoViewRequester>() }

    var searchText by remember(rootNode) { mutableStateOf("") }
    var regexEnabled by remember(rootNode) { mutableStateOf(false) }
    var searchHits by remember(rootNode) { mutableStateOf(emptyList<JsonSearchHit>()) }
    var selectedHitIndex by remember(rootNode) { mutableStateOf(-1) }
    var searchError by remember(rootNode) { mutableStateOf<String?>(null) }

    val selectedHit = searchHits.getOrNull(selectedHitIndex)
    val hitRangesByNode = remember(searchHits) {
        searchHits.groupBy(keySelector = { hit -> hit.nodeId }, valueTransform = { hit -> hit.range })
    }

    LaunchedEffect(selectedHit?.nodeId, selectedHit?.range?.first, selectedHit?.range?.last) {
        val hit = selectedHit ?: return@LaunchedEffect
        expandAncestors(
            nodeId = hit.nodeId,
            parentById = parentById,
            expandedNodes = expandedNodes,
        )
        repeat(8) {
            val requester = bringIntoViewByNode[hit.nodeId]
            if (requester != null) {
                requester.bringIntoView()
                return@LaunchedEffect
            }
            delay(16L)
        }
    }

    val runFind: () -> Unit = {
        val computation = computeSearchHits(
            query = searchText,
            regexEnabled = regexEnabled,
            orderedNodes = orderedNodes,
            rowTextById = rowTextById,
        )
        searchError = computation.error
        searchHits = computation.hits
        selectedHitIndex = if (computation.hits.isEmpty()) -1 else 0
    }

    val runFindNext: () -> Unit = {
        if (searchHits.isEmpty()) {
            val computation = computeSearchHits(
                query = searchText,
                regexEnabled = regexEnabled,
                orderedNodes = orderedNodes,
                rowTextById = rowTextById,
            )
            searchError = computation.error
            searchHits = computation.hits
            selectedHitIndex = if (computation.hits.isEmpty()) -1 else 0
        } else {
            selectedHitIndex = when {
                searchHits.isEmpty() -> -1
                selectedHitIndex + 1 >= searchHits.size -> 0
                else -> selectedHitIndex + 1
            }
        }
    }

    val searchSummary = when {
        searchText.isBlank() -> "Enter search text"
        searchError != null -> "Invalid regex"
        searchHits.isEmpty() -> "No matches"
        else -> "${selectedHitIndex + 1} of ${searchHits.size}"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactTextField(
            value = searchText,
            onValueChange = { value ->
                searchText = value
                searchHits = emptyList()
                selectedHitIndex = -1
                searchError = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = monoTextStyle,
            label = "Find in JSON",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = regexEnabled,
                    onCheckedChange = { enabled ->
                        regexEnabled = enabled
                        searchHits = emptyList()
                        selectedHitIndex = -1
                        searchError = null
                    },
                )
                Text(
                    text = "Regex",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = searchSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (searchError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            CompactButton(
                label = "Find",
                onClick = runFind,
                enabled = searchText.isNotBlank(),
            )
            CompactButton(
                label = "Next",
                onClick = runFindNext,
                enabled = searchText.isNotBlank(),
            )
        }

        JsonTreeNodeRow(
            node = rootNode,
            expandedNodes = expandedNodes,
            hitRangesByNode = hitRangesByNode,
            selectedHit = selectedHit,
            bringIntoViewByNode = bringIntoViewByNode,
        )
    }
}

@Composable
private fun JsonTreeNodeRow(
    node: JsonTreeNode,
    expandedNodes: SnapshotStateMap<String, Boolean>,
    hitRangesByNode: Map<String, List<IntRange>>,
    selectedHit: JsonSearchHit?,
    bringIntoViewByNode: MutableMap<String, BringIntoViewRequester>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val monoTextStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    val hasChildren = node.children.isNotEmpty()
    val expanded = expandedNodes[node.id] ?: false
    val requester = remember(node.id) { BringIntoViewRequester() }
    val textPieces = remember(node.id) { buildNodeTextPieces(node) }
    val rowMatchRanges = hitRangesByNode[node.id].orEmpty()
    val activeRange = selectedHit?.takeIf { hit -> hit.nodeId == node.id }?.range
    val activeRow = activeRange != null
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "JsonTreeChevron",
    )

    DisposableEffect(node.id, requester, bringIntoViewByNode) {
        bringIntoViewByNode[node.id] = requester
        onDispose {
            bringIntoViewByNode.remove(node.id)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .then(
                if (activeRow) {
                    Modifier.background(colorScheme.primary.copy(alpha = 0.08f))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = hasChildren) {
                expandedNodes[node.id] = !expanded
            }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(modifier = Modifier.width((node.depth * 14).dp))

        Box(
            modifier = Modifier.width(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hasChildren) {
                Text(
                    text = ">",
                    modifier = Modifier.rotate(chevronRotation),
                    style = monoTextStyle,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = buildAnnotatedNodeText(
                pieces = textPieces,
                colorScheme = colorScheme,
                matchRanges = rowMatchRanges,
                activeRange = activeRange,
            ),
            style = monoTextStyle,
        )
    }

    if (hasChildren) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 140)) +
                fadeIn(animationSpec = tween(durationMillis = 90)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 140)) +
                fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Column {
                node.children.forEach { child ->
                    JsonTreeNodeRow(
                        node = child,
                        expandedNodes = expandedNodes,
                        hitRangesByNode = hitRangesByNode,
                        selectedHit = selectedHit,
                        bringIntoViewByNode = bringIntoViewByNode,
                    )
                }
            }
        }
    }
}

private data class JsonTreeNode(
    val id: String,
    val parentId: String?,
    val depth: Int,
    val key: String?,
    val index: Int?,
    val element: JsonElement,
    val children: List<JsonTreeNode>,
)

private data class JsonSearchHit(
    val nodeId: String,
    val range: IntRange,
)

private data class JsonSearchComputation(
    val hits: List<JsonSearchHit>,
    val error: String?,
)

private enum class JsonTokenType {
    Punctuation,
    Key,
    StringValue,
    NumberValue,
    BooleanValue,
    NullValue,
    Summary,
}

private data class JsonTextPiece(
    val text: String,
    val type: JsonTokenType,
)

private object JsonTreeParser {
    val json = Json {
        isLenient = true
        allowTrailingComma = true
        allowSpecialFloatingPointValues = true
    }

    fun parseToJsonElement(raw: String): JsonElement {
        val normalized = raw
            .removePrefix("\uFEFF")
            .trim()

        parseOrNull(normalized)?.let { element ->
            return element
        }

        parseEmbeddedTopLevelJson(normalized)?.let { element ->
            return element
        }

        return json.parseToJsonElement(normalized)
    }

    private fun parseOrNull(input: String): JsonElement? {
        return runCatching { json.parseToJsonElement(input) }.getOrNull()
    }

    private fun parseEmbeddedTopLevelJson(input: String): JsonElement? {
        val firstNonWhitespace = input.indexOfFirst { character -> !character.isWhitespace() }
        if (firstNonWhitespace < 0) {
            return null
        }

        val startIndex = when (input[firstNonWhitespace]) {
            '{',
            '[',
            -> firstNonWhitespace

            else -> input.indexOfAny(
                chars = charArrayOf('{', '['),
                startIndex = firstNonWhitespace,
            )
        }
        if (startIndex < 0) {
            return null
        }

        val endIndex = findTopLevelJsonEnd(
            input = input,
            startIndex = startIndex,
        ) ?: return null

        val candidate = input.substring(startIndex, endIndex + 1)
        return parseOrNull(candidate)
    }

    private fun findTopLevelJsonEnd(
        input: String,
        startIndex: Int,
    ): Int? {
        if (startIndex !in input.indices) {
            return null
        }
        val openingChar = input[startIndex]
        if (openingChar != '{' && openingChar != '[') {
            return null
        }

        val stack = ArrayDeque<Char>()
        stack.addLast(openingChar)

        var insideString = false
        var escaped = false

        var currentIndex = startIndex + 1
        while (currentIndex < input.length) {
            val character = input[currentIndex]

            if (insideString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> insideString = false
                }
                currentIndex++
                continue
            }

            when (character) {
                '"' -> insideString = true
                '{' -> stack.addLast('{')
                '[' -> stack.addLast('[')
                '}' -> {
                    if (stack.removeLastOrNull() != '{') {
                        return null
                    }
                    if (stack.isEmpty()) {
                        return currentIndex
                    }
                }

                ']' -> {
                    if (stack.removeLastOrNull() != '[') {
                        return null
                    }
                    if (stack.isEmpty()) {
                        return currentIndex
                    }
                }
            }

            currentIndex++
        }

        return null
    }
}

private fun buildTreeNode(
    element: JsonElement,
    id: String,
    parentId: String?,
    depth: Int,
    key: String?,
    index: Int?,
): JsonTreeNode {
    val children = when (element) {
        is JsonObject -> element.entries.mapIndexed { position, (childKey, childElement) ->
            buildTreeNode(
                element = childElement,
                id = "$id.o$position",
                parentId = id,
                depth = depth + 1,
                key = childKey,
                index = null,
            )
        }

        is JsonArray -> element.mapIndexed { childIndex, childElement ->
            buildTreeNode(
                element = childElement,
                id = "$id.a$childIndex",
                parentId = id,
                depth = depth + 1,
                key = null,
                index = childIndex,
            )
        }

        is JsonPrimitive -> emptyList()
    }

    return JsonTreeNode(
        id = id,
        parentId = parentId,
        depth = depth,
        key = key,
        index = index,
        element = element,
        children = children,
    )
}

private fun flattenTreeNodes(root: JsonTreeNode): List<JsonTreeNode> {
    val nodes = mutableListOf<JsonTreeNode>()

    fun walk(node: JsonTreeNode) {
        nodes += node
        node.children.forEach(::walk)
    }

    walk(root)
    return nodes
}

private fun initializeExpansionMap(
    node: JsonTreeNode,
    expandedNodes: SnapshotStateMap<String, Boolean>,
) {
    if (node.children.isNotEmpty()) {
        expandedNodes[node.id] = node.depth <= 1
    }
    node.children.forEach { child ->
        initializeExpansionMap(
            node = child,
            expandedNodes = expandedNodes,
        )
    }
}

private fun expandAncestors(
    nodeId: String,
    parentById: Map<String, String?>,
    expandedNodes: SnapshotStateMap<String, Boolean>,
) {
    var currentParent = parentById[nodeId]
    while (currentParent != null) {
        expandedNodes[currentParent] = true
        currentParent = parentById[currentParent]
    }
}

private fun computeSearchHits(
    query: String,
    regexEnabled: Boolean,
    orderedNodes: List<JsonTreeNode>,
    rowTextById: Map<String, String>,
): JsonSearchComputation {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
        return JsonSearchComputation(
            hits = emptyList(),
            error = null,
        )
    }

    val regex = if (regexEnabled) {
        try {
            Regex(normalizedQuery)
        } catch (error: IllegalArgumentException) {
            return JsonSearchComputation(
                hits = emptyList(),
                error = error.localizedMessage ?: "Invalid regex pattern",
            )
        }
    } else {
        null
    }

    val hits = mutableListOf<JsonSearchHit>()
    orderedNodes.forEach { node ->
        val rowText = rowTextById[node.id] ?: return@forEach
        val ranges = if (regex != null) {
            findRegexRanges(rowText, regex)
        } else {
            findLiteralRanges(rowText, normalizedQuery)
        }
        ranges.forEach { range ->
            hits += JsonSearchHit(
                nodeId = node.id,
                range = range,
            )
        }
    }

    return JsonSearchComputation(
        hits = hits,
        error = null,
    )
}

private fun findLiteralRanges(
    text: String,
    query: String,
): List<IntRange> {
    if (query.isEmpty()) {
        return emptyList()
    }

    val ranges = mutableListOf<IntRange>()
    var fromIndex = 0
    while (fromIndex <= text.length - query.length) {
        val start = text.indexOf(
            string = query,
            startIndex = fromIndex,
        )
        if (start < 0) {
            break
        }
        ranges += (start until (start + query.length))
        fromIndex = start + query.length
    }
    return ranges
}

private fun findRegexRanges(
    text: String,
    regex: Regex,
): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    regex.findAll(text).forEach { match ->
        if (match.value.isNotEmpty()) {
            ranges += match.range
        }
    }
    return ranges
}

private fun nodeText(node: JsonTreeNode): String {
    val pieces = buildNodeTextPieces(node)
    return buildString {
        pieces.forEach { piece ->
            append(piece.text)
        }
    }
}

private fun buildNodeTextPieces(node: JsonTreeNode): List<JsonTextPiece> {
    val pieces = mutableListOf<JsonTextPiece>()

    when {
        node.parentId == null -> {
            pieces += JsonTextPiece("\$: ", JsonTokenType.Punctuation)
        }

        node.key != null -> {
            pieces += JsonTextPiece("\"", JsonTokenType.Punctuation)
            pieces += JsonTextPiece(escapeJsonString(node.key), JsonTokenType.Key)
            pieces += JsonTextPiece("\": ", JsonTokenType.Punctuation)
        }

        node.index != null -> {
            pieces += JsonTextPiece("[${node.index}]: ", JsonTokenType.Punctuation)
        }
    }

    when (val value = node.element) {
        is JsonObject -> {
            if (value.isEmpty()) {
                pieces += JsonTextPiece("{}", JsonTokenType.Punctuation)
            } else {
                pieces += JsonTextPiece("{", JsonTokenType.Punctuation)
                pieces += JsonTextPiece("${value.size} keys", JsonTokenType.Summary)
                pieces += JsonTextPiece("}", JsonTokenType.Punctuation)
            }
        }

        is JsonArray -> {
            if (value.isEmpty()) {
                pieces += JsonTextPiece("[]", JsonTokenType.Punctuation)
            } else {
                pieces += JsonTextPiece("[", JsonTokenType.Punctuation)
                pieces += JsonTextPiece("${value.size} items", JsonTokenType.Summary)
                pieces += JsonTextPiece("]", JsonTokenType.Punctuation)
            }
        }

        is JsonPrimitive -> {
            when {
                value.isString -> {
                    pieces += JsonTextPiece("\"", JsonTokenType.Punctuation)
                    pieces += JsonTextPiece(escapeJsonString(value.content), JsonTokenType.StringValue)
                    pieces += JsonTextPiece("\"", JsonTokenType.Punctuation)
                }

                value.content == "null" -> {
                    pieces += JsonTextPiece("null", JsonTokenType.NullValue)
                }

                value.content == "true" || value.content == "false" -> {
                    pieces += JsonTextPiece(value.content, JsonTokenType.BooleanValue)
                }

                value.content.toDoubleOrNull() != null -> {
                    pieces += JsonTextPiece(value.content, JsonTokenType.NumberValue)
                }

                else -> {
                    pieces += JsonTextPiece(value.content, JsonTokenType.Summary)
                }
            }
        }
    }

    return pieces
}

private fun buildAnnotatedNodeText(
    pieces: List<JsonTextPiece>,
    colorScheme: ColorScheme,
    matchRanges: List<IntRange>,
    activeRange: IntRange?,
): AnnotatedString {
    val text = buildString {
        pieces.forEach { piece ->
            append(piece.text)
        }
    }

    return buildAnnotatedString {
        append(text)

        var offset = 0
        pieces.forEach { piece ->
            val start = offset
            val endExclusive = start + piece.text.length
            val style = tokenStyle(piece.type, colorScheme)
            if (style != null && start < endExclusive) {
                addStyle(style, start, endExclusive)
            }
            offset = endExclusive
        }

        matchRanges.forEach { range ->
            val start = range.first.coerceAtLeast(0)
            val endExclusive = (range.last + 1).coerceAtMost(text.length)
            if (start < endExclusive) {
                addStyle(
                    style = SpanStyle(
                        background = colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    ),
                    start = start,
                    end = endExclusive,
                )
            }
        }

        if (activeRange != null) {
            val start = activeRange.first.coerceAtLeast(0)
            val endExclusive = (activeRange.last + 1).coerceAtMost(text.length)
            if (start < endExclusive) {
                addStyle(
                    style = SpanStyle(
                        background = colorScheme.primary.copy(alpha = 0.32f),
                    ),
                    start = start,
                    end = endExclusive,
                )
            }
        }
    }
}

private fun tokenStyle(
    tokenType: JsonTokenType,
    colorScheme: ColorScheme,
): SpanStyle? = when (tokenType) {
    JsonTokenType.Punctuation -> SpanStyle(color = colorScheme.onSurfaceVariant)
    JsonTokenType.Key -> SpanStyle(
        color = colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )

    JsonTokenType.StringValue -> SpanStyle(color = colorScheme.secondary)
    JsonTokenType.NumberValue -> SpanStyle(color = colorScheme.tertiary)
    JsonTokenType.BooleanValue -> SpanStyle(color = colorScheme.primary)
    JsonTokenType.NullValue -> SpanStyle(
        color = colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )

    JsonTokenType.Summary -> null
}

private fun escapeJsonString(value: String): String {
    return buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(radix = 16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }
}
