package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.UserProfileRepository
import com.bablabs.bringabrainlanguage.domain.models.UserProfile
import kotlinx.datetime.Clock

class InMemoryUserProfileRepository : UserProfileRepository {
    
    private var profile: UserProfile? = null
    
    override suspend fun getProfile(): UserProfile? = profile
    
    override suspend fun saveProfile(profile: UserProfile) {
        this.profile = profile
    }
    
    override suspend fun updateOnboardingComplete() {
        profile = profile?.copy(onboardingCompleted = true)
    }
    
    override suspend fun updateLastActive() {
        profile = profile?.copy(lastActiveAt = Clock.System.now().toEpochMilliseconds())
    }
    
    override suspend fun clear() {
        profile = null
    }
}
