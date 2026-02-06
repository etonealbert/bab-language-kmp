package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val nativeLanguage: LanguageCode,
    val targetLanguages: List<TargetLanguage>,
    val currentTargetLanguage: LanguageCode,
    val interests: Set<Interest>,
    val learningGoals: Set<LearningGoal>,
    val dailyGoalMinutes: Int,
    val voiceSpeed: VoiceSpeed,
    val showTranslations: TranslationMode,
    val onboardingCompleted: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long
)

@Serializable
data class TargetLanguage(
    val code: LanguageCode,
    val proficiencyLevel: CEFRLevel,
    val startedAt: Long
)

typealias LanguageCode = String

@Serializable
enum class CEFRLevel { A1, A2, B1, B2, C1, C2 }

@Serializable
enum class Interest {
    TRAVEL, BUSINESS, ROMANCE, SCI_FI, EVERYDAY,
    FOOD, CULTURE, SPORTS, MUSIC, MOVIES
}

@Serializable
enum class LearningGoal {
    CONVERSATION, READING, LISTENING, EXAM_PREP, WORK, TRAVEL
}

@Serializable
enum class VoiceSpeed { SLOW, NORMAL, FAST }

@Serializable
enum class TranslationMode { ALWAYS, ON_TAP, NEVER }
