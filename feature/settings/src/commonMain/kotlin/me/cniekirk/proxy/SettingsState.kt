package me.cniekirk.proxy

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val onboardingUrls: CertificateOnboardingUrls = CertificateOnboardingUrls(
        friendlyUrl = CertificateDistributionService.DEFAULT_CERTIFICATE_URL,
        fallbackUrl = null,
    ),
    val isProvisioningCertificate: Boolean = false,
    val sslToggleError: String? = null,
)
