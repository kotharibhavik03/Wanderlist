package com.rizz.wanderlist

import android.app.Application
import com.rizz.wanderlist.di.initKoin

class WanderlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}