package com.rizz.wanderlist.di

import android.content.Context
import com.rizz.wanderlist.data.location.AndroidLocationService
import com.rizz.wanderlist.data.photo.AndroidPhotoCompressor
import com.rizz.wanderlist.data.search.AndroidPlaceSearch
import com.rizz.wanderlist.domain.repository.LocationService
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PlaceSearch
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android implementations of the app's platform services. Needs the application context. */
internal fun androidPlatformModule(context: Context): Module = module {
    single<PlaceSearch> { AndroidPlaceSearch(context.applicationContext) }
    single<LocationService> { AndroidLocationService(context.applicationContext) }
    single<PhotoCompressor> { AndroidPhotoCompressor(context.applicationContext) }
}

/** Android's start-up entry point. Keeps Koin an implementation detail of the shared module. */
fun initAndroidApp(context: Context) = initApp(androidPlatformModule(context))
