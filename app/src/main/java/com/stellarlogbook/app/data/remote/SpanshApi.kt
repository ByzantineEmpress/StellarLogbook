package com.stellarlogbook.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/** Spansh endpoints verified in Phase 0 (2026-11-03). */
interface SpanshApi {

    /** Full system record incl. bodies with estimated scan/mapping values. */
    @GET("api/system/{id64}")
    suspend fun system(@Path("id64") id64: Long): SpanshSystemResponse
}
