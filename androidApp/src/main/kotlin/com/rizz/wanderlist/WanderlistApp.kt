package com.rizz.wanderlist

import android.app.Application
import com.rizz.wanderlist.di.initApp

class WanderlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initApp()
    }
}