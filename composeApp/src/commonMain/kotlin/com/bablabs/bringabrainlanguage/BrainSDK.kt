package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogHistoryRepository
import com.bablabs.bringabrainlanguage.domain.interfaces.ProgressRepository
import com.bablabs.bringabrainlanguage.domain.interfaces.SavedSession
import com.bablabs.bringabrainlanguage.domain.interfaces.UserProfileRepository
import com.bablabs.bringabrainlanguage.domain.interfaces.VocabularyRepository
import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.domain.services.SRSScheduler
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.AICapabilities
import com.bablabs.bringabrainlanguage.infrastructure.ai.DeviceCapabilities
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import com.bablabs.bringabrainlanguage.infrastructure.network.ble.DiscoveredDevice
import com.bablabs.bringabrainlanguage.infrastructure.network.ble.createBleScanner
import com.bablabs.bringabrainlanguage.infrastructure.repositories.InMemoryDialogHistoryRepository
import com.bablabs.bringabrainlanguage.infrastructure.repositories.InMemoryProgressRepository
import com.bablabs.bringabrainlanguage.infrastructure.repositories.InMemoryUserProfileRepository
import com.bablabs.bringabrainlanguage.infrastructure.repositories.InMemoryVocabularyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

class BrainSDK(
    aiProvider: AIProvider? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    private val userProfileRepository: UserProfileRepository = InMemoryUserProfileRepository(),
    private val vocabularyRepository: VocabularyRepository = InMemoryVocabularyRepository(),
    private val progressRepository: ProgressRepository = InMemoryProgressRepository(),
    private val dialogHistoryRepository: DialogHistoryRepository = InMemoryDialogHistoryRepository()
) {
    
    /**
     * Convenience constructor for iOS/Swift.
     * 
     * Kotlin default parameter values are not exposed as separate Swift initializers.
     * This secondary constructor enables `BrainSDK()` in Swift without DI boilerplate.
     * 
     * Usage (Swift):
     * ```swift
     * let sdk = BrainSDK()
     * ```
     */
    constructor() : this(
        aiProvider = null,
        coroutineContext = Dispatchers.Default,
        userProfileRepository = InMemoryUserProfileRepository(),
        vocabularyRepository = InMemoryVocabularyRepository(),
        progressRepository = InMemoryProgressRepository(),
        dialogHistoryRepository = InMemoryDialogHistoryRepository()
    )
    
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    
    private val networkSession = LoopbackNetworkSession(
        localPeerId = generatePeerId()
    )
    
    private val resolvedAIProvider: AIProvider = aiProvider 
        ?: DeviceCapabilities.getBestAvailableProvider()
    
    private val dialogStore = DialogStore(
        networkSession = networkSession,
        aiProvider = resolvedAIProvider,
        coroutineContext = coroutineContext
    )
    
    private val bleScanner by lazy { createBleScanner() }
    
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    private val _vocabularyStats = MutableStateFlow(VocabularyStats(0, 0, 0, 0, 0, 0))
    private val _dueReviews = MutableStateFlow<List<VocabularyEntry>>(emptyList())
    private val _progress = MutableStateFlow<UserProgress?>(null)
    
    val state: StateFlow<SessionState> = dialogStore.state
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    val vocabularyStats: StateFlow<VocabularyStats> = _vocabularyStats.asStateFlow()
    val dueReviews: StateFlow<List<VocabularyEntry>> = _dueReviews.asStateFlow()
    val progress: StateFlow<UserProgress?> = _progress.asStateFlow()
    
    val aiCapabilities: AICapabilities
        get() = DeviceCapabilities.check()
    
    init {
        scope.launch { loadInitialData() }
    }
    
    private suspend fun loadInitialData() {
        _userProfile.value = userProfileRepository.getProfile()
        _userProfile.value?.let { profile ->
            refreshVocabularyStats(profile.currentTargetLanguage)
            _progress.value = progressRepository.getProgress(profile.currentTargetLanguage)
        }
    }
    
    private suspend fun refreshVocabularyStats(language: LanguageCode) {
        _vocabularyStats.value = vocabularyRepository.getStats(language)
        _dueReviews.value = vocabularyRepository.getDueForReview(language)
    }

    // ==================== ONBOARDING ====================
    
    suspend fun completeOnboarding(profile: UserProfile) {
        userProfileRepository.saveProfile(profile)
        userProfileRepository.updateOnboardingComplete()
        _userProfile.value = profile
        refreshVocabularyStats(profile.currentTargetLanguage)
    }
    
    fun isOnboardingRequired(): Boolean {
        return _userProfile.value == null || _userProfile.value?.onboardingCompleted == false
    }
    
    suspend fun updateProfile(update: (UserProfile) -> UserProfile) {
        val current = _userProfile.value ?: return
        val updated = update(current)
        userProfileRepository.saveProfile(updated)
        _userProfile.value = updated
        if (current.currentTargetLanguage != updated.currentTargetLanguage) {
            refreshVocabularyStats(updated.currentTargetLanguage)
        }
    }

    // ==================== VOCABULARY ====================
    
    suspend fun getVocabularyForReview(limit: Int = 20): List<VocabularyEntry> {
        val language = _userProfile.value?.currentTargetLanguage ?: return emptyList()
        return vocabularyRepository.getDueForReview(language, limit)
    }
    
    suspend fun recordVocabularyReview(entryId: String, quality: ReviewQuality) {
        val entry = vocabularyRepository.getById(entryId) ?: return
        val updated = SRSScheduler.calculateNextReview(entry, quality)
        vocabularyRepository.upsert(updated)
        _userProfile.value?.currentTargetLanguage?.let { refreshVocabularyStats(it) }
    }
    
    suspend fun addToVocabulary(entry: VocabularyEntry) {
        vocabularyRepository.upsert(entry)
        _userProfile.value?.currentTargetLanguage?.let { refreshVocabularyStats(it) }
    }
    
    suspend fun createVocabularyEntry(
        word: String,
        translation: String,
        language: LanguageCode? = null
    ): VocabularyEntry {
        val lang = language ?: _userProfile.value?.currentTargetLanguage ?: "es"
        val dialogId = state.value.scenario?.id
        return SRSScheduler.createNewEntry(word, translation, lang, dialogId)
    }

    // ==================== PROGRESS ====================
    
    suspend fun getProgress(): UserProgress? {
        val language = _userProfile.value?.currentTargetLanguage ?: return null
        return progressRepository.getProgress(language)
    }
    
    suspend fun getSessionHistory(limit: Int = 10): List<SavedSession> {
        return dialogHistoryRepository.getSessions(limit)
    }

    // ==================== PEDAGOGICAL FEATURES ====================
    
    fun requestHint(level: HintLevel = HintLevel.STARTER_WORDS) {
        dialogStore.accept(DialogStore.Intent.RequestHint(level))
    }
    
    fun triggerPlotTwist(description: String) {
        dialogStore.accept(DialogStore.Intent.TriggerPlotTwist(description))
    }
    
    fun setSecretObjective(playerId: String, objective: String) {
        dialogStore.accept(DialogStore.Intent.SetSecretObjective(playerId, objective))
    }

    // ==================== GAME LIFECYCLE ====================
    
    fun startSoloGame(scenarioId: String, userRoleId: String) {
        val scenario = getScenarioById(scenarioId)
        val role = scenario.availableRoles.find { it.id == userRoleId }
            ?: scenario.availableRoles.first()
        
        dialogStore.accept(DialogStore.Intent.StartSoloGame(
            scenario = scenario,
            userRole = role
        ))
        
        scope.launch { userProfileRepository.updateLastActive() }
    }
    
    fun hostGame(scenarioId: String, userRoleId: String) {
        val scenario = getScenarioById(scenarioId)
        val role = scenario.availableRoles.find { it.id == userRoleId }
            ?: scenario.availableRoles.first()
        
        dialogStore.accept(DialogStore.Intent.HostGame(
            scenario = scenario,
            userRole = role
        ))
        
        scope.launch { userProfileRepository.updateLastActive() }
    }
    
    fun scanForHosts(): Flow<DiscoveredDevice> {
        return bleScanner.scan()
    }
    
    fun joinGame(hostDeviceId: String, userRoleId: String) {
        val defaultRole = Role("guest", "Guest", "Joining player")
        
        dialogStore.accept(DialogStore.Intent.JoinGame(
            hostDeviceId = hostDeviceId,
            userRole = defaultRole
        ))
        
        scope.launch { userProfileRepository.updateLastActive() }
    }
    
    fun generate() {
        dialogStore.accept(DialogStore.Intent.Generate)
    }
    
    fun leaveGame() {
        dialogStore.accept(DialogStore.Intent.LeaveGame)
    }
    
    suspend fun endSession() {
        dialogStore.accept(DialogStore.Intent.EndSession)
        dialogHistoryRepository.saveSession(state.value, state.value.sessionStats)
    }
    
    fun getAvailableScenarios(): List<Scenario> {
        return listOf(
            getScenarioById("coffee-shop"),
            getScenarioById("the-heist"),
            getScenarioById("first-date")
        )
    }
    
    private fun getScenarioById(id: String): Scenario {
        return when (id) {
            "coffee-shop" -> Scenario(
                id = "coffee-shop",
                name = "Ordering Coffee",
                description = "Practice ordering at a Spanish cafe",
                availableRoles = listOf(
                    Role("barista", "Barista", "The coffee shop worker"),
                    Role("customer", "Customer", "The person ordering")
                )
            )
            "the-heist" -> Scenario(
                id = "the-heist",
                name = "The Heist",
                description = "A dramatic crime scene unfolds",
                availableRoles = listOf(
                    Role("detective", "Detective", "The investigator"),
                    Role("thief", "Thief", "The suspect")
                )
            )
            else -> Scenario(
                id = "first-date",
                name = "First Date",
                description = "A romantic dinner conversation",
                availableRoles = listOf(
                    Role("date1", "Alex", "First person"),
                    Role("date2", "Sam", "Second person")
                )
            )
        }
    }
    
    private fun generatePeerId(): String {
        return "player-${Random.nextInt(10000, 99999)}"
    }
}
