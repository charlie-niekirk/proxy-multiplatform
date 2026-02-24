package me.cniekirk.proxy

interface CertificateDistributionService {
    suspend fun getOnboardingUrls(proxyPort: Int): CertificateOnboardingUrls
    suspend fun loadCertificatePayload(): CertificatePayload?

    companion object {
        const val INTERNAL_HOST = "cmp-proxy"
        const val CERTIFICATE_PATH = "/SSL"
        const val DEFAULT_CERTIFICATE_URL = "http://$INTERNAL_HOST$CERTIFICATE_PATH"
    }
}

data class CertificateOnboardingUrls(
    val friendlyUrl: String,
    val fallbackUrl: String?,
)

data class CertificatePayload(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)
