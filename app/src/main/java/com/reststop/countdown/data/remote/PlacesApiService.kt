package com.reststop.countdown.data.remote

import com.reststop.countdown.data.remote.dto.NearbySearchRequestDto
import com.reststop.countdown.data.remote.dto.NearbySearchResponseDto
import com.reststop.countdown.data.remote.dto.TextSearchRequestDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Places API (New). Nearby Search is sampled at a handful of points along the route to find
 * rest areas and optional points of interest - invoked exactly once during trip setup, never
 * repeated during live GPS tracking. Text Search is the one exception: it's called as the user
 * types a destination, debounced in the ViewModel to keep the request count sane, and (unlike
 * Autocomplete) returns each result's location directly so it can be pinned on the map.
 */
interface PlacesApiService {

    @Headers("Content-Type: application/json")
    @POST("v1/places:searchNearby")
    suspend fun searchNearby(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "places.id,places.displayName,places.location",
        @Body request: NearbySearchRequestDto,
    ): NearbySearchResponseDto

    @Headers("Content-Type: application/json")
    @POST("v1/places:searchText")
    suspend fun searchText(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "places.id,places.displayName,places.formattedAddress,places.location",
        @Body request: TextSearchRequestDto,
    ): NearbySearchResponseDto
}
