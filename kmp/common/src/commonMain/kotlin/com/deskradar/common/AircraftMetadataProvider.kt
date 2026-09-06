package com.deskradar.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Registration/model info OpenSky's states endpoint doesn't provide (see AircraftMetadataProvider). */
data class AircraftMetadata(
    val registration: String?,
    val model: String?,
    val operator: String?
)

/**
 * Looks up registration/model/operator by icao24 — data OpenSky's `/api/states/all` doesn't
 * include at all (see docs/design.md's OpenSky-coverage/limitations notes). Backed by adsbdb.com,
 * a free public API with no key required; results are cached in-memory per icao24 so repeat taps
 * on the same aircraft across polls don't re-hit the network.
 */
class AircraftMetadataProvider(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
        }
    }
) {
    private val cache = mutableMapOf<String, AircraftMetadata?>()
    private val cacheMutex = Mutex()

    suspend fun lookup(icao24: String): AircraftMetadata? {
        val key = icao24.lowercase()
        cacheMutex.withLock {
            if (cache.containsKey(key)) return cache[key]
        }

        val result = try {
            val response: HttpResponse = httpClient.get("https://api.adsbdb.com/v0/aircraft/$key")
            if (response.status != HttpStatusCode.OK) {
                null
            } else {
                val body: AdsbDbResponse = response.body()
                body.response.aircraft?.let {
                    AircraftMetadata(registration = it.registration, model = it.type, operator = it.registeredOwner)
                }
            }
        } catch (e: Exception) {
            null
        }

        cacheMutex.withLock { cache[key] = result }
        return result
    }

    fun close() = httpClient.close()
}

@Serializable
private data class AdsbDbResponse(val response: AdsbDbResponseBody)

@Serializable
private data class AdsbDbResponseBody(val aircraft: AdsbDbAircraft? = null)

@Serializable
private data class AdsbDbAircraft(
    val type: String? = null,
    val registration: String? = null,
    @kotlinx.serialization.SerialName("registered_owner") val registeredOwner: String? = null
)
