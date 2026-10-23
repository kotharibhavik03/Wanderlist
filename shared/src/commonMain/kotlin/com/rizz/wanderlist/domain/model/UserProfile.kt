package com.rizz.wanderlist.domain.model

/**
 * The public part of a user, looked up by email so people on a shared place can see each
 * other's names. Each user publishes their own when they sign in.
 */
data class UserProfile(
    val email: String,
    val name: String?,
    val photoUrl: String?
)
