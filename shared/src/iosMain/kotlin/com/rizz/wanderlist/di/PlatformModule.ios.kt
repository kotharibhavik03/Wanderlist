package com.rizz.wanderlist.di

import com.rizz.wanderlist.data.search.IosPlaceSearch
import com.rizz.wanderlist.domain.repository.PlaceSearch
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS implementations of the app's platform services. */
val iosPlatformModule: Module = module {
    single<PlaceSearch> { IosPlaceSearch() }
}
