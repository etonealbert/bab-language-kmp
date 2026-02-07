# Word Translation & Vocabulary SDK Guide

**Last Updated**: February 2026  
**SDK Version**: Phase 4 (Language Learning Features)

---

## Overview

This guide documents the word translation and vocabulary management capabilities of the BringABrain SDK. These features enable:

- Context-aware word translation
- Vocabulary saving with spaced repetition (SRS)
- Translation caching for offline access
- Progress tracking and mastery levels

---

## Quick Start

### iOS (Swift)

```swift
import BabLanguageSDK

let sdk = BrainSDK()

// Translate a word
Task {
    let translation = try await sdk.translateWord(
        word: "café",
        sentenceContext: "Quiero un café, por favor"
    )
    print("\(translation.word) = \(translation.translation)")
    print("Part of speech: \(translation.partOfSpeech?.name ?? "unknown")")
}

// Add to vocabulary
Task {
    let entry = try await sdk.addToVocabulary(
        word: "perro",
        translation: "dog",
        partOfSpeech: .noun,
        exampleSentence: "El perro es grande"
    )
    print("Added: \(entry.word)")
}

// Check if word is in vocabulary
if sdk.isInVocabulary(word: "café") {
    print("Already saved!")
}
```

### Android (Kotlin)

```kotlin
val sdk = BrainSDK()

// Translate a word
lifecycleScope.launch {
    val translation = sdk.translateWord(
        word = "café",
        sentenceContext = "Quiero un café, por favor"
    )
    println("${translation.word} = ${translation.translation}")
}

// Add to vocabulary
lifecycleScope.launch {
    val entry = sdk.addToVocabulary(
        word = "perro",
        translation = "dog",
        partOfSpeech = PartOfSpeech.NOUN,
        exampleSentence = "El perro es grande"
    )
}

// Observe vocabulary
sdk.vocabularyEntries.collect { entries ->
    updateVocabularyList(entries)
}
```

---

## Translation API

### translateWord

Translates a single word with context awareness for disambiguation.

```kotlin
suspend fun translateWord(
    word: String,
    sentenceContext: String,
    sourceLanguage: LanguageCode? = null,  // defaults to profile's target language
    targetLanguage: LanguageCode? = null   // defaults to profile's native language
): WordTranslation
```

**Parameters:**
- `word`: The word to translate (e.g., "café")
- `sentenceContext`: Surrounding sentence for disambiguation (e.g., "Quiero un café")
- `sourceLanguage`: Source language code (optional, uses profile default)
- `targetLanguage`: Target language code (optional, uses profile default)

**Returns:** `WordTranslation` with rich metadata

### WordTranslation Model

```kotlin
data class WordTranslation(
    val word: String,                    // "café"
    val translation: String,             // "coffee"
    val partOfSpeech: PartOfSpeech?,     // NOUN, VERB, etc.
    val phoneticSpelling: String?,       // "ka-FEH"
    val audioUrl: String?,               // pronunciation audio
    val definitions: List<String>,       // ["A hot drink...", "A small restaurant..."]
    val exampleSentences: List<ExampleSentence>,
    val relatedWords: List<String>,      // ["cafetería", "cafeína"]
    val sourceLanguage: LanguageCode,
    val targetLanguage: LanguageCode,
    val contextUsed: String?
)
```

### Translation Caching

Translations are automatically cached to avoid repeated API calls.

```kotlin
// Access cached translations
val cache: StateFlow<Map<String, WordTranslation>> = sdk.translationCache

// Clear cache
suspend fun clearTranslationCache()
```

**Cache Key Format:** `"sourceLanguage:targetLanguage:word"` (e.g., `"es:en:café"`)

---

## Vocabulary API

### Adding Words

```kotlin
// Method 1: Full VocabularyEntry
suspend fun addToVocabulary(entry: VocabularyEntry)

// Method 2: Simplified parameters (recommended)
suspend fun addToVocabulary(
    word: String,
    translation: String,
    partOfSpeech: PartOfSpeech? = null,
    sourceLanguage: LanguageCode? = null,
    exampleSentence: String? = null,
    scenarioId: String? = null
): VocabularyEntry
```

