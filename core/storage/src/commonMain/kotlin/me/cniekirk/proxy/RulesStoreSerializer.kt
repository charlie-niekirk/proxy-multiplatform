package me.cniekirk.proxy

import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal object RulesStoreSerializer : Serializer<RulesStore> {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override val defaultValue: RulesStore = RulesStore()

    override suspend fun readFrom(input: InputStream): RulesStore {
        return try {
            json.decodeFromString(RulesStore.serializer(), input.bufferedReader().use { it.readText() })
        } catch (_: SerializationException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: RulesStore, output: OutputStream) {
        output.write(json.encodeToString(RulesStore.serializer(), t).encodeToByteArray())
    }
}
