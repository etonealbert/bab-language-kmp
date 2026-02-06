# Bring a Brain - Quick Start for AI Assistants

> Read this first. Then dive into specific docs as needed.

## What is this project?

**Kotlin Multiplatform SDK** for a language learning game where players act out dialog scenarios.

- **Solo Mode**: Play with AI partner (uses on-device LLM on iOS 26)
- **Multiplayer Mode**: 2-4 players via Bluetooth
- **Headless**: No UI in SDK - native apps bring SwiftUI/Compose

## Key Files to Know

| Purpose | File |
|---------|------|
| Entry point | `composeApp/src/commonMain/.../BrainSDK.kt` |
| State machine | `composeApp/src/commonMain/.../domain/stores/DialogStore.kt` |
| Game state | `composeApp/src/commonMain/.../domain/models/SessionState.kt` |
| Network types | `composeApp/src/commonMain/.../domain/models/Packet.kt` |
| Build config | `composeApp/build.gradle.kts` |

## Build Commands

```bash
# Run tests
./gradlew :composeApp:allTests

# Build iOS framework
./gradlew :composeApp:assembleBabLanguageSDKXCFramework

# Build Android AAR
./gradlew :composeApp:assembleRelease
```

## Architecture in 30 Seconds

```
User Action → Intent → Executor → Packet → NetworkSession → Reducer → State
                                              ↑
                                    (loopback or BLE)
```

**Key rule**: ALL state changes go through NetworkSession → Reducer. Even Solo mode.

## What NOT to Do

1. **No UI code** - This is a headless SDK
2. **No mutable state** - Use `data class` with `copy()`
3. **No platform code in commonMain** - Use `expect/actual`
4. **No skipping tests** - TDD required

## Current Status

- **Phase 1**: Core SDK ✅
- **Phase 2**: BLE Multiplayer ✅
- **Phase 3**: iOS 26 LLM docs ✅
- **Phase 4**: Language Learning Features ✅ (100 tests passing)
- **Phase 5**: WebSocket backend 📋 (planned)

## Phase 4 Implementation Summary

Language learning features are now implemented:

| Feature | Purpose |
|---------|---------|
| UserProfile | CEFR levels, interests, learning goals |
| Vocabulary SRS | Spaced repetition with SM-2 algorithm |
| Information Gaps | Secret objectives forcing communication |
| Asymmetric Difficulty | Different prompts per player CEFR level |
| Narrative Recasts | In-flow error correction by NPCs |
| Safety Net | Hint system for anxious learners |
| Plot Twists | AI Director interventions |
| Progress/XP | Streaks, levels, collaborative stats |

**Design doc**: `docs/plans/2026-02-05-language-learning-features-design.md`

## Deep Dive Docs

| Doc | When to Read |
|-----|--------------|
| `PROJECT_RULES.md` | Full project context |
| `CODING_STANDARDS.md` | Writing code |
| `SESSION_HISTORY.md` | Understanding past decisions |
| `docs/ios-foundation-model-integration.md` | iOS 26 AI integration |
| `docs/ios/coredata-persistence-guide.md` | CoreData implementation for iOS apps |
| `docs/android/room-persistence-guide.md` | Room implementation for Android apps |
| `docs/plans/*.md` | Architecture decisions |

## Common Tasks

### Adding a New Feature

1. Write failing test in `commonTest/`
2. Implement in appropriate layer
3. Follow existing patterns
4. Update state through Reducer

### Adding Platform-Specific Code

```kotlin
// commonMain - declare
expect class PlatformThing() {
    fun doThing(): Result
}

// androidMain - implement
actual class PlatformThing {
    actual fun doThing(): Result = /* Android impl */
}

// iosMain - implement
actual class PlatformThing {
    actual fun doThing(): Result = /* iOS impl */
}
```

### Debugging State Issues

1. Check `DialogStore.kt` Reducer
2. Verify Packet type is handled
3. Add logging in Executor
4. Run relevant test

## Questions?

Check the detailed docs or ask the human developer.
