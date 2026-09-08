package com.reststop.countdown

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import com.reststop.countdown.di.AppContainer

class RestCountdownApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // BitmapDescriptorFactory (used for destination/POI map markers) throws
        // IllegalStateException if called before the Maps SDK has initialized a native map view -
        // which can easily happen from Compose, since a remember{} block can run before a sibling
        // GoogleMap composable further down the tree has attached. Initializing explicitly here
        // makes it safe to call from anywhere, regardless of composition order.
        MapsInitializer.initialize(applicationContext)
    }
}
