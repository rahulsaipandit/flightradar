package com.deskradar.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

sealed class OpenSkyResult {
    data class Success(val aircraft: List<Aircraft>) : OpenSkyResult()
    /** OpenSky rate-limited us (HTTP 429) — caller should back off harder than a normal failure. */
    object RateLimited : OpenSkyResult()
    data class Failure(val message: String) : OpenSkyResult()
}

/**
 * Guest-mode (unauthenticated) OpenSky client — no client credentials, no OAuth token refresh.
 * Ports the request shape from fetchAndMapFlights() in firmware/ESP32Radar/ESP32Radar.ino,
 * minus the authenticated branch (see docs/design.md: OAuth mode is a deliberate fast-follow).
 */
class OpenSkyApiClient(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            // Without this, a stalled connection would hang RadarRepository's poll loop forever.
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
        }
    }
) {
    suspend fun fetchStates(box: BoundingBox): OpenSkyResult {
        val response: HttpResponse = try {
            httpClient.get("https://opensky-network.org/api/states/all") {
                parameter("lamin", box.latMin)
                parameter("lomin", box.lonMin)
                parameter("lamax", box.latMax)
                parameter("lomax", box.lonMax)
            }
        } catch (e: Exception) {
            return OpenSkyResult.Failure(e.message ?: "network error")
        }

        return when (response.status) {
            HttpStatusCode.OK -> try {
                val body: OpenSkyStatesResponse = response.body()
                // mapNotNull runs inside this try too — one malformed record must not kill
                // the whole poll loop (an uncaught exception here would propagate out of
                // RadarRepository's flow and stop it updating permanently).
                OpenSkyResult.Success(body.states.orEmpty().mapNotNull { it.toAircraftOrNull() })
            } catch (e: Exception) {
                OpenSkyResult.Failure(e.message ?: "parse error")
            }
            HttpStatusCode.TooManyRequests -> OpenSkyResult.RateLimited
            else -> OpenSkyResult.Failure("HTTP ${response.status.value}")
        }
    }

    fun close() = httpClient.close()
}
