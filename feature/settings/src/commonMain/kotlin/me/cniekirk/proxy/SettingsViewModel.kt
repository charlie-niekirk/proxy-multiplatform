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
    private val settingsRepository: SettingsRepository
) : ViewModel(), ContainerHost<SettingsState, Nothing> {

    override val container = container<SettingsState, Nothing>(SettingsState()) {
        observeSettings()
    }

    private fun observeSettings() = intent {
        settingsRepository.settings
            .distinctUntilChanged()
            .collect { currentSettings ->
                reduce {
                    state.copy(settings = currentSettings)
                }
            }
    }

    fun updateProxyPort(port: Int) = intent {
        settingsRepository.updateProxySettings { it.copy(port = port) }
    }

    fun toggleSslDecryption(enabled: Boolean) = intent {
        settingsRepository.updateProxySettings { it.copy(sslDecryptionEnabled = enabled) }
    }
}