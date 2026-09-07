package com.reststop.countdown.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit clients used by [com.reststop.countdown.data.repository.TripRepositoryImpl]. */
object NetworkModule {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val directionsApi: DirectionsApiService by lazy {
        retrofit("https://maps.googleapis.com/").create(DirectionsApiService::class.java)
    }

    val placesApi: PlacesApiService by lazy {
        retrofit("https://places.googleapis.com/").create(PlacesApiService::class.java)
    }
}
