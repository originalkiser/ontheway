package com.reststop.countdown

import android.app.Application
import com.reststop.countdown.di.AppContainer

class RestCountdownApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
