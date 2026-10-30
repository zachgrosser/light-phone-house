package com.zachgrosser.house

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HouseStoreTest {

    private val dir: File = createTempDirectory("house-store").toFile()
    private val store = HouseStore(dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `starts empty and not connected`() = runBlocking {
        val settings = store.load()
        assertEquals(HouseSettings(), settings)
        assertFalse(settings.isConnected)
    }

    @Test
    fun `keeps the connection between launches`() = runBlocking {
        store.setConnection("http://homeassistant.local:8123", "token")
        val reloaded = HouseStore(dir).load()
        assertEquals("http://homeassistant.local:8123", reloaded.address)
        assertTrue(reloaded.isConnected)
    }

    @Test
    fun `shortcuts are added in order and removed on a second tap`() = runBlocking {
        store.toggleShortcut(Shortcut("light.a", "A"))
        store.toggleShortcut(Shortcut("scene.b", "B"))
        store.toggleShortcut(Shortcut("switch.c", "C"))
        assertEquals(listOf("light.a", "scene.b", "switch.c"), store.load().shortcuts.map { it.entityId })

        store.toggleShortcut(Shortcut("scene.b", "B"))
        assertEquals(listOf("light.a", "switch.c"), store.load().shortcuts.map { it.entityId })
    }

    @Test
    fun `forgetting removes everything`() = runBlocking {
        store.setConnection("http://ha:8123", "token")
        store.toggleShortcut(Shortcut("light.a", "A"))
        store.clear()
        assertEquals(HouseSettings(), HouseStore(dir).load())
    }

    @Test
    fun `a damaged file is treated as empty`() = runBlocking {
        File(dir, "house.json").writeText("{not json")
        assertEquals(HouseSettings(), store.load())
    }
}
