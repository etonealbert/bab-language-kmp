
# Project: Bring a Brain (Master Design Document)

**Platform:** iOS (Primary), Android (Secondary)
**Architecture:** Kotlin Multiplatform (KMP) + Native UI (SwiftUI/Compose)
**Core Concept:** Collaborative language learning via AI-generated role-play dialogs.

---

## 1. The Concept

"Bring a Brain" is a multiplayer language learning game where 1 to 4 players connect (offline via Bluetooth or online via WebSockets) to act out improvised dialogs.

* **The Problem:** Language apps are lonely. People learn better when they speak to others, but they don't know *what* to say.
* **The Solution:** The app acts as an "AI Director," generating scenarios, lines, and plot twists in real-time, forcing users to read, speak, and react in their target language.

---

## 2. User Experience (UX) Flow

### A. Onboarding (The Setup)

* **Profile Creation:** User enters Name, Age, Gender, Native Language, and Target Language (initially Spanish for English speakers).
* **Preferences:** Users select interests (e.g., "Travel," "Business," "Romance," "Sci-Fi") to guide the AI generation.
* **Output:** Saved locally to `Settings` (Key-Value store) and synced to Server if Premium.

### B. The Lobby (Home Screen)

* **Dialog Library:** A list of saved/past dialogs.
* **Connect Button:**
* **Host:** Starts advertising (BLE) or creates a Room (WebSocket).
* **Join:** Scans for devices (BLE) or enters Room Code.


* **Scenario Selection:** The Host selects a scenario (e.g., "Ordering Coffee," "The Heist," "First Date") or chooses "Generate Random."

### C. The Active Game (The Core Loop)

* **Role Selection:** Players pick roles (e.g., "The Barista," "The Angry Customer").
* **The Script:**
* The app generates 4-5 lines of dialog at a time.
* **UI:** Chat bubbles with Native Text + English Translation (hidden by default, toggleable).
* **Audio:** TTS (Text-to-Speech) available for pronunciation checks.


* **The "Director" Tools (Bottom Bar):**
* **Generate:** Request the next set of lines from AI.
* **Change Scene:** Propose a location shift like same conception but another scene (e.g., "Move to the Park").
* **Vocab Challenge:** Pause dialog to quiz each other on a word found in the text.


* **Voting System:** If a player taps "Change Scene," a permission request appears on all other screens ("User A wants to move to the Park. Allow?"). All must vote "Yes."

### D. Solo Mode

* **User vs. Robot:** The user plays one role; the App (Robot) plays the other.
* **Robot Behavior:** Uses native Text-to-Speech to read its lines aloud.

### E. Tablet Experience (iPad)

* **Adaptive Layout:** Uses `NavigationSplitView`.
* **Left/Main:** The active Chat/Dialog.
* **Right/Sidebar:** Persistent "Vocabulary List" extracted from the current conversation.



---

## 3. Technical Architecture (The Stack)

### A. The "Headless" Strategy

The app logic runs entirely in Kotlin; the UI is 100% Native.

* **Shared Logic (KMP):** `commonMain`
* **State Management:** MVI (Model-View-Intent) using `MVIKotlin`.
* **Navigation Logic:** `Decompose` (Back stack management).
* **Networking:** `Ktor` (WebSockets) + `Kable` (Bluetooth LE).
* **Database:** `SQLDelight` (Local caching of dialogs).


* **UI Layer:**
* **iOS:** `SwiftUI` observing `StateFlow`.
* **Android:** `Jetpack Compose` observing `StateFlow`.



### B. Artificial Intelligence (The Brain)

* **Interface:** `interface AIProvider` (Defined in KMP).
* **iOS Implementation (Free/Offline):**
* Uses **iOS 26 Foundation Models** (`SystemLanguageModel`).
* Bridge: Swift wrapper injects native API into Kotlin interface.


* **Android/Premium Implementation (Paid/Online):**
* Uses **Rust Backend API**.
* Server calls powerful external LLMs (or hosted Llama models).



### C. Backend (Rust)

* **Framework:** `Axum` (Web), `Tokio` (Async Runtime).
* **Database:** `PostgreSQL` + `SQLx`.
* **Role:**
* Authenticates Users.
* Validates Apple/Google Receipts (StoreKit 2 JWS verification).
* Hosts WebSocket Rooms for Online Multiplayer.
* Provides AI Generation API for Android devices.



---

## 4. Implementation Logic

### The Session State Machine

The entire app is driven by this single source of truth in `commonMain`:

```kotlin
data class SessionState(
    val connectionStatus: ConnectionStatus, // Connected, Scanning, Offline
    val participants: List<Participant>,    // Profiles of connected users
    val dialogHistory: List<DialogLine>,    // The script so far
    val currentPhase: DialogPhase,          // Setup, Active, Voting, Finished
    val pendingVote: VoteRequest?,          // "User A wants to X..."
    val isPremium: Boolean                  // Unlocks server features
)

```

### The Dialog Data Model

```kotlin
data class DialogLine(
    val speakerId: String,       // UUID of the user/robot
    val roleName: String,        // "The Detective"
    val textNative: String,      // "¡Alto ahí!"
    val textTranslated: String,  // "Stop right there!"
    val audioUrl: String?        // For server-generated TTS
)

```

---

## 5. Monetization Strategy

* **Freemium Model:**
* **Free:** Local Play (Bluetooth), Solo Mode, iOS On-Device AI (Foundation Models).
* **Premium ($Subscription):** Online Multiplayer (Server), Android AI Generation, Cloud History Sync, Advanced Vocab Tools.


* **Tech:** `RevenueCat KMP` for managing subscriptions, validated by Rust Backend.

---

## 6. Development Phases

1. **Phase 0 (Setup):** Initialize KMP Repo, Configure Gradle for SPM (XCFramework), Setup Mock Backend.
2. **Phase 1 (Core):** Implement Onboarding, Local Data (SQLDelight), and Solo Mode (Mock AI).
3. **Phase 2 (The Brain):** Connect iOS 26 Foundation Models (Swift Bridge) and MVI State Machine.
4. **Phase 3 (Connectivity):** Implement Kable (Bluetooth) for local 2-player sync.
5. **Phase 4 (The Server):** Build Rust Backend, implement StoreKit 2 validation, enable Online Play.
