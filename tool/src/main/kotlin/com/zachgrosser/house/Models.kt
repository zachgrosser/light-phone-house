package com.zachgrosser.house

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// No Android classes in here, so it can all be unit tested.

// What House supports, in picker order. Locks and alarms are left out on purpose.
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

fun domainOf(entityId: String): String = entityId.substringBefore('.')

data class Entity(
    val id: String,
    val state: String,
    val name: String,
    val memberCount: Int? = null,
) {
    val kind: EntityKind? get() = EntityKind.of(id)
}

@Serializable
data class Shortcut(val entityId: String, val name: String)

data class ServiceCall(val domain: String, val service: String, val entityId: String)

data class Status(val label: String, val isOn: Boolean? = null) {
    companion object {
        val Unavailable = Status("Unavailable")
    }
}

private val UNAVAILABLE_STATES = setOf("unavailable", "unknown")

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

// Uses explicit on/off instead of "toggle" so a tap does what the screen showed.
// Returns null if the device is unavailable.
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

fun expectedState(call: ServiceCall): String? = when (call.service) {
    "turn_on" -> if (call.domain in setOf("scene", "script")) null else "on"
    "turn_off" -> "off"
    "open_cover" -> "opening"
    "close_cover" -> "closing"
    else -> null
}

private val json = Json { ignoreUnknownKeys = true }

// Handles both /api/states (a list) and /api/states/<id> (one object).
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
    // Groups list their members under "entity_id".
    val members = (attributes?.get("entity_id") as? JsonArray)?.size
    return Entity(id = id, state = state, name = name ?: id, memberCount = members)
}

fun describe(entity: Entity): String? = entity.memberCount?.let { "Group of $it" }

// "homeassistant.local" -> "http://homeassistant.local:8123". No scheme means http on port 8123.
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

data class SetupCode(val address: String?, val token: String)

private val TOKEN_PATTERN = Regex("^[A-Za-z0-9._-]{20,}$")

// A QR code can hold just the token, or the address and token separated by whitespace.
fun parseSetupCode(text: String): SetupCode? {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val address = parts.firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
    val token = parts.filter { it != address && TOKEN_PATTERN.matches(it) }.maxByOrNull { it.length } ?: return null
    return SetupCode(address = address?.let(::normalizeAddress), token = token)
}