### Checking Vocabulary

```kotlin
// Check if word exists (case-insensitive)
fun isInVocabulary(word: String, language: LanguageCode? = null): Boolean

// Get specific entry
fun getVocabularyEntry(word: String, language: LanguageCode? = null): VocabularyEntry?
```

### Updating & Removing

```kotlin
// Update notes
suspend fun updateVocabularyNotes(entryId: String, notes: String)

// Remove entry
suspend fun removeFromVocabulary(entryId: String)
```

### Observing Vocabulary

```kotlin
// All vocabulary entries
val vocabularyEntries: StateFlow<List<VocabularyEntry>>

// Words due for review
val dueReviews: StateFlow<List<VocabularyEntry>>

// Statistics
val vocabularyStats: StateFlow<VocabularyStats>
```

---

## VocabularyEntry Model

```kotlin
data class VocabularyEntry(
    val id: String,
    val word: String,
    val translation: String,
    val language: LanguageCode,
    val partOfSpeech: PartOfSpeech?,
    val exampleSentence: String?,
    val audioUrl: String?,
    
    // SRS Fields
    val masteryLevel: Int,           // 0-5
    val easeFactor: Float,           // SM-2 ease factor
    val intervalDays: Int,           // days until next review
    val nextReviewAt: Long,          // timestamp
    val totalReviews: Int,
    val correctReviews: Int,
    
    // Context
    val firstSeenInDialogId: String?,
    val firstSeenAt: Long,
    val lastReviewedAt: Long?,
    val notes: String?               // user notes
)
```

### Mastery Levels

```kotlin
enum class MasteryLevel {
    NEW,        // masteryLevel 0, never reviewed
    LEARNING,   // masteryLevel 1-2, < 7 days interval
    REVIEWING,  // masteryLevel 3-4, 7-30 days interval
    MASTERED    // masteryLevel 5, > 30 days interval
}

// Get mastery level for an entry
val level: MasteryLevel = entry.mastery
```

---

## Spaced Repetition (SRS)

### Recording Reviews

```kotlin
suspend fun recordVocabularyReview(entryId: String, quality: ReviewQuality)
```

### ReviewQuality Levels

| Quality | Effect | Use When |
|---------|--------|----------|
| `AGAIN` | Reset interval to 1 day, decrease ease | Complete failure |
| `HARD` | Slight interval increase, decrease ease | Correct but difficult |
| `GOOD` | Normal interval increase | Correct with effort |
| `EASY` | Large interval increase, increase ease | Effortless recall |

### Getting Due Reviews

```kotlin
suspend fun getVocabularyForReview(limit: Int = 20): List<VocabularyEntry>

// Or observe the StateFlow
val dueReviews: StateFlow<List<VocabularyEntry>>
```

---

## Platform-Specific Implementation

### TranslationProvider

The SDK uses an injectable `TranslationProvider` for flexibility:

```kotlin
interface TranslationProvider {
    suspend fun translateWord(
        word: String,
        sentenceContext: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ): WordTranslation
    
    fun isAvailable(): Boolean
}
```

**Default:** `MockTranslationProvider` (for development)

**Production implementations:**
- **Subscribed users:** Server API provider
- **iOS 26 free users:** Native Foundation Models provider

### TranslationCacheRepository

Native apps should provide platform-specific cache implementations:

```kotlin
interface TranslationCacheRepository {
    suspend fun get(word: String, sourceLanguage: LanguageCode, targetLanguage: LanguageCode): WordTranslation?
    suspend fun put(translation: WordTranslation)
    suspend fun getAll(): Map<String, WordTranslation>
    suspend fun clear()
    suspend fun remove(word: String, sourceLanguage: LanguageCode, targetLanguage: LanguageCode)
}
```

**iOS Implementation (SwiftData):**

