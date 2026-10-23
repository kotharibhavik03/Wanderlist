package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /**
     * Publishes the signed-in [user]'s name and photo under their email. A photo the user
     * uploaded themselves is kept; otherwise the account's (Google) photo is used.
     */
    suspend fun saveMyProfile(user: User)

    /**
     * Profiles for the given emails, keyed by lowercase email. An email with no profile
     * (that person hasn't signed in yet) is simply missing from the map.
     */
    fun observeProfiles(emails: Set<String>): Flow<Map<String, UserProfile>>

    /** Uploads the compressed JPEG at [localPath] and makes it the signed-in user's photo. */
    suspend fun setMyPhoto(localPath: String)

    /** Goes back to the account's (Google) photo, deleting the uploaded one. */
    suspend fun useAccountPhoto()
}
