package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@ContributesBinding(AppScope::class)
@Inject
class JvmProxyRuntimeService(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: InMemorySessionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val tlsService: JvmTlsService,
    private val certificateDistributionService: CertificateDistributionService,
) : ProxyRuntimeService {

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (acceptJob?.isActive == true) {
                logDebug("Start requested, but proxy is already running.")
                return
            }

            val settings = settingsRepository.settings.first().proxy
            logDebug("Starting proxy runtime with configured endpoint ${settings.host}:${settings.port}")
            if (settings.host == "127.0.0.1" || settings.host.equals("localhost", ignoreCase = true)) {
                val lanIpHint = detectLanIpHint(settings.port)
                logWarn(
                    "Proxy host is loopback (${settings.host}). " +
                        "LAN devices using $lanIpHint:${settings.port} will not connect.",
                )
            }
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(settings.host, settings.port))
            }
            logDebug("Proxy listening on ${socket.inetAddress.hostAddress}:${socket.localPort}")

            serverSocket = socket
            acceptJob = runtimeScope.launch {
                while (isActive) {
                    val clientSocket = try {
                        socket.accept()
                    } catch (_: SocketException) {
                        logDebug("Server socket closed; stopping accept loop.")
                        break
                    }

                    logDebug("Accepted client connection from ${clientSocket.remoteSocketAddress}")
                    launch {
                        runCatching {
                            handleClientSocket(clientSocket = clientSocket)
                        }.onFailure { error ->
                            logError(
                                "Unhandled error while processing client ${clientSocket.remoteSocketAddress}: ${error.message}",
                                error,
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        var jobToCancel: Job? = null
        lifecycleMutex.withLock {
            logDebug("Stopping proxy runtime.")
            runCatching { serverSocket?.close() }
            serverSocket = null
            jobToCancel = acceptJob
            acceptJob = null
        }

        jobToCancel?.cancelAndJoin()
    }

    private suspend fun handleClientSocket(clientSocket: Socket) {
        clientSocket.use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val request = try {
                parseRequest(input)
            } catch (error: Exception) {
                logWarn("Failed to parse request from ${socket.remoteSocketAddress}: ${error.message}")
                sendPlainTextResponse(
                    output = output,
                    statusCode = 400,
                    reasonPhrase = "Bad Request",
                    message = "Unable to parse proxy request: ${error.message}",
                )
                return
            }

            if (request == null) {
                logDebug("Client ${socket.remoteSocketAddress} closed without sending a request.")
                return
            }

            val proxySettings = settingsRepository.settings.first().proxy
            val maxBodyCaptureBytes = proxySettings.maxBodyCaptureBytes
            logDebug("Received ${request.method} ${request.target} from ${socket.remoteSocketAddress}")
            if (request.method.equals("CONNECT", ignoreCase = true)) {
                handleConnectRequest(
                    clientSocket = socket,
                    clientInput = input,
                    clientOutput = output,
                    request = request,
                    proxySettings = proxySettings,
                )
                return
            }

            val targetUrl = resolveTargetUrl(request.target, request.headers)
            if (targetUrl == null) {
                logWarn("Unable to resolve upstream URL from target=${request.target}")
                sendPlainTextResponse(
                    output = output,
                    statusCode = 400,
                    reasonPhrase = "Bad Request",
                    message = "Unable to resolve target URL from request.",
                )
                return
            }

            if (isInternalCertificateRequest(targetUrl, proxySettings)) {
                handleInternalCertificateRequest(
                    request = request,
                    targetUrl = targetUrl,
                    proxySettings = proxySettings,
                    clientOutput = output,
                    maxBodyCaptureBytes = maxBodyCaptureBytes,
                )
                return
            }

            val requestTimestamp = System.currentTimeMillis()
            val rules = ruleRepository.rules.first()
            val requestMatchContext = buildRuleMatchContext(targetUrl)
            val requestMutationResult = ruleEngine.applyRequestRules(
                rules = rules,
                request = RuleHttpRequest(
                    method = request.method,
                    matchContext = requestMatchContext,
                    headers = request.headers,
                    bodyBytes = request.bodyBytes,
                ),
            )
            val effectiveRequest = request.copy(
                headers = requestMutationResult.value.headers,
                bodyBytes = requestMutationResult.value.bodyBytes,
            )
            val requestPreview = buildBodyPreview(
                bodyBytes = effectiveRequest.bodyBytes,
                headers = effectiveRequest.headers,
                maxBodyCaptureBytes = maxBodyCaptureBytes,
            )
            logDebug("Forwarding ${request.method} ${targetUrl} upstream")

            val upstreamResponse = try {
                forwardRequest(targetUrl, effectiveRequest)
            } catch (error: Exception) {
                val finishedAt = System.currentTimeMillis()
                logError("Upstream request failed for ${request.method} ${targetUrl}: ${error.message}", error)
                sendPlainTextResponse(
                    output = output,
                    statusCode = 502,
                    reasonPhrase = "Bad Gateway",
                    message = "Proxy failed to reach upstream: ${error.message}",
                )
                sessionRepository.addSession(
                    CapturedSession(
                        id = UUID.randomUUID().toString(),
                        request = CapturedRequest(
                            method = effectiveRequest.method,
                            url = targetUrl.toString(),
                            headers = effectiveRequest.headers,
                            body = requestPreview,
                            bodySizeBytes = effectiveRequest.bodyBytes.size.toLong(),
                            timestampEpochMillis = requestTimestamp,
                        ),
                        response = null,
                        error = error.message ?: error::class.simpleName,
                        durationMillis = finishedAt - requestTimestamp,
                        appliedRules = buildAppliedRuleTraces(
                            requestTraces = requestMutationResult.traces,
                            responseTraces = emptyList(),
                        ),
                    ),
                )
                return
            }

            val responseMutationResult = ruleEngine.applyResponseRules(
                rules = rules,
                requestMatchContext = requestMatchContext,
                response = RuleHttpResponse(
                    statusCode = upstreamResponse.statusCode,
                    reasonPhrase = upstreamResponse.reasonPhrase,
                    headers = upstreamResponse.headers,
                    bodyBytes = upstreamResponse.bodyBytes,
                ),
            )
            val effectiveResponse = upstreamResponse.copy(
                headers = responseMutationResult.value.headers,
                bodyBytes = responseMutationResult.value.bodyBytes,
            )

            writeUpstreamResponse(output, effectiveResponse)

            val responseTimestamp = System.currentTimeMillis()
            val durationMillis = responseTimestamp - requestTimestamp
            val responseImageBytes = buildImagePreviewBytes(
                bodyBytes = effectiveResponse.bodyBytes,
                headers = effectiveResponse.headers,
                maxBodyCaptureBytes = maxBodyCaptureBytes,
            )
            sessionRepository.addSession(
                CapturedSession(
                    id = UUID.randomUUID().toString(),
                    request = CapturedRequest(
                        method = effectiveRequest.method,
                        url = targetUrl.toString(),
                        headers = effectiveRequest.headers,
                        body = requestPreview,
                        bodySizeBytes = effectiveRequest.bodyBytes.size.toLong(),
                        timestampEpochMillis = requestTimestamp,
                    ),
                    response = CapturedResponse(
                        statusCode = effectiveResponse.statusCode,
                        reasonPhrase = effectiveResponse.reasonPhrase,
                        headers = effectiveResponse.headers,
                        body = if (responseImageBytes != null) {
                            null
                        } else if (isImageContentType(effectiveResponse.headers)) {
                            "Image preview unavailable. Response exceeded capture limits or could not be decoded."
                        } else {
                            buildBodyPreview(
                                bodyBytes = effectiveResponse.bodyBytes,
                                headers = effectiveResponse.headers,
                                maxBodyCaptureBytes = maxBodyCaptureBytes,
                            )
                        },
                        imageBytes = responseImageBytes,
                        bodySizeBytes = effectiveResponse.bodyBytes.size.toLong(),
                        timestampEpochMillis = responseTimestamp,
                    ),
                    error = null,
                    durationMillis = durationMillis,
                    appliedRules = buildAppliedRuleTraces(
                        requestTraces = requestMutationResult.traces,
                        responseTraces = responseMutationResult.traces,
                    ),
                ),
            )
            logDebug(
                "Captured ${effectiveRequest.method} ${targetUrl} -> ${effectiveResponse.statusCode} " +
                    "in ${durationMillis}ms",
            )
        }
    }

    private suspend fun isInternalCertificateRequest(
        targetUrl: URL,
        proxySettings: ProxySettings,
    ): Boolean {
        val path = targetUrl.path
            .trimEnd('/')
            .ifEmpty { "/" }
        if (!path.equals(CertificateDistributionService.CERTIFICATE_PATH, ignoreCase = true)) {
            return false
        }

        val host = targetUrl.host.trim().lowercase(Locale.US)
        if (host == CertificateDistributionService.INTERNAL_HOST) {
            return true
        }

        val requestPort = if (targetUrl.port >= 0) targetUrl.port else targetUrl.defaultPort
        if (requestPort != proxySettings.port) {
            return false
        }

        if (host in LOCALHOST_HOSTS) {
            return true
        }

        val configuredHost = proxySettings.host.trim().lowercase(Locale.US)
        if (
            configuredHost.isNotBlank() &&
            configuredHost !in WILDCARD_PROXY_HOSTS &&
            host == configuredHost
        ) {
            return true
        }

        val fallbackHost = certificateDistributionService
            .getOnboardingUrls(proxyPort = proxySettings.port)
            .fallbackUrl
            ?.let { fallbackUrl ->
                parseUrlHost(fallbackUrl)?.lowercase(Locale.US)
            }

        return fallbackHost != null && host == fallbackHost
    }

    private suspend fun handleInternalCertificateRequest(
        request: ParsedRequest,
        targetUrl: URL,
        proxySettings: ProxySettings,
        clientOutput: OutputStream,
        maxBodyCaptureBytes: Long,
    ) {
        val requestTimestamp = System.currentTimeMillis()
        val requestPreview = buildBodyPreview(
            bodyBytes = request.bodyBytes,
            headers = request.headers,
            maxBodyCaptureBytes = maxBodyCaptureBytes,
        )

        val response = runCatching {
            buildCertificateRouteResponse(proxyPort = proxySettings.port)
        }.getOrElse { error ->
            val message = "Unable to prepare certificate response: ${error.message ?: "Unknown error"}"
            UpstreamResponse(
                statusCode = 500,
                reasonPhrase = "Internal Server Error",
                headers = listOf(
                    HeaderEntry(
                        name = CONTENT_TYPE_HEADER,
                        value = "text/plain; charset=utf-8",
                    ),
                ),
                bodyBytes = message.toByteArray(StandardCharsets.UTF_8),
            )
        }

        writeUpstreamResponse(clientOutput, response)

        val responseTimestamp = System.currentTimeMillis()
        val durationMillis = responseTimestamp - requestTimestamp
        val responseImageBytes = buildImagePreviewBytes(
            bodyBytes = response.bodyBytes,
            headers = response.headers,
            maxBodyCaptureBytes = maxBodyCaptureBytes,
        )
        sessionRepository.addSession(
            CapturedSession(
                id = UUID.randomUUID().toString(),
                request = CapturedRequest(
                    method = request.method,
                    url = targetUrl.toString(),
                    headers = request.headers,
                    body = requestPreview,
                    bodySizeBytes = request.bodyBytes.size.toLong(),
                    timestampEpochMillis = requestTimestamp,
                ),
                response = CapturedResponse(
                    statusCode = response.statusCode,
                    reasonPhrase = response.reasonPhrase,
                    headers = response.headers,
                    body = if (responseImageBytes != null) {
                        null
                    } else if (isImageContentType(response.headers)) {
                        "Image preview unavailable. Response exceeded capture limits or could not be decoded."
                    } else {
                        buildBodyPreview(
                            bodyBytes = response.bodyBytes,
                            headers = response.headers,
                            maxBodyCaptureBytes = maxBodyCaptureBytes,
                        )
                    },
                    imageBytes = responseImageBytes,
                    bodySizeBytes = response.bodyBytes.size.toLong(),
                    timestampEpochMillis = responseTimestamp,
                ),
                error = if (response.statusCode >= 500) {
                    response.bodyBytes.toString(StandardCharsets.UTF_8)
                } else {
                    null
                },
                durationMillis = durationMillis,
            ),
        )
        logDebug(
            "Served internal certificate route ${request.method} ${targetUrl} -> " +
                "${response.statusCode} in ${durationMillis}ms",
        )
    }

    private suspend fun buildCertificateRouteResponse(proxyPort: Int): UpstreamResponse {
        val certificatePayload = certificateDistributionService.loadCertificatePayload()
        if (certificatePayload != null) {
            return UpstreamResponse(
                statusCode = 200,
                reasonPhrase = "OK",
                headers = listOf(
                    HeaderEntry(
                        name = CONTENT_TYPE_HEADER,
                        value = certificatePayload.contentType,
                    ),
                    HeaderEntry(
                        name = CONTENT_DISPOSITION_HEADER,
                        value = "attachment; filename=\"${certificatePayload.fileName}\"",
                    ),
                    HeaderEntry(
                        name = CACHE_CONTROL_HEADER,
                        value = "no-store",
                    ),
                ),
                bodyBytes = certificatePayload.bytes,
            )
        }

        val onboardingUrls = certificateDistributionService.getOnboardingUrls(proxyPort = proxyPort)
        val bodyBytes = buildMissingCertificatePage(onboardingUrls).toByteArray(StandardCharsets.UTF_8)
        return UpstreamResponse(
            statusCode = 404,
            reasonPhrase = "Not Found",
            headers = listOf(
                HeaderEntry(
                    name = CONTENT_TYPE_HEADER,
                    value = "text/html; charset=utf-8",
                ),
                HeaderEntry(
                    name = CACHE_CONTROL_HEADER,
                    value = "no-store",
                ),
            ),
            bodyBytes = bodyBytes,
        )
    }

    private fun buildMissingCertificatePage(onboardingUrls: CertificateOnboardingUrls): String {
        val friendlyUrl = escapeHtml(onboardingUrls.friendlyUrl)
        val fallbackUrl = onboardingUrls.fallbackUrl
            ?.takeUnless { it == onboardingUrls.friendlyUrl }
            ?.let(::escapeHtml)

        val fallbackLink = fallbackUrl?.let { url ->
            "<li><a href=\"$url\">$url</a></li>"
        }.orEmpty()

        return """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <title>CMP Proxy Certificate</title>
              </head>
              <body>
                <h1>CMP Proxy Certificate</h1>
                <p>Root certificate material is not generated yet.</p>
                <p>Enable SSL decryption in the desktop app settings, then refresh this page.</p>
                <p>Certificate route URLs:</p>
                <ul>
                  <li><a href="$friendlyUrl">$friendlyUrl</a></li>
                  $fallbackLink
                </ul>
              </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private suspend fun handleConnectRequest(
        clientSocket: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        request: ParsedRequest,
        proxySettings: ProxySettings,
    ) {
        val requestTimestamp = System.currentTimeMillis()

        val connectTarget = parseConnectTarget(request.target)
        if (connectTarget == null) {
            val failureMessage = "Malformed CONNECT target: ${request.target}"
            logWarn(failureMessage)
            sendPlainTextResponse(
                output = clientOutput,
                statusCode = 400,
                reasonPhrase = "Bad Request",
                message = failureMessage,
            )
            val responseTimestamp = System.currentTimeMillis()
            sessionRepository.addSession(
                CapturedSession(
                    id = UUID.randomUUID().toString(),
                    request = CapturedRequest(
                        method = request.method,
                        url = request.target,
                        headers = request.headers,
                        body = null,
                        bodySizeBytes = request.bodyBytes.size.toLong(),
                        timestampEpochMillis = requestTimestamp,
                    ),
                    response = CapturedResponse(
                        statusCode = 400,
                        reasonPhrase = "Bad Request",
                        headers = emptyList(),
                        body = failureMessage,
                        bodySizeBytes = failureMessage.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                        timestampEpochMillis = responseTimestamp,
                    ),
                    error = failureMessage,
                    durationMillis = responseTimestamp - requestTimestamp,
                ),
            )
            return
        }

        val connectResult = if (proxySettings.sslDecryptionEnabled) {
            val mitmResult = runCatching {
                tlsService.ensureCertificateMaterial()
                establishMitmTunnel(
                    clientSocket = clientSocket,
                    clientOutput = clientOutput,
                    connectTarget = connectTarget,
                    maxBodyCaptureBytes = proxySettings.maxBodyCaptureBytes,
                )
            }.getOrElse { error ->
                ConnectTunnelResult(
                    mode = ConnectMode.MITM,
                    connectionEstablished = false,
                    clientToUpstreamBytes = 0,
                    upstreamToClientBytes = 0,
                    errorMessage = error.message ?: error::class.simpleName,
                    capturedHttpSessions = 0,
                )
            }

            if (mitmResult.connectionEstablished) {
                mitmResult
            } else {
                logWarn(
                    "MITM setup failed for ${connectTarget.authority}; " +
                        "falling back to tunnel mode: ${mitmResult.errorMessage}",
                )
                establishPassthroughTunnel(
                    clientSocket = clientSocket,
                    clientInput = clientInput,
                    clientOutput = clientOutput,
                    connectTarget = connectTarget,
                )
            }
        } else {
            establishPassthroughTunnel(
                clientSocket = clientSocket,
                clientInput = clientInput,
                clientOutput = clientOutput,
                connectTarget = connectTarget,
            )
        }

        if (!connectResult.connectionEstablished) {
            val failureReason = connectResult.errorMessage ?: "Unable to establish upstream tunnel."
            sendPlainTextResponse(
                output = clientOutput,
                statusCode = 502,
                reasonPhrase = "Bad Gateway",
                message = failureReason,
            )
        }

        val shouldRecordConnectSession =
            connectResult.mode != ConnectMode.MITM ||
                !connectResult.connectionEstablished ||
                connectResult.capturedHttpSessions == 0

        if (!shouldRecordConnectSession) {
            logDebug(
                "CONNECT ${connectTarget.authority} completed in mode=mitm " +
                    "capturedHttpSessions=${connectResult.capturedHttpSessions}",
            )
            return
        }

        val responseTimestamp = System.currentTimeMillis()
        val responseStatusCode = if (connectResult.connectionEstablished) 200 else 502
        val responseReasonPhrase = if (connectResult.connectionEstablished) {
            "Connection Established"
        } else {
            "Bad Gateway"
        }
        val modeLabel = connectResult.mode.label.lowercase(Locale.US)
        val responseBody = buildString {
            append("Mode: ")
            append(modeLabel)
            append('\n')
            append("Client->Upstream bytes: ")
            append(connectResult.clientToUpstreamBytes)
            append('\n')
            append("Upstream->Client bytes: ")
            append(connectResult.upstreamToClientBytes)
        }

        sessionRepository.addSession(
            CapturedSession(
                id = UUID.randomUUID().toString(),
                request = CapturedRequest(
                    method = request.method,
                    url = connectTarget.authority,
                    headers = request.headers,
                    body = null,
                    bodySizeBytes = request.bodyBytes.size.toLong(),
                    timestampEpochMillis = requestTimestamp,
                ),
                response = CapturedResponse(
                    statusCode = responseStatusCode,
                    reasonPhrase = responseReasonPhrase,
                    headers = listOf(
                        HeaderEntry(
                            name = CONNECT_MODE_HEADER,
                            value = modeLabel,
                        ),
                    ),
                    body = responseBody,
                    bodySizeBytes = responseBody.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                    timestampEpochMillis = responseTimestamp,
                ),
                error = connectResult.errorMessage,
                durationMillis = responseTimestamp - requestTimestamp,
            ),
        )
        logDebug(
            "CONNECT ${connectTarget.authority} completed in mode=${modeLabel} " +
                "established=${connectResult.connectionEstablished} " +
                "capturedHttpSessions=${connectResult.capturedHttpSessions} " +
                "bytes(${connectResult.clientToUpstreamBytes}/${connectResult.upstreamToClientBytes})",
        )
    }

    private suspend fun establishPassthroughTunnel(
        clientSocket: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        connectTarget: ConnectTarget,
    ): ConnectTunnelResult {
        return runCatching {
            Socket().use { upstreamSocket ->
                upstreamSocket.soTimeout = SOCKET_TIMEOUT_MILLIS
                upstreamSocket.connect(
                    InetSocketAddress(connectTarget.host, connectTarget.port),
                    CONNECT_TIMEOUT_MILLIS,
                )

                sendConnectionEstablished(clientOutput)

                val relayResult = relayBidirectional(
                    clientInput = clientInput,
                    clientOutput = clientOutput,
                    upstreamInput = upstreamSocket.getInputStream(),
                    upstreamOutput = upstreamSocket.getOutputStream(),
                    onClientInputComplete = {
                        runCatching { upstreamSocket.shutdownOutput() }
                    },
                    onUpstreamInputComplete = {
                        runCatching { clientSocket.shutdownOutput() }
                    },
                )
                ConnectTunnelResult(
                    mode = ConnectMode.TUNNEL,
                    connectionEstablished = true,
                    clientToUpstreamBytes = relayResult.clientToUpstreamBytes,
                    upstreamToClientBytes = relayResult.upstreamToClientBytes,
                    errorMessage = relayResult.errorMessage,
                    capturedHttpSessions = 0,
                )
            }
        }.getOrElse { error ->
            ConnectTunnelResult(
                mode = ConnectMode.TUNNEL,
                connectionEstablished = false,
                clientToUpstreamBytes = 0,
                upstreamToClientBytes = 0,
                errorMessage = error.message ?: error::class.simpleName,
                capturedHttpSessions = 0,
            )
        }
    }

    private suspend fun establishMitmTunnel(
        clientSocket: Socket,
        clientOutput: OutputStream,
        connectTarget: ConnectTarget,
        maxBodyCaptureBytes: Long,
    ): ConnectTunnelResult {
        var connectionEstablished = false

        return try {
            val mitmContext = tlsService.createMitmServerContext(connectTarget.host)

            sendConnectionEstablished(clientOutput)
            connectionEstablished = true

            val inboundSocketFactory = mitmContext.socketFactory as SSLSocketFactory
            (inboundSocketFactory.createSocket(
                clientSocket,
                connectTarget.host,
                connectTarget.port,
                false,
            ) as SSLSocket).use { clientTlsSocket ->
                clientTlsSocket.useClientMode = false
                clientTlsSocket.enabledProtocols = clientTlsSocket.enabledProtocols.preferredTlsProtocols()
                clientTlsSocket.startHandshake()

                val relayResult = handleMitmDecryptedHttpTraffic(
                    clientInput = clientTlsSocket.inputStream,
                    clientOutput = clientTlsSocket.outputStream,
                    connectTarget = connectTarget,
                    maxBodyCaptureBytes = maxBodyCaptureBytes,
                )

                ConnectTunnelResult(
                    mode = ConnectMode.MITM,
                    connectionEstablished = true,
                    clientToUpstreamBytes = relayResult.clientToUpstreamBytes,
                    upstreamToClientBytes = relayResult.upstreamToClientBytes,
                    errorMessage = relayResult.errorMessage,
                    capturedHttpSessions = relayResult.capturedHttpSessions,
                )
            }
        } catch (error: Exception) {
            ConnectTunnelResult(
                mode = ConnectMode.MITM,
                connectionEstablished = connectionEstablished,
                clientToUpstreamBytes = 0,
                upstreamToClientBytes = 0,
                errorMessage = error.message ?: error::class.simpleName,
                capturedHttpSessions = 0,
            )
        }
    }

    private suspend fun handleMitmDecryptedHttpTraffic(
        clientInput: InputStream,
        clientOutput: OutputStream,
        connectTarget: ConnectTarget,
        maxBodyCaptureBytes: Long,
    ): TunnelRelayResult {
        var clientToUpstreamBytes = 0L
        var upstreamToClientBytes = 0L
        var capturedHttpSessions = 0

        while (true) {
            val request = try {
                parseRequest(clientInput)
            } catch (error: Exception) {
                val errorMessage = if (error.isExpectedRelayTermination()) {
                    null
                } else {
                    error.message ?: error::class.simpleName
                }
                return TunnelRelayResult(
                    clientToUpstreamBytes = clientToUpstreamBytes,
                    upstreamToClientBytes = upstreamToClientBytes,
                    errorMessage = errorMessage,
                    capturedHttpSessions = capturedHttpSessions,
                )
            }

            if (request == null) {
                return TunnelRelayResult(
                    clientToUpstreamBytes = clientToUpstreamBytes,
                    upstreamToClientBytes = upstreamToClientBytes,
                    errorMessage = null,
                    capturedHttpSessions = capturedHttpSessions,
                )
            }

            val requestTimestamp = System.currentTimeMillis()
            val targetUrl = resolveTargetUrl(
                target = request.target,
                headers = request.headers,
                defaultScheme = HTTPS_SCHEME,
            )
            val requestUrl = targetUrl?.toString() ?: buildMitmFallbackUrl(connectTarget, request.target)

            if (targetUrl == null) {
                val failureMessage = "Unable to resolve target URL from decrypted request."
                val requestPreview = buildBodyPreview(
                    bodyBytes = request.bodyBytes,
                    headers = request.headers,
                    maxBodyCaptureBytes = maxBodyCaptureBytes,
                )
                sendPlainTextResponse(
                    output = clientOutput,
                    statusCode = 400,
                    reasonPhrase = "Bad Request",
                    message = failureMessage,
                )
                val responseTimestamp = System.currentTimeMillis()
                sessionRepository.addSession(
                    CapturedSession(
                        id = UUID.randomUUID().toString(),
                        request = CapturedRequest(
                            method = request.method,
                            url = requestUrl,
                            headers = request.headers,
                            body = requestPreview,
                            bodySizeBytes = request.bodyBytes.size.toLong(),
                            timestampEpochMillis = requestTimestamp,
                        ),
                        response = CapturedResponse(
                            statusCode = 400,
                            reasonPhrase = "Bad Request",
                            headers = emptyList(),
                            body = failureMessage,
                            bodySizeBytes = failureMessage.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                            timestampEpochMillis = responseTimestamp,
                        ),
                        error = failureMessage,
                        durationMillis = responseTimestamp - requestTimestamp,
                    ),
                )
                return TunnelRelayResult(
                    clientToUpstreamBytes = clientToUpstreamBytes,
                    upstreamToClientBytes = upstreamToClientBytes,
                    errorMessage = failureMessage,
                    capturedHttpSessions = capturedHttpSessions,
                )
            }

            val rules = ruleRepository.rules.first()
            val requestMatchContext = buildRuleMatchContext(targetUrl)
            val requestMutationResult = ruleEngine.applyRequestRules(
                rules = rules,
                request = RuleHttpRequest(
                    method = request.method,
                    matchContext = requestMatchContext,
                    headers = request.headers,
                    bodyBytes = request.bodyBytes,
                ),
            )
            val effectiveRequest = request.copy(
                headers = requestMutationResult.value.headers,
                bodyBytes = requestMutationResult.value.bodyBytes,
            )
            val requestPreview = buildBodyPreview(
                bodyBytes = effectiveRequest.bodyBytes,
                headers = effectiveRequest.headers,
                maxBodyCaptureBytes = maxBodyCaptureBytes,
            )
            clientToUpstreamBytes += effectiveRequest.bodyBytes.size.toLong()

            if (effectiveRequest.headers.isWebSocketUpgrade()) {
                val wsResult = handleWebSocketSession(
                    clientInput = clientInput,
                    clientOutput = clientOutput,
                    connectTarget = connectTarget,
                    request = effectiveRequest,
                    targetUrl = targetUrl,
                    requestTimestamp = requestTimestamp,
                    requestPreview = requestPreview,
                    requestTraces = requestMutationResult.traces,
                    maxBodyCaptureBytes = maxBodyCaptureBytes,
                )
                clientToUpstreamBytes += wsResult.clientToUpstreamBytes
                upstreamToClientBytes += wsResult.upstreamToClientBytes
                capturedHttpSessions += 1
                return TunnelRelayResult(
                    clientToUpstreamBytes = clientToUpstreamBytes,
                    upstreamToClientBytes = upstreamToClientBytes,
                    errorMessage = wsResult.errorMessage,
                    capturedHttpSessions = capturedHttpSessions,
                )
            }

            val upstreamResponse = try {
                forwardRequest(targetUrl, effectiveRequest)
            } catch (error: Exception) {
                val failureMessage = error.message ?: error::class.simpleName.orEmpty()
                sendPlainTextResponse(
                    output = clientOutput,
                    statusCode = 502,
                    reasonPhrase = "Bad Gateway",
                    message = "Proxy failed to reach upstream: $failureMessage",
                )
                val responseTimestamp = System.currentTimeMillis()
                sessionRepository.addSession(
                    CapturedSession(
                        id = UUID.randomUUID().toString(),
                        request = CapturedRequest(
                            method = effectiveRequest.method,
                            url = requestUrl,
                            headers = effectiveRequest.headers,
                            body = requestPreview,
                            bodySizeBytes = effectiveRequest.bodyBytes.size.toLong(),
                            timestampEpochMillis = requestTimestamp,
                        ),
                        response = null,
                        error = failureMessage,
                        durationMillis = responseTimestamp - requestTimestamp,
                        appliedRules = buildAppliedRuleTraces(
                            requestTraces = requestMutationResult.traces,
                            responseTraces = emptyList(),
                        ),
                    ),
                )
                return TunnelRelayResult(
                    clientToUpstreamBytes = clientToUpstreamBytes,
                    upstreamToClientBytes = upstreamToClientBytes,
                    errorMessage = failureMessage,
                    capturedHttpSessions = capturedHttpSessions,
                )
            }

            val responseMutationResult = ruleEngine.applyResponseRules(
                rules = rules,
                requestMatchContext = requestMatchContext,
                response = RuleHttpResponse(
                    statusCode = upstreamResponse.statusCode,
                    reasonPhrase = upstreamResponse.reasonPhrase,
                    headers = upstreamResponse.headers,
                    bodyBytes = upstreamResponse.bodyBytes,
                ),
            )
            val effectiveResponse = upstreamResponse.copy(
                headers = responseMutationResult.value.headers,
                bodyBytes = responseMutationResult.value.bodyBytes,
            )

            writeUpstreamResponse(clientOutput, effectiveResponse)
            upstreamToClientBytes += effectiveResponse.bodyBytes.size.toLong()

            val responseTimestamp = System.currentTimeMillis()
            val responseImageBytes = buildImagePreviewBytes(
                bodyBytes = effectiveResponse.bodyBytes,
                headers = effectiveResponse.headers,
                maxBodyCaptureBytes = maxBodyCaptureBytes,
            )
            sessionRepository.addSession(
                CapturedSession(
                    id = UUID.randomUUID().toString(),
                    request = CapturedRequest(
                        method = effectiveRequest.method,
                        url = targetUrl.toString(),
                        headers = effectiveRequest.headers,
                        body = requestPreview,
                        bodySizeBytes = effectiveRequest.bodyBytes.size.toLong(),
                        timestampEpochMillis = requestTimestamp,
                    ),
                    response = CapturedResponse(
                        statusCode = effectiveResponse.statusCode,
                        reasonPhrase = effectiveResponse.reasonPhrase,
                        headers = effectiveResponse.headers,
                        body = if (responseImageBytes != null) {
                            null
                        } else if (isImageContentType(effectiveResponse.headers)) {
                            "Image preview unavailable. Response exceeded capture limits or could not be decoded."
                        } else {
                            buildBodyPreview(
                                bodyBytes = effectiveResponse.bodyBytes,
                                headers = effectiveResponse.headers,
                                maxBodyCaptureBytes = maxBodyCaptureBytes,
                            )
                        },
                        imageBytes = responseImageBytes,
                        bodySizeBytes = effectiveResponse.bodyBytes.size.toLong(),
                        timestampEpochMillis = responseTimestamp,
                    ),
                    error = null,
                    durationMillis = responseTimestamp - requestTimestamp,
                    appliedRules = buildAppliedRuleTraces(
                        requestTraces = requestMutationResult.traces,
                        responseTraces = responseMutationResult.traces,
                    ),
                ),
            )
            capturedHttpSessions += 1
            logDebug(
                "Captured MITM ${effectiveRequest.method} ${targetUrl} -> ${effectiveResponse.statusCode} " +
                    "in ${responseTimestamp - requestTimestamp}ms",
            )
        }
    }

    private fun buildMitmFallbackUrl(connectTarget: ConnectTarget, requestTarget: String): String {
        if (
            requestTarget.startsWith("http://", ignoreCase = true) ||
            requestTarget.startsWith("https://", ignoreCase = true)
        ) {
            return requestTarget
        }

        val normalizedPath = if (requestTarget.startsWith('/')) requestTarget else "/$requestTarget"
        return "$HTTPS_SCHEME://${connectTarget.authority}$normalizedPath"
    }

    private fun sendConnectionEstablished(output: OutputStream) {
        output.write("HTTP/1.1 200 Connection Established\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Proxy-Agent: cmp-proxy\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    private suspend fun relayBidirectional(
        clientInput: InputStream,
        clientOutput: OutputStream,
        upstreamInput: InputStream,
        upstreamOutput: OutputStream,
        onClientInputComplete: () -> Unit,
        onUpstreamInputComplete: () -> Unit,
    ): TunnelRelayResult = coroutineScope {
        val clientToUpstreamJob = async(Dispatchers.IO) {
            relaySingleDirection(
                source = clientInput,
                sink = upstreamOutput,
                onComplete = onClientInputComplete,
            )
        }
        val upstreamToClientJob = async(Dispatchers.IO) {
            relaySingleDirection(
                source = upstreamInput,
                sink = clientOutput,
                onComplete = onUpstreamInputComplete,
            )
        }

        val clientToUpstream = clientToUpstreamJob.await()
        val upstreamToClient = upstreamToClientJob.await()

        TunnelRelayResult(
            clientToUpstreamBytes = clientToUpstream.relayedBytes,
            upstreamToClientBytes = upstreamToClient.relayedBytes,
            errorMessage = clientToUpstream.errorMessage ?: upstreamToClient.errorMessage,
            capturedHttpSessions = 0,
        )
    }

    private fun relaySingleDirection(
        source: InputStream,
        sink: OutputStream,
        onComplete: () -> Unit,
    ): DirectionRelayResult {
        val buffer = ByteArray(TUNNEL_BUFFER_BYTES)
        var relayedBytes = 0L
        var relayError: String? = null

        try {
            while (true) {
                val readCount = source.read(buffer)
                if (readCount == -1) {
                    break
                }
                sink.write(buffer, 0, readCount)
                relayedBytes += readCount.toLong()
            }
        } catch (error: Exception) {
            if (!error.isExpectedRelayTermination()) {
                relayError = error.message ?: error::class.simpleName
            }
        } finally {
            runCatching { sink.flush() }
            onComplete()
        }

        return DirectionRelayResult(
            relayedBytes = relayedBytes,
            errorMessage = relayError,
        )
    }

    private fun parseConnectTarget(rawTarget: String): ConnectTarget? {
        val target = rawTarget.trim()
        if (target.isEmpty()) {
            return null
        }

        if (target.startsWith("[")) {
            val closingBracketIndex = target.indexOf(']')
            if (closingBracketIndex <= 1) {
                return null
            }

            val host = target.substring(1, closingBracketIndex)
            val remainder = target.substring(closingBracketIndex + 1)
            val port = when {
                remainder.isEmpty() -> DEFAULT_HTTPS_PORT
                remainder.startsWith(":") -> remainder.drop(1).toIntOrNull()
                else -> null
            } ?: return null

            if (!port.isValidPort()) {
                return null
            }

            return ConnectTarget(
                host = host,
                port = port,
                authority = "[$host]:$port",
            )
        }

        if (target.count { it == ':' } > 1) {
            return null
        }

        val separatorIndex = target.lastIndexOf(':')
        val host = if (separatorIndex > 0) {
            target.substring(0, separatorIndex).trim()
        } else {
            target
        }
        val port = if (separatorIndex > 0) {
            target.substring(separatorIndex + 1).toIntOrNull()
        } else {
            DEFAULT_HTTPS_PORT
        } ?: return null

        if (host.isBlank() || !port.isValidPort()) {
            return null
        }

        return ConnectTarget(
            host = host,
            port = port,
            authority = "$host:$port",
        )
    }

    private fun parseRequest(input: InputStream): ParsedRequest? {
        val headerBytes = readHeaderBytes(input) ?: return null
        val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1)
        val lines = headerText.split(HEADER_LINE_DELIMITER)
        val requestLine = lines.firstOrNull().orEmpty().trim()
        if (requestLine.isBlank()) {
            return null
        }

        val requestParts = requestLine.split(' ', limit = 3)
        require(requestParts.size == 3) { "Malformed request line: $requestLine" }

        val headers = lines
            .drop(1)
            .takeWhile { it.isNotEmpty() }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(':')
                if (separatorIndex <= 0) {
                    null
                } else {
                    HeaderEntry(
                        name = line.substring(0, separatorIndex).trim(),
                        value = line.substring(separatorIndex + 1).trim(),
                    )
                }
            }

        val contentLength = headers.headerValue(CONTENT_LENGTH_HEADER)?.toLongOrNull()
        val isChunked = headers
            .headerValue(TRANSFER_ENCODING_HEADER)
            ?.contains("chunked", ignoreCase = true)
            ?: false

        val bodyBytes = when {
            isChunked -> readChunkedBody(input)
            contentLength != null && contentLength > 0 -> {
                require(contentLength <= Int.MAX_VALUE) { "Request body too large." }
                readFixedLengthBody(input, contentLength.toInt())
            }

            else -> ByteArray(0)
        }

        return ParsedRequest(
            method = requestParts[0],
            target = requestParts[1],
            headers = headers,
            bodyBytes = bodyBytes,
        )
    }

    private fun readHeaderBytes(input: InputStream): ByteArray? {
        val buffer = ByteArray(MAX_HEADER_BYTES)
        var size = 0
        var foundTerminator = false

        while (size < MAX_HEADER_BYTES) {
            val currentByte = input.read()
            if (currentByte == -1) {
                break
            }

            buffer[size] = currentByte.toByte()
            size += 1

            if (
                size >= TERMINATOR_BYTES &&
                buffer[size - 4] == CARRIAGE_RETURN &&
                buffer[size - 3] == LINE_FEED &&
                buffer[size - 2] == CARRIAGE_RETURN &&
                buffer[size - 1] == LINE_FEED
            ) {
                foundTerminator = true
                break
            }
        }

        if (size == 0) {
            return null
        }

        require(foundTerminator) { "Request headers exceeded max size." }
        return buffer.copyOf(size)
    }

    private fun readFixedLengthBody(input: InputStream, contentLength: Int): ByteArray {
        val body = ByteArray(contentLength)
        var offset = 0

        while (offset < contentLength) {
            val readCount = input.read(body, offset, contentLength - offset)
            if (readCount == -1) {
                throw EOFException("Unexpected end of stream while reading request body.")
            }
            offset += readCount
        }

        return body
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val bodyBytes = ByteArrayOutputStream()

        while (true) {
            val chunkHeader = readAsciiLine(input)
            val chunkSize = chunkHeader.substringBefore(';').trim().toInt(16)

            if (chunkSize == 0) {
                while (readAsciiLine(input).isNotEmpty()) {
                    // Consume trailer headers.
                }
                break
            }

            val chunk = readFixedLengthBody(input, chunkSize)
            bodyBytes.write(chunk)

            val cr = input.read()
            val lf = input.read()
            if (cr != CARRIAGE_RETURN.toInt() || lf != LINE_FEED.toInt()) {
                throw IllegalStateException("Malformed chunked request body.")
            }
        }

        return bodyBytes.toByteArray()
    }

    private fun readAsciiLine(input: InputStream): String {
        val line = ByteArrayOutputStream()

        while (true) {
            val nextByte = input.read()
            if (nextByte == -1) {
                throw EOFException("Unexpected end of stream while reading line.")
            }

            if (nextByte == CARRIAGE_RETURN.toInt()) {
                val lineFeed = input.read()
                if (lineFeed != LINE_FEED.toInt()) {
                    throw IllegalStateException("Malformed CRLF line ending.")
                }
                return line.toString(StandardCharsets.ISO_8859_1)
            }

            line.write(nextByte)
        }
    }

    private fun resolveTargetUrl(
        target: String,
        headers: List<HeaderEntry>,
        defaultScheme: String = HTTP_SCHEME,
    ): URL? {
        return runCatching {
            if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
                URI(target).toURL()
            } else {
                val hostHeader = headers.headerValue(HOST_HEADER)
                    ?: throw IllegalArgumentException("Missing Host header")
                val normalizedPath = if (target.startsWith('/')) target else "/$target"
                URI("$defaultScheme://$hostHeader$normalizedPath").toURL()
            }
        }.getOrNull()
    }

    private fun parseUrlHost(url: String): String? {
        return runCatching {
            URI(url).host
        }.getOrNull()
    }

    private fun buildRuleMatchContext(targetUrl: URL): RuleMatchContext {
        val scheme = targetUrl.protocol
            .takeIf { protocol -> protocol.isNotBlank() }
            ?: HTTP_SCHEME
        val port = when {
            targetUrl.port >= 0 -> targetUrl.port
            targetUrl.defaultPort >= 0 -> targetUrl.defaultPort
            scheme.equals(HTTPS_SCHEME, ignoreCase = true) -> DEFAULT_HTTPS_PORT
            else -> DEFAULT_HTTP_PORT
        }
        return RuleMatchContext(
            scheme = scheme,
            host = targetUrl.host.orEmpty(),
            path = targetUrl.path.ifEmpty { "/" },
            port = port,
        )
    }

    private fun buildAppliedRuleTraces(
        requestTraces: List<RuleExecutionTrace>,
        responseTraces: List<RuleExecutionTrace>,
    ): List<AppliedRuleTrace> {
        val mergedTraces = linkedMapOf<String, MutableAppliedRuleTrace>()
        (requestTraces + responseTraces).forEach { trace ->
            val current = mergedTraces.getOrPut(trace.ruleId) {
                MutableAppliedRuleTrace(
                    ruleId = trace.ruleId,
                    ruleName = trace.ruleName,
                )
            }
            when (trace.target) {
                RuleTarget.REQUEST -> current.appliedToRequest = true
                RuleTarget.RESPONSE -> current.appliedToResponse = true
            }
            current.mutations += trace.mutations
        }
        return mergedTraces.values.map { trace ->
            AppliedRuleTrace(
                ruleId = trace.ruleId,
                ruleName = trace.ruleName,
                appliedToRequest = trace.appliedToRequest,
                appliedToResponse = trace.appliedToResponse,
                mutations = trace.mutations.toList(),
            )
        }
    }

    private fun forwardRequest(targetUrl: URL, request: ParsedRequest): UpstreamResponse {
        val connection = (targetUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            useCaches = false
            instanceFollowRedirects = false
            doInput = true
        }

        request.headers.forEach { header ->
            if (
                !header.name.isHopByHopHeader() &&
                !header.name.equals(HOST_HEADER, ignoreCase = true) &&
                !header.name.equals(CONTENT_LENGTH_HEADER, ignoreCase = true)
            ) {
                connection.addRequestProperty(header.name, header.value)
            }
        }

        connection.setRequestProperty(CONNECTION_HEADER, "close")

        if (request.bodyBytes.isNotEmpty() && request.method.canHaveBody()) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(request.bodyBytes.size)
            connection.outputStream.use { output ->
                output.write(request.bodyBytes)
            }
        }

        val statusCode = connection.responseCode
        val reasonPhrase = connection.responseMessage
        val responseHeaders = connection.headerFields
            .entries
            .filter { it.key != null }
            .flatMap { entry ->
                val name = entry.key.orEmpty()
                entry.value.orEmpty().map { value ->
                    HeaderEntry(name = name, value = value)
                }
            }

        val responseBytes = (
            connection.errorStream
                ?: runCatching { connection.inputStream }.getOrNull()
        )?.use { stream ->
            stream.readBytes()
        } ?: ByteArray(0)

        return UpstreamResponse(
            statusCode = statusCode,
            reasonPhrase = reasonPhrase,
            headers = responseHeaders,
            bodyBytes = responseBytes,
        )
    }

    private fun writeUpstreamResponse(output: OutputStream, response: UpstreamResponse) {
        val reasonPhrase = response.reasonPhrase
            ?.takeIf { it.isNotBlank() }
            ?: "OK"

        output.write(
            "HTTP/1.1 ${response.statusCode} $reasonPhrase\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )

        response.headers
            .filterNot { it.name.isHopByHopHeader() }
            .filterNot { it.name.equals(CONTENT_LENGTH_HEADER, ignoreCase = true) }
            .filterNot { it.name.equals(CONNECTION_HEADER, ignoreCase = true) }
            .forEach { header ->
                output.write("${header.name}: ${header.value}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }

        output.write("$CONTENT_LENGTH_HEADER: ${response.bodyBytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("$CONNECTION_HEADER: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write(response.bodyBytes)
        output.flush()
    }

    private fun sendPlainTextResponse(
        output: OutputStream,
        statusCode: Int,
        reasonPhrase: String,
        message: String,
    ) {
        val body = message.toByteArray(StandardCharsets.UTF_8)

        output.write("HTTP/1.1 $statusCode $reasonPhrase\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("$CONTENT_LENGTH_HEADER: ${body.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("$CONNECTION_HEADER: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun buildBodyPreview(
        bodyBytes: ByteArray,
        headers: List<HeaderEntry>,
        maxBodyCaptureBytes: Long,
    ): String? {
        if (bodyBytes.isEmpty() || maxBodyCaptureBytes <= 0) {
            return null
        }

        val decodedBodyResult = decodeBodyForPreview(
            bodyBytes = bodyBytes,
            headers = headers,
        )
        val bytesToRender = decodedBodyResult.decodedBytes ?: bodyBytes

        val maxCapture = maxBodyCaptureBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val previewSize = bytesToRender.size.coerceAtMost(maxCapture)
        val charset = resolveBodyCharset(headers)
        val preview = bytesToRender.copyOf(previewSize).toString(charset)
        val suffix = buildList {
            decodedBodyResult.message?.let(::add)
            if (previewSize < bytesToRender.size) {
                add("…truncated ${bytesToRender.size - previewSize} bytes")
            }
        }

        return if (suffix.isEmpty()) {
            preview
        } else if (preview.isBlank()) {
            suffix.joinToString(separator = "\n")
        } else {
            "$preview\n\n${suffix.joinToString(separator = "\n")}"
        }
    }

    private fun buildImagePreviewBytes(
        bodyBytes: ByteArray,
        headers: List<HeaderEntry>,
        maxBodyCaptureBytes: Long,
    ): ByteArray? {
        if (!isImageContentType(headers) || bodyBytes.isEmpty() || maxBodyCaptureBytes <= 0) {
            return null
        }

        val maxCapture = maxBodyCaptureBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val decodedBodyResult = decodeBodyForPreview(
            bodyBytes = bodyBytes,
            headers = headers,
        )
        val decodedBytes = decodedBodyResult.decodedBytes ?: return null
        return decodedBytes.takeIf { bytes -> bytes.size <= maxCapture }
    }

    private fun decodeBodyForPreview(
        bodyBytes: ByteArray,
        headers: List<HeaderEntry>,
    ): DecodedBodyResult {
        val contentEncoding = headers.headerValue(CONTENT_ENCODING_HEADER)
            ?.split(',')
            ?.map { token -> token.trim().lowercase(Locale.US) }
            ?.filter { token -> token.isNotEmpty() && token != IDENTITY_ENCODING }
            ?: emptyList()

        if (contentEncoding.isEmpty()) {
            return DecodedBodyResult(
                decodedBytes = bodyBytes,
                message = null,
            )
        }

        var decoded = bodyBytes
        for (encoding in contentEncoding.asReversed()) {
            decoded = when (encoding) {
                GZIP_ENCODING,
                X_GZIP_ENCODING,
                -> runCatching {
                    GZIPInputStream(ByteArrayInputStream(decoded)).use { stream ->
                        stream.readBytes()
                    }
                }.getOrNull()

                DEFLATE_ENCODING -> runCatching {
                    InflaterInputStream(ByteArrayInputStream(decoded)).use { stream ->
                        stream.readBytes()
                    }
                }.getOrNull()

                else -> {
                    return DecodedBodyResult(
                        decodedBytes = null,
                        message = "Preview not decoded: unsupported Content-Encoding \"$encoding\".",
                    )
                }
            } ?: return DecodedBodyResult(
                decodedBytes = null,
                message = "Preview not decoded: failed to decompress Content-Encoding \"$encoding\".",
            )
        }

        return DecodedBodyResult(
            decodedBytes = decoded,
            message = "Decoded Content-Encoding: ${contentEncoding.joinToString(", ")}",
        )
    }

    private fun resolveBodyCharset(headers: List<HeaderEntry>): Charset {
        val contentType = headers.headerValue(CONTENT_TYPE_HEADER).orEmpty()
        val charsetName = contentType
            .split(';')
            .map { token -> token.trim() }
            .firstOrNull { token -> token.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { token -> token.isNotBlank() }

        return if (charsetName == null) {
            StandardCharsets.UTF_8
        } else {
            runCatching { Charset.forName(charsetName) }.getOrDefault(StandardCharsets.UTF_8)
        }
    }

    private fun List<HeaderEntry>.headerValue(name: String): String? {
        return firstOrNull { header ->
            header.name.equals(name, ignoreCase = true)
        }?.value
    }

    private fun String.isHopByHopHeader(): Boolean {
        return lowercase(Locale.US) in HOP_BY_HOP_HEADERS
    }

    private fun String.canHaveBody(): Boolean {
        return !equals("GET", ignoreCase = true) &&
            !equals("HEAD", ignoreCase = true) &&
            !equals("TRACE", ignoreCase = true)
    }

    private fun Throwable.isExpectedRelayTermination(): Boolean {
        return this is EOFException ||
            this is SocketException ||
            this is SSLException
    }

    private fun Array<String>.preferredTlsProtocols(): Array<String> {
        val supported = toSet()
        val preferred = listOf("TLSv1.3", "TLSv1.2").filter { candidate ->
            candidate in supported
        }
        return if (preferred.isNotEmpty()) preferred.toTypedArray() else this
    }

    private fun Int.isValidPort(): Boolean {
        return this in 1..65535
    }

    private suspend fun detectLanIpHint(proxyPort: Int): String {
        return certificateDistributionService
            .getOnboardingUrls(proxyPort = proxyPort)
            .fallbackUrl
            ?.let { fallbackUrl ->
                parseUrlHost(fallbackUrl)
            }
            ?: "LAN-IP"
    }

    private fun logDebug(message: String) {
        println("$LOG_PREFIX $message")
    }

    private fun logWarn(message: String) {
        println("$LOG_PREFIX WARN: $message")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        System.err.println("$LOG_PREFIX ERROR: $message")
        throwable?.printStackTrace()
    }

    private data class ParsedRequest(
        val method: String,
        val target: String,
        val headers: List<HeaderEntry>,
        val bodyBytes: ByteArray,
    )

    private data class UpstreamResponse(
        val statusCode: Int,
        val reasonPhrase: String?,
        val headers: List<HeaderEntry>,
        val bodyBytes: ByteArray,
    )

    private data class ConnectTarget(
        val host: String,
        val port: Int,
        val authority: String,
    )

    private data class ConnectTunnelResult(
        val mode: ConnectMode,
        val connectionEstablished: Boolean,
        val clientToUpstreamBytes: Long,
        val upstreamToClientBytes: Long,
        val errorMessage: String?,
        val capturedHttpSessions: Int,
    )

    private data class TunnelRelayResult(
        val clientToUpstreamBytes: Long,
        val upstreamToClientBytes: Long,
        val errorMessage: String?,
        val capturedHttpSessions: Int,
    )

    private data class DecodedBodyResult(
        val decodedBytes: ByteArray?,
        val message: String?,
    )

    private data class DirectionRelayResult(
        val relayedBytes: Long,
        val errorMessage: String?,
    )

    private data class MutableAppliedRuleTrace(
        val ruleId: String,
        val ruleName: String,
        var appliedToRequest: Boolean = false,
        var appliedToResponse: Boolean = false,
        val mutations: MutableList<String> = mutableListOf(),
    )

    private data class WebSocketRelayResult(
        val clientToUpstreamBytes: Long,
        val upstreamToClientBytes: Long,
        val errorMessage: String?,
    )

    private data class WebSocketFrameCapture(
        val opcode: Int,
        val isFin: Boolean,
        val capturedPayload: ByteArray,
        val totalPayloadSize: Long,
    )

    private enum class ConnectMode(val label: String) {
        TUNNEL("TUNNEL"),
        MITM("MITM"),
    }

    // -------------------------------------------------------------------------
    // WebSocket helpers
    // -------------------------------------------------------------------------

    private fun List<HeaderEntry>.isWebSocketUpgrade(): Boolean {
        val upgrade = headerValue("Upgrade")
        val connection = headerValue("Connection")
        return upgrade?.equals("websocket", ignoreCase = true) == true &&
            connection?.contains("Upgrade", ignoreCase = true) == true
    }

    private fun parseRawResponseHeader(
        responseBytes: ByteArray,
    ): Triple<Int, String, List<HeaderEntry>>? {
        val text = responseBytes.toString(StandardCharsets.ISO_8859_1)
        val lines = text.split(HEADER_LINE_DELIMITER)
        val statusLine = lines.firstOrNull()?.trim().orEmpty()
        val statusParts = statusLine.split(' ', limit = 3)
        if (statusParts.size < 2) return null
        val statusCode = statusParts[1].toIntOrNull() ?: return null
        val reasonPhrase = statusParts.getOrElse(2) { "" }.trim()
        val headers = lines
            .drop(1)
            .takeWhile { it.isNotEmpty() }
            .mapNotNull { line ->
                val sep = line.indexOf(':')
                if (sep <= 0) null
                else HeaderEntry(
                    name = line.substring(0, sep).trim(),
                    value = line.substring(sep + 1).trim(),
                )
            }
        return Triple(statusCode, reasonPhrase, headers)
    }

    private fun writeRawResponseHeader(
        output: OutputStream,
        statusCode: Int,
        reasonPhrase: String,
        headers: List<HeaderEntry>,
    ) {
        output.write("HTTP/1.1 $statusCode $reasonPhrase\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        headers.forEach { header ->
            output.write("${header.name}: ${header.value}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        }
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    private fun writeRawRequest(request: ParsedRequest, output: OutputStream) {
        output.write(
            "${request.method} ${request.target} HTTP/1.1\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        request.headers.forEach { header ->
            output.write("${header.name}: ${header.value}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        }
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        if (request.bodyBytes.isNotEmpty()) {
            output.write(request.bodyBytes)
        }
        output.flush()
    }

    /**
     * Reads one WebSocket frame from [source], writes it verbatim to [sink],
     * and returns a capture of up to [maxCaptureBytes] of the (unmasked) payload.
     * Returns null when the source stream is cleanly closed (EOF on first byte).
     */
    private fun readAndForwardWebSocketFrame(
        source: InputStream,
        sink: OutputStream,
        maxCaptureBytes: Int,
    ): WebSocketFrameCapture? {
        val b0 = source.read()
        if (b0 == -1) return null
        val b1 = source.read()
        if (b1 == -1) return null

        val fin = (b0 and 0x80) != 0
        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0

        val headerOut = ByteArrayOutputStream(14)
        headerOut.write(b0)
        headerOut.write(b1)

        var payloadLen = (b1 and 0x7F).toLong()
        when (payloadLen.toInt()) {
            126 -> {
                val ext = readFixedLengthBody(source, 2)
                headerOut.write(ext)
                payloadLen = ((ext[0].toInt() and 0xFF) shl 8 or (ext[1].toInt() and 0xFF)).toLong()
            }
            127 -> {
                val ext = readFixedLengthBody(source, 8)
                headerOut.write(ext)
                payloadLen = 0L
                for (b in ext) {
                    payloadLen = (payloadLen shl 8) or (b.toLong() and 0xFF)
                }
            }
        }

        val maskKey: ByteArray? = if (masked) {
            val key = readFixedLengthBody(source, 4)
            headerOut.write(key)
            key
        } else {
            null
        }

        sink.write(headerOut.toByteArray())

        val captureOut = ByteArrayOutputStream(minOf(payloadLen, maxCaptureBytes.toLong()).toInt())
        val buffer = ByteArray(TUNNEL_BUFFER_BYTES)
        var remaining = payloadLen
        var maskOffset = 0L
        var capturedSoFar = 0L

        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = source.read(buffer, 0, toRead)
            if (read == -1) throw EOFException("Unexpected end of WebSocket frame payload.")
            sink.write(buffer, 0, read)

            if (capturedSoFar < maxCaptureBytes) {
                val toCopy = minOf(read.toLong(), maxCaptureBytes - capturedSoFar).toInt()
                if (maskKey != null) {
                    for (i in 0 until toCopy) {
                        val bytePos = (maskOffset + i).toInt()
                        captureOut.write(
                            (buffer[i].toInt() and 0xFF) xor (maskKey[bytePos % 4].toInt() and 0xFF),
                        )
                    }
                } else {
                    captureOut.write(buffer, 0, toCopy)
                }
                capturedSoFar += toCopy
            }

            maskOffset += read
            remaining -= read
        }

        sink.flush()

        return WebSocketFrameCapture(
            opcode = opcode,
            isFin = fin,
            capturedPayload = captureOut.toByteArray(),
            totalPayloadSize = payloadLen,
        )
    }

    private fun mapWebSocketOpcode(opcode: Int): WebSocketOpcode? = when (opcode) {
        0 -> WebSocketOpcode.Continuation
        1 -> WebSocketOpcode.Text
        2 -> WebSocketOpcode.Binary
        8 -> WebSocketOpcode.Close
        9 -> WebSocketOpcode.Ping
        10 -> WebSocketOpcode.Pong
        else -> null
    }

    private fun decodeWebSocketPayload(payload: ByteArray, opcode: WebSocketOpcode): String {
        return when (opcode) {
            WebSocketOpcode.Text,
            WebSocketOpcode.Continuation,
            WebSocketOpcode.Close,
            WebSocketOpcode.Ping,
            WebSocketOpcode.Pong,
            -> payload.toString(StandardCharsets.UTF_8)

            WebSocketOpcode.Binary -> {
                val preview = payload.take(256).joinToString(" ") { b -> "%02x".format(b) }
                if (payload.size > 256) "$preview ..." else preview
            }
        }
    }

    private fun relayWebSocketDirection(
        source: InputStream,
        sink: OutputStream,
        direction: WebSocketDirection,
        maxCaptureBytes: Int,
        messages: MutableList<WebSocketMessage>,
    ): Long {
        var totalBytes = 0L
        try {
            while (true) {
                val frame = readAndForwardWebSocketFrame(source, sink, maxCaptureBytes) ?: break
                totalBytes += frame.totalPayloadSize

                val opcode = mapWebSocketOpcode(frame.opcode)
                if (opcode != null) {
                    messages.add(
                        WebSocketMessage(
                            direction = direction,
                            opcode = opcode,
                            payloadText = decodeWebSocketPayload(frame.capturedPayload, opcode),
                            payloadSizeBytes = frame.totalPayloadSize
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            timestampEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }

                if (frame.opcode == 8) break  // Close frame - stop relaying
            }
        } catch (error: Exception) {
            if (!error.isExpectedRelayTermination()) {
                logWarn("WebSocket relay error ($direction): ${error.message}")
            }
        }
        return totalBytes
    }

    private suspend fun relayAndCaptureWebSocketFrames(
        clientIn: InputStream,
        clientOut: OutputStream,
        upstreamIn: InputStream,
        upstreamOut: OutputStream,
        maxCaptureBytes: Int,
    ): Pair<List<WebSocketMessage>, Pair<Long, Long>> = coroutineScope {
        val messages: MutableList<WebSocketMessage> = CopyOnWriteArrayList()

        val c2uJob = async(Dispatchers.IO) {
            relayWebSocketDirection(
                source = clientIn,
                sink = upstreamOut,
                direction = WebSocketDirection.ClientToServer,
                maxCaptureBytes = maxCaptureBytes,
                messages = messages,
            )
        }
        val u2cJob = async(Dispatchers.IO) {
            relayWebSocketDirection(
                source = upstreamIn,
                sink = clientOut,
                direction = WebSocketDirection.ServerToClient,
                maxCaptureBytes = maxCaptureBytes,
                messages = messages,
            )
        }

        val c2uBytes = c2uJob.await()
        val u2cBytes = u2cJob.await()

        messages.sortedBy { it.timestampEpochMillis } to (c2uBytes to u2cBytes)
    }

    private suspend fun handleWebSocketSession(
        clientInput: InputStream,
        clientOutput: OutputStream,
        connectTarget: ConnectTarget,
        request: ParsedRequest,
        targetUrl: URL,
        requestTimestamp: Long,
        requestPreview: String?,
        requestTraces: List<RuleExecutionTrace>,
        maxBodyCaptureBytes: Long,
    ): WebSocketRelayResult {
        val sessionId = UUID.randomUUID().toString()
        val capturedRequest = CapturedRequest(
            method = request.method,
            url = targetUrl.toString(),
            headers = request.headers,
            body = requestPreview,
            bodySizeBytes = request.bodyBytes.size.toLong(),
            timestampEpochMillis = requestTimestamp,
        )

        val upstreamSocket = runCatching {
            (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(connectTarget.host, connectTarget.port) as SSLSocket
        }.getOrElse { error ->
            val message = "Failed to connect WebSocket upstream: ${error.message}"
            logError(message, error)
            sessionRepository.addSession(
                CapturedSession(
                    id = sessionId,
                    request = capturedRequest,
                    response = null,
                    error = message,
                    durationMillis = System.currentTimeMillis() - requestTimestamp,
                    appliedRules = buildAppliedRuleTraces(requestTraces, emptyList()),
                ),
            )
            writeRawResponseHeader(clientOutput, 502, "Bad Gateway", emptyList())
            return WebSocketRelayResult(0L, 0L, message)
        }

        return try {
            upstreamSocket.apply {
                enabledProtocols = enabledProtocols.preferredTlsProtocols()
                startHandshake()
            }

            val upstreamIn = upstreamSocket.inputStream
            val upstreamOut = upstreamSocket.outputStream

            writeRawRequest(request, upstreamOut)

            val responseHeaderBytes = readHeaderBytes(upstreamIn)
            if (responseHeaderBytes == null) {
                val message = "WebSocket upstream closed without response."
                sessionRepository.addSession(
                    CapturedSession(
                        id = sessionId,
                        request = capturedRequest,
                        response = null,
                        error = message,
                        durationMillis = System.currentTimeMillis() - requestTimestamp,
                        appliedRules = buildAppliedRuleTraces(requestTraces, emptyList()),
                    ),
                )
                writeRawResponseHeader(clientOutput, 502, "Bad Gateway", emptyList())
                return WebSocketRelayResult(0L, 0L, message)
            }

            val (statusCode, reasonPhrase, responseHeaders) = parseRawResponseHeader(responseHeaderBytes)
                ?: run {
                    val message = "Malformed WebSocket upstream response."
                    sessionRepository.addSession(
                        CapturedSession(
                            id = sessionId,
                            request = capturedRequest,
                            response = null,
                            error = message,
                            durationMillis = System.currentTimeMillis() - requestTimestamp,
                            appliedRules = buildAppliedRuleTraces(requestTraces, emptyList()),
                        ),
                    )
                    writeRawResponseHeader(clientOutput, 502, "Bad Gateway", emptyList())
                    return WebSocketRelayResult(0L, 0L, message)
                }

            val responseTimestamp = System.currentTimeMillis()
            writeRawResponseHeader(clientOutput, statusCode, reasonPhrase, responseHeaders)

            val initialSession = CapturedSession(
                id = sessionId,
                request = capturedRequest,
                response = CapturedResponse(
                    statusCode = statusCode,
                    reasonPhrase = reasonPhrase,
                    headers = responseHeaders,
                    body = null,
                    bodySizeBytes = 0L,
                    timestampEpochMillis = responseTimestamp,
                ),
                error = null,
                durationMillis = responseTimestamp - requestTimestamp,
                appliedRules = buildAppliedRuleTraces(requestTraces, emptyList()),
                webSocketMessages = emptyList(),
            )
            sessionRepository.upsertSession(initialSession)

            if (statusCode != 101) {
                logDebug("WebSocket upgrade declined by upstream: $statusCode $reasonPhrase")
                return WebSocketRelayResult(0L, 0L, null)
            }

            logDebug("WebSocket connection established: $targetUrl")

            val maxCapture = maxBodyCaptureBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val (capturedMessages, byteCounts) = relayAndCaptureWebSocketFrames(
                clientIn = clientInput,
                clientOut = clientOutput,
                upstreamIn = upstreamIn,
                upstreamOut = upstreamOut,
                maxCaptureBytes = maxCapture,
            )

            val finalDuration = System.currentTimeMillis() - requestTimestamp
            sessionRepository.upsertSession(
                initialSession.copy(
                    durationMillis = finalDuration,
                    webSocketMessages = capturedMessages,
                ),
            )

            logDebug(
                "WebSocket session completed: $targetUrl " +
                    "messages=${capturedMessages.size} " +
                    "bytes(${byteCounts.first}/${byteCounts.second})",
            )

            WebSocketRelayResult(
                clientToUpstreamBytes = byteCounts.first,
                upstreamToClientBytes = byteCounts.second,
                errorMessage = null,
            )
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName.orEmpty()
            logError("WebSocket session error for $targetUrl: $message", error)
            WebSocketRelayResult(0L, 0L, message)
        } finally {
            runCatching { upstreamSocket.close() }
        }
    }

    private companion object {
        const val LOG_PREFIX = "[cmp-proxy]"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val SOCKET_TIMEOUT_MILLIS = 30_000

        const val MAX_HEADER_BYTES = 65_536
        const val TERMINATOR_BYTES = 4
        const val TUNNEL_BUFFER_BYTES = 16 * 1024
        const val DEFAULT_HTTP_PORT = 80
        const val DEFAULT_HTTPS_PORT = 443

        const val HEADER_LINE_DELIMITER = "\r\n"
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"

        const val CONTENT_LENGTH_HEADER = "Content-Length"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val CONTENT_ENCODING_HEADER = "Content-Encoding"
        const val CONTENT_DISPOSITION_HEADER = "Content-Disposition"
        const val CACHE_CONTROL_HEADER = "Cache-Control"
        const val CONNECTION_HEADER = "Connection"
        const val HOST_HEADER = "Host"
        const val TRANSFER_ENCODING_HEADER = "Transfer-Encoding"
        const val CONNECT_MODE_HEADER = "X-CMP-Connect-Mode"
        const val IDENTITY_ENCODING = "identity"
        const val GZIP_ENCODING = "gzip"
        const val X_GZIP_ENCODING = "x-gzip"
        const val DEFLATE_ENCODING = "deflate"

        val LOCALHOST_HOSTS = setOf("localhost", "127.0.0.1", "::1")
        val WILDCARD_PROXY_HOSTS = setOf("0.0.0.0", "::")

        val HOP_BY_HOP_HEADERS = setOf(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "proxy-connection",
        )

        val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
        val LINE_FEED: Byte = '\n'.code.toByte()
    }
}
