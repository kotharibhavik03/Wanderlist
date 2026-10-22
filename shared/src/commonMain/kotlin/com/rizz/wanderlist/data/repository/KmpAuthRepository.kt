package com.rizz.wanderlist.data.repository

import com.mmk.kmpauth.core.KMPAuth
import com.mmk.kmpauth.core.auth.KMPAuthUser
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class KmpAuthRepository : AuthRepository {

    override val currentUser: Flow<User?>
        get() = KMPAuth.currentUserFlow.map { it?.toDomain() }

    override suspend fun signOut() {
        KMPAuth.signOut()
    }
}

private fun KMPAuthUser.toDomain() = User(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl
)