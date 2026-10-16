package com.zachgrosser.house

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// Talks to the person's own Home Assistant through its REST API:
// developers.home-assistant.io/docs/api/rest/
// Every request carries their long-lived access token as "Authorization: Bearer <token>".

private val USER_AGENT = "LightPhoneHouse/${BuildConfig.VERSION_NAME}"

/** Home Assistant refused the access token (it was deleted, or mistyped). */
class NotAuthorizedException : Exception("Home Assistant didn't accept the access token")

/** The entity doesn't exist any more (renamed or removed in Home Assistant). */
class EntityMissingException(val entityId: String) : Exception("No entity called $entityId")

class HomeAssistantHttpException(val status: Int) : Exception("Home Assistant answered HTTP $status")

/** A short, plain explanation of a failed request, for showing on screen. */
fun errorMessage(error: Throwable): String = when (error) {
    is NotAuthorizedException ->
        "Home Assistant didn't accept the access token. Make a new one and add it in Settings."
    is EntityMissingException -> "Home Assistant no longer has ${error.entityId}. Remove it in Edit."
    is HomeAssistantHttpException -> "Home Assistant answered with an error (${error.status})."
    is IOException -> "Couldn't reach Home Assistant. Check the phone is online and the address in Settings."
    else -> "Something went wrong talking to Home Assistant."
}

/** About the Home Assistant server, from `GET /api/config`. */
data class ServerInfo(val locationName: String?, val version: String?)

class HomeAssistantApi(private val address: String, private val token: String) {

    /** Checks the address and token work. */
    suspend fun serverInfo(): Result<ServerInfo> = apiCall {
        val response = client.get("$address/api/config") { authorize() }
        checkStatus(response)
        val config = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        ServerInfo(
            locationName = (config?.get("location_name") as? JsonPrimitive)?.contentOrNull,
            version = (config?.get("version") as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** Every entity House knows how to show. Used by the picker. */
    suspend fun supportedEntities(): Result<List<Entity>> = apiCall {
        val response = client.get("$address/api/states") { authorize() }
        checkStatus(response)
        parseStates(response.bodyAsText()).filter { it.kind != null }
    }

    /**
     * The current state of each of [entityIds], fetched one by one (all at once). For the
     * handful of shortcuts on the main screen this is much less data than every entity in the
     * house. Entities that no longer exist are left out of the result.
     */
    suspend fun states(entityIds: List<String>): Result<Map<String, Entity>> = apiCall {
        coroutineScope {
            entityIds.map { id ->
                async {
                    val response = client.get("$address/api/states/${id.encodeURLPathPart()}") { authorize() }
                    if (response.status == HttpStatusCode.NotFound) return@async null
                    checkStatus(response)
                    parseStates(response.bodyAsText()).firstOrNull()
                }
            }.awaitAll().filterNotNull().associateBy { it.id }
        }
    }

    /**
     * Runs [call]. Returns the entity's new state if Home Assistant reported it straight away
     * (it lists the states that changed while the action ran), or null if it didn't.
     */
    suspend fun call(call: ServiceCall): Result<Entity?> = apiCall {
        val response = client.post("$address/api/services/${call.domain}/${call.service}") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("entity_id", call.entityId) }.toString())
        }
        if (response.status == HttpStatusCode.NotFound) throw EntityMissingException(call.entityId)
        checkStatus(response)
        runCatching { parseStates(response.bodyAsText()) }.getOrDefault(emptyList())
            .firstOrNull { it.id == call.entityId }
    }

    private fun HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun checkStatus(response: HttpResponse) {
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            throw NotAuthorizedException()
        }
        if (!response.status.isSuccess()) throw HomeAssistantHttpException(response.status.value)
    }

    /** Like runCatching, but lets coroutine cancellation through. */
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** One HTTP client for the whole tool. */
        private val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 20_000
            }
            defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
            expectSuccess = false
        }
    }
}
