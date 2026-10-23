package com.rizz.wanderlist.di

import com.mmk.kmpauth.core.KMPAuth
import com.mmk.kmpauth.google.google
import com.rizz.wanderlist.config.AuthConfig
import org.koin.core.context.startKoin
import org.koin.core.module.Module

private var initialized = false

/**
 * Starts Koin and KMPAuth once. Safe to call repeatedly. [platformModule] provides the
 * platform's own services (androidPlatformModule / iosPlatformModule).
 */
fun initApp(platformModule: Module) {
    if (initialized) return
    initialized = true

    startKoin { modules(appModule, platformModule) }
    KMPAuth.initialize {
        google(serverId = AuthConfig.GOOGLE_WEB_CLIENT_ID)
    }
}