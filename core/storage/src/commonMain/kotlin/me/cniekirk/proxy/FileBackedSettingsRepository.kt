package me.cniekirk.proxy

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import java.io.File

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FileBackedSettingsRepository : SettingsRepository {
    private val dataStore: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        produceFile = {
            File(FILE_PATH).also { it.parentFile?.mkdirs() }
        },
    )

    override val settings: Flow<AppSettings> = dataStore.data

    override suspend fun updateProxySettings(transform: (ProxySettings) -> ProxySettings) {
        dataStore.updateData { current -> current.copy(proxy = transform(current.proxy)) }
    }

    override suspend fun updateCertificateState(transform: (CertificateState) -> CertificateState) {
        dataStore.updateData { current -> current.copy(certificate = transform(current.certificate)) }
    }

    private companion object {
        const val FILE_PATH = "settings.json"
    }
}
