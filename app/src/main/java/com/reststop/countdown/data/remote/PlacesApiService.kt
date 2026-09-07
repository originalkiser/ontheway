package com.reststop.countdown.data.remote

import com.reststop.countdown.data.remote.dto.NearbySearchRequestDto
import com.reststop.countdown.data.remote.dto.NearbySearchResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Places API (New). Nearby Search is used, sampled at a handful of points along the route,
 * to find rest stops and optional points of interest. Every call site in the repository is
 * invoked exactly once during trip setup - never repeated during live GPS tracking.
 */
interface PlacesApiService {

    @Headers("Content-Type: application/json")
    @POST("v1/places:searchNearby")
    suspend fun searchNearby(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "places.id,places.displayName,places.location",
        @Body request: NearbySearchRequestDto,
    ): NearbySearchResponseDto
}
