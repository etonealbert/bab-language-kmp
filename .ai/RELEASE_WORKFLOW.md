# SDK Release Workflow (iOS)

The iOS SDK release is triggered by pushing a git tag. You can do this manually or use the auto-increment command below.

## Auto-Increment Command (Recommended)

Run this single command in the terminal to automatically pull the latest tags, increment the patch version (e.g., `1.0.6` -> `1.0.7`), and trigger the release:

```bash
git checkout main && git pull origin main && \
git fetch --tags && \
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0") && \
NEW_TAG=$(echo $LATEST_TAG | awk -F. -v OFS=. '{$NF++;print}') && \
echo "Updating from $LATEST_TAG to $NEW_TAG..." && \
git tag $NEW_TAG && \
git push origin $NEW_TAG
```

### What This Does

1. Checks out `main` and pulls latest changes
2. Fetches all remote tags
3. Finds the most recent tag (e.g., `v1.0.6`)
4. Increments the patch version (e.g., `v1.0.6` -> `v1.0.7`)
5. Creates the new tag locally
6. Pushes the tag to origin, which triggers the CI/CD release pipeline

## Manual Release

```bash
git tag v1.0.7
git push origin v1.0.7
```

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.10 | 2026-02-11 | Lobby State Sync: LobbyState/PlayerProfile models, LOBBY_UPDATE/CLIENT_JOIN/START_GAME packets, setLobbyScenario/setLobbyDifficulty/onClientJoined/startLobbyGame SDK methods, iOS SDKObserver docs |
| 1.0.8 | 2026-02-10 | Chat history feature: HistorySession model, HistoryRepository interface, MockRemoteHistoryRepository with premium gating, BrainSDK.history StateFlow, endSession() auto-save, UserProfile.isPremium |
| 1.0.7 | 2026-02-10 | SwiftData persistence for UserProfile, secondary constructor for Swift DI, onboarding fix |
| 1.0.6 | — | Previous release |

## Pre-Release Checklist

1. All tests pass: `./gradlew :composeApp:allTests`
2. XCFramework builds: `./gradlew :composeApp:assembleBabLanguageSDKXCFramework`
3. Docs updated (especially `docs/ios/integration-guide.md`)
4. `.ai/` context files updated if architecture changed
5. `SESSION_HISTORY.md` updated with changes
