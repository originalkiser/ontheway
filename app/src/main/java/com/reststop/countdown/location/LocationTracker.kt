package com.reststop.countdown.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around [FusedLocationProviderClient]. This is the ONLY source of real-time
 * position updates in the app - the countdown, auto-advance, and POI proximity logic all run
 * off the `Location` objects this class emits, with no further Google API calls.
 */
class LocationTracker(private val fusedLocationClient: FusedLocationProviderClient) {

    /** One-shot high-accuracy fix, used only to seed the Directions API call's "origin". */
    @SuppressLint("MissingPermission")
    suspend fun awaitCurrentLocation(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        return fusedLocationClient.getCurrentLocation(request, null).await()
            ?: fusedLocationClient.lastLocation.await()
    }

    /**
     * A live stream of GPS fixes for hands-free tracking. The Flow is only "hot" while
     * collected, and location updates are automatically removed when the collector cancels
     * (e.g. the trip ends, or the ViewModel is cleared) - the caller never has to manage the
     * callback lifecycle manually.
     */
    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(
        intervalMillis: Long = 3_000L,
        minUpdateDistanceMeters: Float = 15f,
    ): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }
}
