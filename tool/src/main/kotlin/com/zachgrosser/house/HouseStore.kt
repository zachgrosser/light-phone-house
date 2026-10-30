package com.zachgrosser.house

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HouseSettings(
    val address: String? = null,
    val token: String? = null,
    val shortcuts: List<Shortcut> = emptyList(),
) {
    val isConnected: Boolean get() = !address.isNullOrEmpty() && !token.isNullOrEmpty()
}

// Saved as one JSON file in the tool's private storage (token included).
class HouseStore(filesDir: File) {

    private val file = File(filesDir, "house.json")

    suspend fun load(): HouseSettings = locked { read() }

    suspend fun update(change: (HouseSettings) -> HouseSettings): HouseSettings = locked {
        val updated = change(read())
        write(updated)
        updated
    }

    suspend fun setConnection(address: String?, token: String?) =
        update { it.copy(address = address, token = token) }

    suspend fun toggleShortcut(shortcut: Shortcut) = update { settings ->
        val existing = settings.shortcuts.any { it.entityId == shortcut.entityId }
        settings.copy(
            shortcuts = if (existing) {
                settings.shortcuts.filterNot { it.entityId == shortcut.entityId }
            } else {
                settings.shortcuts + shortcut
            },
        )
    }

    suspend fun clear() = update { HouseSettings() }

    private fun read(): HouseSettings {
        if (!file.exists()) return HouseSettings()
        return runCatching { json.decodeFromString(HouseSettings.serializer(), file.readText()) }
            .getOrDefault(HouseSettings())
    }

    // Write to a temp file first so a crash can't leave a half-written file.
    private fun write(settings: HouseSettings) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(HouseSettings.serializer(), settings))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private suspend fun <T> locked(block: () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { block() } }

    companion object {
        private val lock = Mutex()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