```swift
class SwiftDataTranslationCacheRepository: TranslationCacheRepository {
    private let modelContext: ModelContext
    
    func get(word: String, sourceLanguage: String, targetLanguage: String) async -> WordTranslation? {
        let key = generateCacheKey(word: word, sourceLanguage: sourceLanguage, targetLanguage: targetLanguage)
        let descriptor = FetchDescriptor<SDTranslationCache>(
            predicate: #Predicate { $0.cacheKey == key }
        )
        let results = try? modelContext.fetch(descriptor)
        return results?.first?.toSDKTranslation()
    }
    
    func put(translation: WordTranslation) async {
        let cached = SDTranslationCache(from: translation)
        modelContext.insert(cached)
        try? modelContext.save()
    }
}
```

**Android Implementation (Room):**

```kotlin
class RoomTranslationCacheRepository(
    private val dao: TranslationCacheDao
) : TranslationCacheRepository {
    
    override suspend fun get(
        word: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ): WordTranslation? {
        val key = generateCacheKey(word, sourceLanguage, targetLanguage)
        return dao.getByKey(key)?.toWordTranslation()
    }
    
    override suspend fun put(translation: WordTranslation) {
        dao.insert(TranslationCacheEntity.fromWordTranslation(translation))
    }
}
```

---

## SDK Constructor

```kotlin
class BrainSDK(
    aiProvider: AIProvider? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    userProfileRepository: UserProfileRepository = InMemoryUserProfileRepository(),
    vocabularyRepository: VocabularyRepository = InMemoryVocabularyRepository(),
    progressRepository: ProgressRepository = InMemoryProgressRepository(),
    dialogHistoryRepository: DialogHistoryRepository = InMemoryDialogHistoryRepository(),
    translationProvider: TranslationProvider = MockTranslationProvider(),
    translationCacheRepository: TranslationCacheRepository = InMemoryTranslationCacheRepository()
)
```

---

## Complete API Reference

### Translation Methods

| Method | Description |
|--------|-------------|
| `translateWord(word, context, source?, target?)` | Translate with context |
| `clearTranslationCache()` | Clear cached translations |
| `translationCache` | Observable cache StateFlow |

### Vocabulary Methods

| Method | Description |
|--------|-------------|
| `addToVocabulary(entry)` | Add VocabularyEntry |
| `addToVocabulary(word, translation, ...)` | Add with parameters |
| `isInVocabulary(word, language?)` | Check if exists |
| `getVocabularyEntry(word, language?)` | Get entry by word |
| `removeFromVocabulary(entryId)` | Delete entry |
| `updateVocabularyNotes(entryId, notes)` | Update notes |
| `getVocabularyForReview(limit)` | Get due reviews |
| `recordVocabularyReview(entryId, quality)` | Record review |
| `vocabularyEntries` | Observable entries StateFlow |
| `vocabularyStats` | Observable stats StateFlow |
| `dueReviews` | Observable due reviews StateFlow |

---

## Error Handling

Translation and vocabulary operations are suspending functions that may throw exceptions:

```kotlin
try {
    val translation = sdk.translateWord("café", "")
} catch (e: Exception) {
    // Handle network or provider errors
}
```

For production apps, implement proper error handling in your TranslationProvider.

---

## Testing

The SDK includes comprehensive tests for all vocabulary and translation features:

- `TranslationModelsTest` - Model construction and MasteryLevel
- `MockTranslationProviderTest` - Translation provider behavior
- `InMemoryTranslationCacheRepositoryTest` - Cache operations
- `InMemoryVocabularyRepositoryExtendedTest` - Repository operations
- `BrainSDKVocabularyTest` - SDK integration tests

Run tests:
```bash
./gradlew :composeApp:test
```

---

## Migration Notes

### From Previous Versions

**VocabularyEntry changes:**
- Added `notes: String?` field (nullable, backward compatible)

**New StateFlows:**
- `vocabularyEntries` - Full vocabulary list
- `translationCache` - Cached translations

**New Methods:**
- `translateWord()` - Word translation
- `isInVocabulary()` - Quick existence check
- `getVocabularyEntry()` - Get by word
- `removeFromVocabulary()` - Delete entry
- `updateVocabularyNotes()` - Update notes
- `clearTranslationCache()` - Clear cache

All changes are additive and backward compatible.
