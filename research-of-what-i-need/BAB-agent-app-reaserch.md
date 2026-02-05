

### Executive Summary: The "Headless" Strategy

You are building a **Distributed System**, not just a simple app. To succeed, strict separation of concerns is vital. Your app will act as a "dumb terminal" for two powerful brains: the **Local Brain** (iOS Foundation Models + BLE) and the **Remote Brain** (Rust Server + WebSockets).

**The Verdict:** Your plan is technically sound. Proceed with **Kotlin Multiplatform (KMP)** for logic and **Native UI** (SwiftUI/Compose).

---

### 1. The Architecture Blueprint

#### A. The Core Logic (KMP + MVI)

We will use **MVIKotlin** + **Decompose**. This makes your app testable without a real backend or real Bluetooth.

* **Store:** Holds `SessionState` (Participants, Dialog History, Votes).
* **Executor:** The decision maker. It receives `Intents` (User clicks "Generate") and decides whether to send a BLE packet or a WebSocket message.
* **Reducer:** The **only** place state changes. It applies changes *only* after they are confirmed by the Host (Device or Server).

#### B. The "Hybrid Host" Logic

This addresses your question about servers. The app chooses the "Host" based on user intent, not just subscription status.

| Scenario | Users' Location | Subscription? | Host | Technology |
| --- | --- | --- | --- | --- |
| **Local Play** | Same Room | Free or Paid | **One Device** | Bluetooth (BLE) |
| **Remote Play** | Different Cities | **Paid Only** | **Rust Server** | WebSockets |

* **Recommendation:** Even if users are paid subscribers, default to **Bluetooth** if they are nearby. It is faster (zero latency) and private. Use the Server Host *only* when physical distance requires it.

#### C. The "Lazy Registration" Auth Flow

This implements your request to ask for sign-in *after* purchase.

1. **Install:** User gets a random UUID (`User-Anon-123`). Stored in Keychain/Keystore.
2. **Play:** User plays free local modes.
3. **Buy:** User purchases subscription.
* **iOS:** App sends JWS Receipt + `User-Anon-123` to Rust.
* **Rust:** Validates JWS. Creates a "Shadow Account" for `User-Anon-123`. Grants access.


4. **Sign In (Optional):** App prompts: *"Protect your purchase. Create a login."*
* User enters `john@email.com`.
* **Rust:** Updates Shadow Account `User-Anon-123` to be owned by `john@email.com`.



---

### 2. Implementation Roadmap

Do not try to build everything at once. Follow this "Mock-First" path.

#### Phase 1: The "Walking Skeleton" (Weeks 1-4)

**Goal:** A working UI with 4 fake users talking to each other. No Real Bluetooth. No Real AI.

1. **Setup KMP:** Configure MVIKotlin, Decompose, and SQLDelight.
2. **The Interfaces:** Create `interface NetworkSession` and `interface AIProvider`.
3. **The Mocks:**
* `MockNetworkSession`: Returns fake messages from "Player 2" after 2 seconds.
* `MockAIProvider`: Returns hardcoded text ("Hello, how are you?") when requested.


4. **UI:** Build the Chat Screen in SwiftUI and Compose. Connect it to the Mock Store.
* *Success Metric:* You can click "Generate," see a fake loading spinner, and see text appear from a fake user.



#### Phase 2: The "Sync" Engine (Weeks 5-8)

**Goal:** Replace `MockNetworkSession` with real Bluetooth.

1. **Implement Kable:** Build the `BleNetworkSession`.
2. **Vector Clocks:** Implement the logic to handle "Who clicked first?"
3. **Testing:** Connect 2 real phones. Test disconnecting and reconnecting.
* *Success Metric:* Phone A clicks "Generate", and Phone B sees the update within 200ms.



#### Phase 3: The "Brain"
**Goal:** Replace `MockAIProvider` with real Intelligence.

1. **iOS Bridge:** Implement `SystemLanguageModel` in Swift and expose it to Kotlin.
2. **Android Mock:** For now, Android just requests from a free API (like Gemini Flash) or stays mocked.
3. **Prompt Engineering:** Tune the system prompt so the AI acts like a "Language Tutor."

#### Phase 4: The Backend & Money 

**Goal:** Real Rust Server, Subscriptions, and Online Play.

1. **Rust Server:** Setup Axum + Postgres.
2. **Receipts:** Implement Apple JWS validation.
3. **WebSockets:** Build the `ServerNetworkSession` (Ktor) to talk to the Rust server.
4. **The Switch:** Add the "Online Mode" toggle in the app.

---

### 3. Technical Recommendation for "Day 1"

Start by creating this folder structure in your KMP `commonMain`:

```text
commonMain/
  ├── domain/
  │   ├── models/            <-- User, DialogLine, Vote
  │   ├── interfaces/        <-- AIProvider, NetworkSession (THE CONTRACTS)
  │   └── state/             <-- SessionState (The Single Source of Truth)
  ├── infrastructure/
  │   ├── mocks/             <-- MockNetworkSession, MockAIProvider (START HERE)
  │   ├── bluetooth/         <-- Real Kable Implementation (Later)
  │   └── network/           <-- Real Ktor Implementation (Later)
  └── presentation/
      └── components/        <-- Decompose Components (ViewModels)

```

**Final Advice:**
This idea is ambitious but achievable. The biggest risk is **Bluetooth stability**. Don't underestimate how hard it is to keep 4 iPhones connected in the background. **Build the Phase 2 Bluetooth prototype early** to prove it works before you spend months on the AI or Payment features.

You are ready. Good luck building "Bring a Brain"!

also realted to backend implement: 

### Level 1: The "Hardcoded Constant" (What you asked for)

This is the easiest way to start Phase 0. You create a simple config object in `commonMain`.

```kotlin
// commonMain/config/AppConfig.kt
object AppConfig {
    // CHANGE THIS BOOLEAN TO SWITCH MODES
    const val USE_MOCK_BACKEND = true 
}

```

Then, in your **Koin Module** (where you setup Dependency Injection), you use this flag:

```kotlin
val appModule = module {
    single<BackendApi> { 
        if (AppConfig.USE_MOCK_BACKEND) {
            MockBackendApi() // Returns fake data immediately
        } else {
            KtorBackendApi() // Connects to real Rust server
        }
    }
}

```

**Pros:** Simple and fast.
**Cons:** You have to recompile the app every time you want to switch but it ok on first stages i need it .

