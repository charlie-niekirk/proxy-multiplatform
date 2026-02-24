package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.NetworkInterface

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class JvmCertificateDistributionService(
    private val tlsService: TlsService,
) : CertificateDistributionService {

    override suspend fun getOnboardingUrls(proxyPort: Int): CertificateOnboardingUrls {
        val fallbackUrl = detectPrimaryLanIpv4Address()?.let { lanAddress ->
            "http://$lanAddress:$proxyPort${CertificateDistributionService.CERTIFICATE_PATH}"
        }
        return CertificateOnboardingUrls(
            friendlyUrl = CertificateDistributionService.DEFAULT_CERTIFICATE_URL,
            fallbackUrl = fallbackUrl,
        )
    }

    override suspend fun loadCertificatePayload(): CertificatePayload? {
        val certificateBytes = tlsService.readRootCertificatePem() ?: return null
        return CertificatePayload(
            fileName = CERTIFICATE_FILE_NAME,
            contentType = CERTIFICATE_CONTENT_TYPE,
            bytes = certificateBytes,
        )
    }

    private fun detectPrimaryLanIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { networkInterface ->
                    !networkInterface.isLoopback && networkInterface.isUp
                }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList().asSequence()
                }
                .firstOrNull { address ->
                    !address.isLoopbackAddress &&
                        !address.hostAddress.contains(":")
                }
                ?.hostAddress
        }.getOrNull()
    }

    private companion object {
        const val CERTIFICATE_FILE_NAME = "cmp-proxy-root-ca.pem"
        const val CERTIFICATE_CONTENT_TYPE = "application/x-x509-ca-cert"
    }
}
