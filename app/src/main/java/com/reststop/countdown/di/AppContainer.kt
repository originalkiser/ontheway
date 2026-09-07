package com.reststop.countdown.di

import android.content.Context
import com.google.android.gms.location.LocationServices
import com.reststop.countdown.BuildConfig
import com.reststop.countdown.data.remote.NetworkModule
import com.reststop.countdown.data.repository.TripRepositoryImpl
import com.reststop.countdown.domain.repository.TripRepository
import com.reststop.countdown.location.LocationTracker

/**
 * Minimal hand-rolled dependency container (no Hilt/Dagger) so the whole build stays plain
 * Kotlin + AGP - fewer moving parts to go wrong, while still keeping data/domain/UI cleanly
 * separated and swappable (e.g. for tests) behind the [TripRepository] interface.
 */
class AppContainer(context: Context) {

    val locationTracker: LocationTracker by lazy {
        LocationTracker(LocationServices.getFusedLocationProviderClient(context))
    }

    val tripRepository: TripRepository by lazy {
        TripRepositoryImpl(
            directionsApi = NetworkModule.directionsApi,
            placesApi = NetworkModule.placesApi,
            apiKey = BuildConfig.MAPS_API_KEY,
        )
    }
}
