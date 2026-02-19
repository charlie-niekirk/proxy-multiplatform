package me.cniekirk.proxy

import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal object AppSettingsSerializer : Serializer<AppSettings> {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            json.decodeFromString(AppSettings.serializer(), input.bufferedReader().use { it.readText() })
        } catch (_: SerializationException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}