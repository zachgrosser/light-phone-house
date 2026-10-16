package com.zachgrosser.house

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// The data House works with, and the rules for what a tap does. Plain Kotlin with no Android
// classes, so it's all covered by unit tests (ModelsTest).

/**
 * The kinds of Home Assistant entities House can show, in the order the picker lists them.
 * Everything else (sensors, locks, cameras, thermostats…) is left to the full Home Assistant app.
 */
enum class EntityKind(val heading: String, val domains: Set<String>) {
    Light("Lights", setOf("light")),
    Switch("Switches", setOf("switch")),
    Fan("Fans", setOf("fan")),
    Cover("Blinds and garage doors", setOf("cover")),
    Toggle("Toggles", setOf("input_boolean")),
    Scene("Scenes", setOf("scene")),
    Script("Scripts", setOf("script")),
    Button("Buttons", setOf("button", "input_button")),
    ;

    companion object {
        fun of(entityId: String): EntityKind? = entries.firstOrNull { domainOf(entityId) in it.domains }
    }
}

/** "light.kitchen" → "light". */
fun domainOf(entityId: String): String = entityId.substringBefore('.')

/** A thing in Home Assistant, as `GET /api/states` describes it. */
data class Entity(
    val id: String,
    val state: String,
    val name: String,
    /** For a group (such as a light group), how many entities it contains. */
    val memberCount: Int? = null,
) {
    val kind: EntityKind? get() = EntityKind.of(id)
}

/** One entity chosen to show on House's main screen. [name] is kept for when Home Assistant can't be reached. */
@Serializable
data class Shortcut(val entityId: String, val name: String)

/** A Home Assistant action: `POST /api/services/<domain>/<service>` with `{"entity_id": …}`. */
data class ServiceCall(val domain: String, val service: String, val entityId: String)

/** What a shortcut shows on the right: a switch drawn on or off, or a word. */
data class Status(val label: String, val isOn: Boolean? = null) {
    companion object {
        val Unavailable = Status("Unavailable")
    }
}

private val UNAVAILABLE_STATES = setOf("unavailable", "unknown")

/** What House shows for an entity of [kind] whose state is [state]. */
fun statusOf(kind: EntityKind, state: String): Status {
    if (state in UNAVAILABLE_STATES && kind !in ACTION_KINDS) return Status.Unavailable
    return when (kind) {
        EntityKind.Light, EntityKind.Switch, EntityKind.Fan, EntityKind.Toggle -> when (state) {
            "on" -> Status("On", isOn = true)
            "off" -> Status("Off", isOn = false)
            else -> Status(state.replaceFirstChar { it.uppercaseChar() })
        }
        EntityKind.Cover -> when (state) {
            "open" -> Status("Open")
            "closed" -> Status("Closed")
            "opening" -> Status("Opening")
            "closing" -> Status("Closing")
            else -> Status(state.replaceFirstChar { it.uppercaseChar() })
        }
        EntityKind.Scene, EntityKind.Script -> Status("Run")
        EntityKind.Button -> Status("Press")
    }
}

private val ACTION_KINDS = setOf(EntityKind.Scene, EntityKind.Script, EntityKind.Button)

/**
 * What tapping [entityId] does, given its current [state]: switches turn off when on and on when
 * off; covers close when open (or opening) and open otherwise; scenes, scripts and buttons run.
 * Uses explicit on/off rather than "toggle", so a tap always does what the screen showed.
 * Null when there's nothing sensible to do (the device is unavailable).
 */
fun tapAction(entityId: String, state: String): ServiceCall? {
    val kind = EntityKind.of(entityId) ?: return null
    val domain = domainOf(entityId)
    if (state in UNAVAILABLE_STATES && kind !in ACTION_KINDS) return null
    val service = when (kind) {
        EntityKind.Light, EntityKind.Switch, EntityKind.Fan, EntityKind.Toggle ->
            if (state == "on") "turn_off" else "turn_on"
        EntityKind.Cover -> if (state == "open" || state == "opening") "close_cover" else "open_cover"
        EntityKind.Scene, EntityKind.Script -> "turn_on"
        EntityKind.Button -> "press"
    }
    return ServiceCall(domain, service, entityId)
}

/** The state Home Assistant will most likely report straight after [call], to show at once. */
fun expectedState(call: ServiceCall): String? = when (call.service) {
    "turn_on" -> if (call.domain in setOf("scene", "script")) null else "on"
    "turn_off" -> "off"
    "open_cover" -> "opening"
    "close_cover" -> "closing"
    else -> null
}

private val json = Json { ignoreUnknownKeys = true }

/** Reads the JSON from `GET /api/states` (a list) or `GET /api/states/<id>` (one object). */
fun parseStates(body: String): List<Entity> {
    val element = json.parseToJsonElement(body)
    val objects = when (element) {
        is JsonArray -> element.filterIsInstance<JsonObject>()
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
    return objects.mapNotNull(::parseEntity)
}

private fun parseEntity(obj: JsonObject): Entity? {
    val id = (obj["entity_id"] as? JsonPrimitive)?.contentOrNull ?: return null
    val state = (obj["state"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
    val attributes = obj["attributes"] as? JsonObject
    val name = (attributes?.get("friendly_name") as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
    // Groups (light groups, switch groups…) list their members under "entity_id".
    val members = (attributes?.get("entity_id") as? JsonArray)?.size
    return Entity(id = id, state = state, name = name ?: id, memberCount = members)
}

/** A line under an entity's name in the picker, such as "Group of 4". */
fun describe(entity: Entity): String? = entity.memberCount?.let { "Group of $it" }

/**
 * Turns what someone typed into a Home Assistant address: "homeassistant.local" becomes
 * "http://homeassistant.local:8123". Without "http://" or "https://", House assumes plain http on
 * Home Assistant's usual port, 8123. A trailing "/" or "/api" is dropped. Null if it isn't an
 * address.
 */
fun normalizeAddress(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val typedScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
    if (!typedScheme && (trimmed.contains("://") || trimmed.contains(':') && trimmed.substringAfter(':').firstOrNull()?.isDigit() != true)) {
        return null
    }
    var text = if (typedScheme) trimmed else "http://$trimmed"
    text = text.trimEnd('/').removeSuffix("/api").trimEnd('/')
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val host = uri.host?.ifEmpty { null } ?: return null
    val scheme = uri.scheme.lowercase()
    val port = when {
        uri.port != -1 -> uri.port
        !typedScheme -> 8123
        else -> -1
    }
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return buildString {
        append(scheme).append("://").append(host)
        if (port != -1) append(':').append(port)
        append(path)
    }
}

/** What a setup QR code held: an access token, and maybe the address too. */
data class SetupCode(val address: String?, val token: String)

private val TOKEN_PATTERN = Regex("^[A-Za-z0-9._-]{20,}$")

/**
 * Reads a scanned QR code. It can hold just the access token, or the address and the token
 * together, separated by a space or a new line (in either order). Null if no token is found.
 */
fun parseSetupCode(text: String): SetupCode? {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val address = parts.firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
    val token = parts.filter { it != address && TOKEN_PATTERN.matches(it) }.maxByOrNull { it.length } ?: return null
    return SetupCode(address = address?.let(::normalizeAddress), token = token)
}
