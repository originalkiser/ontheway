package com.reststop.countdown.data.remote

import com.reststop.countdown.data.remote.dto.DirectionsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Thin wrapper over the (legacy, GET-based) Directions API - called exactly once per trip setup, plus once more if the driver sets or clears a secondary destination. */
interface DirectionsApiService {

    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("alternatives") alternatives: Boolean = true,
        // Google doesn't support route alternatives alongside waypoints, so this is only ever
        // set together with alternatives=false.
        @Query("waypoints") waypoints: String? = null,
    ): DirectionsResponseDto
}
