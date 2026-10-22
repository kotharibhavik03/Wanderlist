package com.rizz.wanderlist.di

import com.mmk.kmpauth.core.KMPAuth
import com.mmk.kmpauth.google.google
import com.rizz.wanderlist.config.AuthConfig
import org.koin.core.context.startKoin

private var initialized = false

/** Starts Koin and KMPAuth once. Safe to call repeatedly. */
fun initApp() {
    if (initialized) return
    initialized = true

    startKoin { modules(appModule) }
    KMPAuth.initialize {
        google(serverId = AuthConfig.GOOGLE_WEB_CLIENT_ID)
    }
}