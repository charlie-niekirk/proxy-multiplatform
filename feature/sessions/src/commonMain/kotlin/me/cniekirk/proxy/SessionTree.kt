package me.cniekirk.proxy

internal const val UnknownSessionHost = "__unknown_session_host__"

internal data class SessionTreeFilter(
    val host: String? = null,
    val pathPrefix: List<String> = emptyList(),
) {
    val isAll: Boolean
        get() = host == null
}

internal data class SessionHostTree(
    val key: String,
    val host: String,
    val count: Int,
    val children: List<SessionPathTree>,
)

internal data class SessionPathTree(
    val key: String,
    val segment: String,
    val count: Int,
    val pathPrefix: List<String>,
    val children: List<SessionPathTree>,
)

internal data class SessionHostPathTree(
    val hosts: List<SessionHostTree>,
    val nodeKeys: Set<String>,
)

private class MutableHostPathNode(
    val segment: String? = null,
) {
    var count: Int = 0
    val children: LinkedHashMap<String, MutableHostPathNode> = linkedMapOf()
}

internal fun buildSessionHostPathTree(sessions: List<CapturedSession>): SessionHostPathTree {
    val hostRoots = linkedMapOf<String, MutableHostPathNode>()
    sessions.forEach { session ->
        val host = extractRequestHost(session.request.url)
        val pathSegments = extractRequestPathSegments(session.request.url)

        val hostRoot = hostRoots.getOrPut(host) { MutableHostPathNode() }
        hostRoot.count += 1

        var currentNode = hostRoot
        pathSegments.forEach { segment ->
            val childNode = currentNode.children.getOrPut(segment) { MutableHostPathNode(segment = segment) }
            childNode.count += 1
            currentNode = childNode
        }
    }

    val hosts = hostRoots.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, MutableHostPathNode>> { entry -> entry.value.count }
                .thenBy { entry -> entry.key },
        )
        .map { entry ->
            val host = entry.key
            SessionHostTree(
                key = hostNodeKey(host),
                host = entry.key,
                count = entry.value.count,
                children = buildPathNodes(
                    host = host,
                    parentNode = entry.value,
                    currentPrefix = emptyList(),
                ),
            )
        }

    val nodeKeys = mutableSetOf<String>()
    hosts.forEach { host ->
        nodeKeys += host.key
        collectPathNodeKeys(host.children, nodeKeys)
    }

    return SessionHostPathTree(
        hosts = hosts,
        nodeKeys = nodeKeys,
    )
}

private fun buildPathNodes(
    host: String,
    parentNode: MutableHostPathNode,
    currentPrefix: List<String>,
): List<SessionPathTree> {
    return parentNode.children.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, MutableHostPathNode>> { entry -> entry.value.count }
                .thenBy { entry -> entry.key },
        )
        .map { entry ->
            val segment = entry.value.segment ?: entry.key
            val pathPrefix = currentPrefix + segment
            SessionPathTree(
                key = pathNodeKey(host, pathPrefix),
                segment = segment,
                count = entry.value.count,
                pathPrefix = pathPrefix,
                children = buildPathNodes(
                    host = host,
                    parentNode = entry.value,
                    currentPrefix = pathPrefix,
                ),
            )
        }
}

private fun collectPathNodeKeys(nodes: List<SessionPathTree>, collector: MutableSet<String>) {
    nodes.forEach { node ->
        collector += node.key
        collectPathNodeKeys(node.children, collector)
    }
}

internal fun SessionTreeFilter.matches(session: CapturedSession): Boolean {
    val filterHost = host ?: return true
    if (extractRequestHost(session.request.url) != filterHost) {
        return false
    }
    if (pathPrefix.isEmpty()) {
        return true
    }

    val sessionPathSegments = extractRequestPathSegments(session.request.url)
    if (sessionPathSegments.size < pathPrefix.size) {
        return false
    }

    return pathPrefix.indices.all { index ->
        sessionPathSegments[index] == pathPrefix[index]
    }
}

internal fun resolveSessionFlashNodeKey(
    session: CapturedSession,
    tree: SessionHostPathTree,
    expandedNodeKeys: Set<String>,
): String? {
    val host = extractRequestHost(session.request.url)
    val hostNode = tree.hosts.firstOrNull { node -> node.host == host } ?: return null
    var targetNodeKey = hostNode.key

    if (hostNode.key !in expandedNodeKeys) {
        return targetNodeKey
    }

    var children = hostNode.children
    val pathSegments = extractRequestPathSegments(session.request.url)
    for (segment in pathSegments) {
        val childNode = children.firstOrNull { node -> node.segment == segment } ?: break
        targetNodeKey = childNode.key
        if (childNode.key !in expandedNodeKeys) {
            return targetNodeKey
        }
        children = childNode.children
    }

    return targetNodeKey
}

private fun extractRequestPathSegments(url: String): List<String> {
    return extractRequestPath(url)
        .split('/')
        .map { segment -> segment.trim().lowercase() }
        .filter { segment -> segment.isNotEmpty() }
}

private fun extractRequestPath(url: String): String {
    val withoutScheme = url.substringAfter("://", url)
    val boundaryIndex = withoutScheme.indexOfAny(charArrayOf('/', '?', '#'))
    val remainder = if (boundaryIndex >= 0) {
        withoutScheme.substring(boundaryIndex)
    } else {
        ""
    }

    val suffixStartIndex = remainder.indexOfAny(charArrayOf('?', '#'))
    return when {
        remainder.isEmpty() -> "/"
        suffixStartIndex < 0 -> remainder
        suffixStartIndex == 0 -> "/"
        else -> remainder.substring(0, suffixStartIndex)
    }
}

private fun extractRequestHost(url: String): String {
    val authority = url
        .substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val hostAndPort = authority.substringAfter('@', authority)
    val host = when {
        hostAndPort.startsWith("[") -> hostAndPort.substringAfter('[').substringBefore(']')
        else -> hostAndPort.substringBefore(':')
    }.trim().lowercase()

    return host.ifEmpty { UnknownSessionHost }
}

private fun hostNodeKey(host: String): String {
    return "host:$host"
}

private fun pathNodeKey(host: String, pathPrefix: List<String>): String {
    return "path:$host/${pathPrefix.joinToString(separator = "/")}"
}
