package com.reststop.countdown.data.remote

import com.reststop.countdown.data.remote.dto.AutocompleteRequestDto
import com.reststop.countdown.data.remote.dto.AutocompleteResponseDto
import com.reststop.countdown.data.remote.dto.NearbySearchRequestDto
import com.reststop.countdown.data.remote.dto.NearbySearchResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Places API (New). Nearby Search is used, sampled at a handful of points along the route,
 * to find rest stops and optional points of interest - invoked exactly once during trip setup,
 * never repeated during live GPS tracking. Autocomplete is the one exception: it's called as
 * the user types a destination, debounced in the ViewModel to keep the request count sane.
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
    @POST("v1/places:autocomplete")
    suspend fun autocomplete(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat",
        @Body request: AutocompleteRequestDto,
    ): AutocompleteResponseDto
}
