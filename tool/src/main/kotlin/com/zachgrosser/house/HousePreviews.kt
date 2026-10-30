package com.zachgrosser.house

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Light Phone III is 1080x1240, Light Flip is 480x640 (dp size is a rough guess).

@Preview(name = "Light Phone III", widthDp = 360, heightDp = 413, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Light Flip", widthDp = 274, heightDp = 366, showBackground = true, backgroundColor = 0xFF000000)
annotation class LightDevicePreviews

@LightDevicePreviews
@Composable
private fun HomeNotConnectedPreview() = HouseTheme {
    HomeContent(HomeState(loaded = true, connected = false), onTap = {}, onRefresh = {}, onEdit = {}, onSettings = {})
}

@LightDevicePreviews
@Composable
private fun HomeEmptyPreview() = HouseTheme {
    HomeContent(HomeState(loaded = true, connected = true), onTap = {}, onRefresh = {}, onEdit = {}, onSettings = {})
}

@LightDevicePreviews
@Composable
private fun HomePreview() = HouseTheme {
    HomeContent(
        HomeState(loaded = true, connected = true, rows = SAMPLE_ROWS),
        onTap = {}, onRefresh = {}, onEdit = {}, onSettings = {},
    )
}

@LightDevicePreviews
@Composable
private fun HomeOfflinePreview() = HouseTheme {
    HomeContent(
        HomeState(
            loaded = true,
            connected = true,
            rows = SAMPLE_ROWS.map { it.copy(state = null) },
            message = "Couldn't reach Home Assistant. Check the phone is online and the address in Settings.",
        ),
        onTap = {}, onRefresh = {}, onEdit = {}, onSettings = {},
    )
}

@LightDevicePreviews
@Composable
private fun PickerPreview() = HouseTheme {
    PickerContent(
        PickerState.Ready(SAMPLE_ENTITIES, chosen = setOf("light.downstairs", "scene.movie_night")),
        onToggle = {},
        onBack = {},
    )
}

@LightDevicePreviews
@Composable
private fun SettingsPreview() = HouseTheme {
    SettingsContent(
        state = SettingsState(
            address = "http://homeassistant.local:8123",
            hasToken = true,
            status = "Connected to Home (Home Assistant 2026.10.1).",
        ),
        onBack = {}, onEditAddress = {}, onScanToken = {}, onTypeToken = {}, onCheck = {}, onForget = {}, onAbout = {},
    )
}

@LightDevicePreviews
@Composable
private fun SettingsEmptyPreview() = HouseTheme {
    SettingsContent(
        state = SettingsState(),
        onBack = {}, onEditAddress = {}, onScanToken = {}, onTypeToken = {}, onCheck = {}, onForget = {}, onAbout = {},
    )
}

@LightDevicePreviews
@Composable
private fun AboutPreview() = HouseTheme { AboutContent(onBack = {}) }

// Sample data

private val SAMPLE_ROWS = listOf(
    ShortcutRow(Shortcut("light.downstairs", "Downstairs lights"), state = "on"),
    ShortcutRow(Shortcut("light.bedroom", "Bedroom"), state = "off"),
    ShortcutRow(Shortcut("switch.porch", "Porch light"), state = "off", busy = true),
    ShortcutRow(Shortcut("cover.garage_door", "Garage door"), state = "closed"),
    ShortcutRow(Shortcut("scene.movie_night", "Movie night"), state = "2026-10-15T20:01:00+00:00"),
    ShortcutRow(Shortcut("script.goodnight", "Goodnight"), state = "off"),
    ShortcutRow(Shortcut("fan.office", "Office fan"), state = "unavailable"),
)

private val SAMPLE_ENTITIES = listOf(
    Entity("light.downstairs", "on", "Downstairs lights", memberCount = 6),
    Entity("light.bedroom", "off", "Bedroom"),
    Entity("light.kitchen", "on", "Kitchen"),
    Entity("switch.porch", "off", "Porch light"),
    Entity("cover.garage_door", "closed", "Garage door"),
    Entity("scene.movie_night", "unknown", "Movie night"),
    Entity("script.goodnight", "off", "Goodnight"),
)
