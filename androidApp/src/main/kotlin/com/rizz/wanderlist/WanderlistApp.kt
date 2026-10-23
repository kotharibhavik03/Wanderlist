package com.rizz.wanderlist

import android.app.Application
import com.rizz.wanderlist.di.initAndroidApp

class WanderlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initAndroidApp(this)
    }
}