package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.UserProfile

interface UserProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
    suspend fun updateOnboardingComplete()
    suspend fun updateLastActive()
    suspend fun clear()
}
