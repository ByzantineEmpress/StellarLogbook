package com.stellarlogbook.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * EDSM endpoints verified in Phase 0 (2026-11-03).
 * - api-v1/system, api-v1/sphere-systems: public, no key needed.
 * - api-logs-v1 endpoints: require YOUR commander name + API key.
 * Respect EDSM rate limits: paginate, back off on 429/503, cache aggressively,
 * and set a real User-Agent (done in AppContainer).
 */
interface EdsmApi {

    @GET("api-v1/system")
    suspend fun system(
        @Query("systemName") systemName: String,
        @Query("showCoordinates") showCoordinates: Int = 1,
        @Query("showInformation") showInformation: Int = 1,
        @Query("showPrimaryStar") showPrimaryStar: Int = 1,
        @Query("showPermit") showPermit: Int = 1,
        @Query("showId") showId: Int = 1
    ): SystemInfoDto

    @GET("api-v1/sphere-systems")
    suspend fun sphereSystems(
        @Query("systemName") systemName: String? = null,
        @Query("x") x: Double? = null,
        @Query("y") y: Double? = null,
        @Query("z") z: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("minRadius") minRadius: Int? = null,
        @Query("showCoordinates") showCoordinates: Int = 1,
        @Query("showPrimaryStar") showPrimaryStar: Int = 1,
        @Query("showId") showId: Int = 1
    ): List<SphereSystemDto>

    @GET("api-logs-v1/get-logs")
    suspend fun logs(
        @Query("commanderName") commanderName: String,
        @Query("apiKey") apiKey: String,
        @Query("startDateTime") startDateTime: String,
        @Query("endDateTime") endDateTime: String
    ): EdsmLogsResponse

    @GET("api-logs-v1/get-position")
    suspend fun position(
        @Query("commanderName") commanderName: String,
        @Query("apiKey") apiKey: String
    ): EdsmPositionResponse
}
