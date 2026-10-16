package com.zachgrosser.house

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** House's settings: where Home Assistant is, how to sign in, and the chosen shortcuts. */
@Serializable
data class HouseSettings(
    val address: String? = null,
    val token: String? = null,
    val shortcuts: List<Shortcut> = emptyList(),
) {
    val isConnected: Boolean get() = !address.isNullOrEmpty() && !token.isNullOrEmpty()
}

/**
 * Keeps [HouseSettings] in one JSON file in the tool's private storage, which other tools can't
 * read. The access token is stored there too, so House can sign in each time it opens.
 */
class HouseStore(filesDir: File) {

    private val file = File(filesDir, "house.json")

    suspend fun load(): HouseSettings = locked { read() }

    /** Changes the settings and saves them; returns the new settings. */
    suspend fun update(change: (HouseSettings) -> HouseSettings): HouseSettings = locked {
        val updated = change(read())
        write(updated)
        updated
    }

    suspend fun setConnection(address: String?, token: String?) =
        update { it.copy(address = address, token = token) }

    /** Adds [shortcut] at the end, or removes it if it's already there. */
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

    /** Forgets the address, token and shortcuts. */
    suspend fun clear() = update { HouseSettings() }

    private fun read(): HouseSettings {
        if (!file.exists()) return HouseSettings()
        return runCatching { json.decodeFromString(HouseSettings.serializer(), file.readText()) }
            .getOrDefault(HouseSettings())
    }

    /** Writes to a temporary file first so a crash can't leave a half-written file behind. */
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
