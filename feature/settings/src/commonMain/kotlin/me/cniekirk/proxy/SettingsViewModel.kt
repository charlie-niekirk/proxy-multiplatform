package me.cniekirk.proxy

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.distinctUntilChanged
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@Inject
@ViewModelKey(SettingsViewModel::class)
@ContributesIntoMap(scope = AppScope::class, binding = binding<ViewModel>())
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tlsService: TlsService,
    private val certificateDistributionService: CertificateDistributionService,
) : ViewModel(), ContainerHost<SettingsState, Nothing> {

    override val container = container<SettingsState, Nothing>(SettingsState()) {
        observeSettings()
    }

    private fun observeSettings() = intent {
        settingsRepository.settings
            .distinctUntilChanged()
            .collect { currentSettings ->
                val onboardingUrls = runCatching {
                    certificateDistributionService.getOnboardingUrls(
                        proxyPort = currentSettings.proxy.port,
                    )
                }.getOrElse {
                    CertificateOnboardingUrls(
                        friendlyUrl = CertificateDistributionService.DEFAULT_CERTIFICATE_URL,
                        fallbackUrl = null,
                    )
                }

                reduce {
                    state.copy(
                        settings = currentSettings,
                        onboardingUrls = onboardingUrls,
                    )
                }
            }
    }

    fun updateProxyPort(port: Int) = intent {
        settingsRepository.updateProxySettings { it.copy(port = port) }
    }

    fun toggleSslDecryption(enabled: Boolean) = intent {
        reduce {
            state.copy(
                isProvisioningCertificate = enabled,
                sslToggleError = null,
            )
        }

        if (enabled) {
            val certificateProvisionResult = runCatching {
                tlsService.ensureCertificateMaterial()
            }

            if (certificateProvisionResult.isFailure) {
                reduce {
                    state.copy(
                        isProvisioningCertificate = false,
                        sslToggleError = certificateProvisionResult.exceptionOrNull()?.message.orEmpty(),
                    )
                }
                return@intent
            }
        }

        settingsRepository.updateProxySettings { it.copy(sslDecryptionEnabled = enabled) }

        reduce {
            state.copy(
                isProvisioningCertificate = false,
                sslToggleError = null,
            )
        }
    }
}
