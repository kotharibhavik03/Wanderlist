package com.rizz.wanderlist.di

import com.rizz.wanderlist.data.repository.InMemoryCollectionRepository
import com.rizz.wanderlist.data.repository.KmpAuthRepository
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.usecase.CreateCollectionUseCase
import com.rizz.wanderlist.views.auth.AuthViewModel
import com.rizz.wanderlist.views.auth.SignInViewModel
import com.rizz.wanderlist.views.collection.CollectionsViewModel
import com.rizz.wanderlist.views.collection.CreateCollectionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CollectionRepository> { InMemoryCollectionRepository() }
    factory { CreateCollectionUseCase(get()) }
    viewModel { CollectionsViewModel(get()) }
    viewModel { CreateCollectionViewModel(get()) }

    single<AuthRepository> { KmpAuthRepository() }
    viewModel { AuthViewModel(get()) }
    viewModel { SignInViewModel() }
}