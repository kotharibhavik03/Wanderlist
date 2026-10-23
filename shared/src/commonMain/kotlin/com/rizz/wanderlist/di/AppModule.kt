package com.rizz.wanderlist.di

import com.rizz.wanderlist.data.firestore.FirestoreCollectionRepository
import com.rizz.wanderlist.data.firestore.FirestorePlaceRepository
import com.rizz.wanderlist.data.firestore.FirestoreProfileRepository
import com.rizz.wanderlist.data.repository.KmpAuthRepository
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.domain.usecase.CreateCollectionUseCase
import com.rizz.wanderlist.domain.usecase.DeleteCollectionUseCase
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SavePlaceUseCase
import com.rizz.wanderlist.domain.usecase.SearchLocationsUseCase
import com.rizz.wanderlist.domain.usecase.UpdatePlaceUseCase
import com.rizz.wanderlist.views.auth.AuthViewModel
import com.rizz.wanderlist.views.auth.SignInViewModel
import com.rizz.wanderlist.views.collection.CollectionDetailViewModel
import com.rizz.wanderlist.views.collection.CollectionsViewModel
import com.rizz.wanderlist.views.collection.CreateCollectionViewModel
import com.rizz.wanderlist.views.home.HomeViewModel
import com.rizz.wanderlist.views.mapPicker.MapPickerViewModel
import com.rizz.wanderlist.views.place.AddPlaceViewModel
import com.rizz.wanderlist.views.place.PlaceDetailViewModel
import com.rizz.wanderlist.views.profile.ProfileViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { Firebase.firestore }
    single<CollectionRepository> { FirestoreCollectionRepository(get(), get()) }
    factory { CreateCollectionUseCase(get()) }
    factory { DeleteCollectionUseCase(get(), get()) }
    factory { ObserveCollectionSummariesUseCase(get(), get()) }
    viewModel { CollectionsViewModel(get(), get(), get()) }
    viewModel { CreateCollectionViewModel(get()) }

    single<AuthRepository> { KmpAuthRepository() }
    single<ProfileRepository> { FirestoreProfileRepository(get()) }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ProfileViewModel(get()) }
    factory { SearchLocationsUseCase(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { MapPickerViewModel(get(), get()) }
    viewModel { SignInViewModel() }

    viewModel { AddPlaceViewModel(get(), get(), get(), get(), get()) }

    single<PlaceRepository> { FirestorePlaceRepository(get(), get()) }
    factory { SavePlaceUseCase(get()) }
    factory { UpdatePlaceUseCase(get()) }
    viewModel { (collectionId: String) -> CollectionDetailViewModel(collectionId, get(), get(), get()) }
    viewModel { (placeId: String) -> PlaceDetailViewModel(placeId, get(), get(), get(), get()) }

}