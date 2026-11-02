package com.rodviks.app

import android.app.Application
import com.rodviks.app.di.ServiceLocator

class RodviksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
