package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Publishes the signed-in [user]'s name and photo under their email. */
    suspend fun saveMyProfile(user: User)

    /**
     * Profiles for the given emails, keyed by lowercase email. An email with no profile
     * (that person hasn't signed in yet) is simply missing from the map.
     */
    fun observeProfiles(emails: Set<String>): Flow<Map<String, UserProfile>>
}
