package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the signed-in user, or null when signed out. Emits again on every change. */
    val currentUser: Flow<User?>

    suspend fun signOut()
}