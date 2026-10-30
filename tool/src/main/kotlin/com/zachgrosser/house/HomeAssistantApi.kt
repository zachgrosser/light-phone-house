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

// Home Assistant REST API: developers.home-assistant.io/docs/api/rest/

private val USER_AGENT = "LightPhoneHouse/${BuildConfig.VERSION_NAME}"

class NotAuthorizedException : Exception("Home Assistant didn't accept the access token")

class EntityMissingException(val entityId: String) : Exception("No entity called $entityId")

class HomeAssistantHttpException(val status: Int) : Exception("Home Assistant answered HTTP $status")

fun errorMessage(error: Throwable): String = when (error) {
    is NotAuthorizedException ->
        "Home Assistant didn't accept the access token. Make a new one and add it in Settings."
    is EntityMissingException -> "Home Assistant no longer has ${error.entityId}. Remove it in Edit."
    is HomeAssistantHttpException -> "Home Assistant answered with an error (${error.status})."
    is IOException -> "Couldn't reach Home Assistant. Check the phone is online and the address in Settings."
    else -> "Something went wrong talking to Home Assistant."
}

data class ServerInfo(val locationName: String?, val version: String?)

class HomeAssistantApi(private val address: String, private val token: String) {

    suspend fun serverInfo(): Result<ServerInfo> = apiCall {
        val response = client.get("$address/api/config") { authorize() }
        checkStatus(response)
        val config = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        ServerInfo(
            locationName = (config?.get("location_name") as? JsonPrimitive)?.contentOrNull,
            version = (config?.get("version") as? JsonPrimitive)?.contentOrNull,
        )
    }

    suspend fun supportedEntities(): Result<List<Entity>> = apiCall {
        val response = client.get("$address/api/states") { authorize() }
        checkStatus(response)
        parseStates(response.bodyAsText()).filter { it.kind != null }
    }

    // One request per shortcut instead of /api/states, which can be huge in a big house.
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

    // Returns the entity's new state if Home Assistant included it in the response.
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

    // Like runCatching, but doesn't swallow cancellation.
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

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
