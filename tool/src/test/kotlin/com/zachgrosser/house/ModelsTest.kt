package com.zachgrosser.house

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsTest {

    @Test
    fun `addresses without a scheme get http and port 8123`() {
        assertEquals("http://homeassistant.local:8123", normalizeAddress("homeassistant.local"))
        assertEquals("http://192.168.1.20:8123", normalizeAddress(" 192.168.1.20/ "))
        assertEquals("http://192.168.1.20:8124", normalizeAddress("192.168.1.20:8124"))
    }

    @Test
    fun `addresses with a scheme keep their port, or none`() {
        assertEquals("https://abc123.ui.nabu.casa", normalizeAddress("https://abc123.ui.nabu.casa/"))
        assertEquals("http://homeassistant.local:8123", normalizeAddress("HTTP://homeassistant.local:8123/api"))
        assertEquals("https://example.com/ha", normalizeAddress("https://example.com/ha/"))
    }

    @Test
    fun `things that aren't addresses are refused`() {
        assertNull(normalizeAddress(""))
        assertNull(normalizeAddress("home assistant"))
        assertNull(normalizeAddress("ftp://example.com"))
        assertNull(normalizeAddress("http://"))
        assertNull(normalizeAddress("http:"))
        assertNull(normalizeAddress("homeassistant:"))
    }

    @Test
    fun `entity kinds come from the domain`() {
        assertEquals(EntityKind.Light, EntityKind.of("light.kitchen"))
        assertEquals(EntityKind.Button, EntityKind.of("input_button.doorbell"))
        assertEquals(EntityKind.Toggle, EntityKind.of("input_boolean.guest_mode"))
        assertNull(EntityKind.of("lock.front_door"))
        assertNull(EntityKind.of("sensor.temperature"))
    }

    @Test
    fun `tapping a switch turns it the other way`() {
        assertEquals(ServiceCall("light", "turn_off", "light.kitchen"), tapAction("light.kitchen", "on"))
        assertEquals(ServiceCall("light", "turn_on", "light.kitchen"), tapAction("light.kitchen", "off"))
        assertEquals(ServiceCall("fan", "turn_on", "fan.office"), tapAction("fan.office", "off"))
    }

    @Test
    fun `tapping a cover opens or closes it`() {
        assertEquals("close_cover", tapAction("cover.garage", "open")?.service)
        assertEquals("close_cover", tapAction("cover.garage", "opening")?.service)
        assertEquals("open_cover", tapAction("cover.garage", "closed")?.service)
    }

    @Test
    fun `scenes, scripts and buttons run whatever their state`() {
        // A scene's state is when it last ran, or "unknown" if it never has.
        assertEquals(ServiceCall("scene", "turn_on", "scene.movie"), tapAction("scene.movie", "unknown"))
        assertEquals(ServiceCall("script", "turn_on", "script.goodnight"), tapAction("script.goodnight", "off"))
        assertEquals(ServiceCall("button", "press", "button.restart"), tapAction("button.restart", "2026-01-01T00:00:00+00:00"))
        assertEquals(ServiceCall("input_button", "press", "input_button.bell"), tapAction("input_button.bell", "unknown"))
    }

    @Test
    fun `unavailable devices and unsupported entities do nothing`() {
        assertNull(tapAction("light.kitchen", "unavailable"))
        assertNull(tapAction("switch.porch", "unknown"))
        assertNull(tapAction("lock.front_door", "locked"))
    }

    @Test
    fun `statuses`() {
        assertEquals(Status("On", isOn = true), statusOf(EntityKind.Light, "on"))
        assertEquals(Status("Off", isOn = false), statusOf(EntityKind.Switch, "off"))
        assertEquals(Status.Unavailable, statusOf(EntityKind.Fan, "unavailable"))
        assertEquals(Status("Closed"), statusOf(EntityKind.Cover, "closed"))
        assertEquals(Status("Run"), statusOf(EntityKind.Scene, "unknown"))
        assertEquals(Status("Press"), statusOf(EntityKind.Button, "unavailable"))
    }

    @Test
    fun `expected states after a tap`() {
        assertEquals("on", expectedState(ServiceCall("light", "turn_on", "light.a")))
        assertEquals("off", expectedState(ServiceCall("switch", "turn_off", "switch.a")))
        assertEquals("opening", expectedState(ServiceCall("cover", "open_cover", "cover.a")))
        assertNull(expectedState(ServiceCall("scene", "turn_on", "scene.a")))
        assertNull(expectedState(ServiceCall("button", "press", "button.a")))
    }

    @Test
    fun `parses a list of states, including light groups`() {
        val body = """
            [
              {"entity_id": "light.downstairs", "state": "on",
               "attributes": {"friendly_name": "Downstairs", "entity_id": ["light.a", "light.b", "light.c"], "brightness": 200},
               "last_changed": "2026-10-16T08:00:00+00:00"},
              {"entity_id": "switch.porch", "state": "off", "attributes": {}},
              {"entity_id": "sensor.temp", "state": "21.5", "attributes": {"friendly_name": "Temperature"}},
              {"state": "on"}
            ]
        """.trimIndent()
        val entities = parseStates(body)
        assertEquals(
            listOf(
                Entity("light.downstairs", "on", "Downstairs", memberCount = 3),
                Entity("switch.porch", "off", "switch.porch"),
                Entity("sensor.temp", "21.5", "Temperature"),
            ),
            entities,
        )
        assertEquals("Group of 3", describe(entities[0]))
        assertNull(describe(entities[1]))
    }

    @Test
    fun `parses a single state`() {
        val body = """{"entity_id": "light.kitchen", "state": "off", "attributes": {"friendly_name": "Kitchen"}}"""
        assertEquals(listOf(Entity("light.kitchen", "off", "Kitchen")), parseStates(body))
    }

    @Test
    fun `picker groups by kind in order, alphabetically, without unsupported entities`() {
        val groups = groupForPicker(
            listOf(
                Entity("scene.b", "unknown", "b scene"),
                Entity("light.z", "on", "Zebra"),
                Entity("sensor.t", "1", "Temp"),
                Entity("light.a", "on", "apple"),
            ),
        )
        assertEquals(listOf(EntityKind.Light, EntityKind.Scene), groups.map { it.first })
        assertEquals(listOf("apple", "Zebra"), groups[0].second.map { it.name })
    }

    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhYmMifQ.abcDEF123_-xyz"

    @Test
    fun `a QR code can hold just the token`() {
        assertEquals(SetupCode(address = null, token = token), parseSetupCode("  $token\n"))
    }

    @Test
    fun `a QR code can hold the address and the token, in either order`() {
        val expected = SetupCode(address = "http://192.168.1.20:8123", token = token)
        assertEquals(expected, parseSetupCode("http://192.168.1.20:8123 $token"))
        assertEquals(expected, parseSetupCode("$token\nhttp://192.168.1.20:8123/"))
    }

    @Test
    fun `a QR code without a token is refused`() {
        assertNull(parseSetupCode("https://example.com"))
        assertNull(parseSetupCode("hello"))
        assertNull(parseSetupCode(""))
    }
}
