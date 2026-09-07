package com.reststop.countdown.data.remote

import com.reststop.countdown.data.remote.dto.DirectionsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Thin wrapper over the (legacy, GET-based) Directions API - called exactly once per trip. */
interface DirectionsApiService {

    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("alternatives") alternatives: Boolean = true,
    ): DirectionsResponseDto
}
