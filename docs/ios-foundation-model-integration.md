# iOS 26 Foundation Model Integration Guide

This guide explains how to integrate Apple's on-device Foundation Models with the Bring a Brain SDK for iOS 26+.

## Prerequisites

- iOS 26.0+ (Foundation Models framework)
- Xcode 17+
- Device with Apple Silicon (iPhone 15 Pro+ or M-series iPad)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Your SwiftUI App                         │
├─────────────────────────────────────────────────────────────┤
│  IOSLLMBridge.swift  ←  You implement this                  │
├─────────────────────────────────────────────────────────────┤
│  NativeLLMProvider.kt  ←  SDK provides this interface       │
├─────────────────────────────────────────────────────────────┤
│  BrainSDK (DialogStore, NetworkSession, etc.)               │
└─────────────────────────────────────────────────────────────┘
```

## Step 1: Check Device Availability

```swift
import FoundationModels

func checkLLMAvailability() -> String {
    if #available(iOS 26.0, *) {
        let availability = SystemLanguageModel.default.availability
        switch availability {
        case .available:
            return "AVAILABLE"
        case .unavailable(.deviceNotSupported):
            return "NOT_SUPPORTED"
        case .unavailable(.modelNotReady):
            return "MODEL_NOT_READY"
        default:
            return "UNKNOWN"
        }
    }
    return "NOT_SUPPORTED"
}
```

## Step 2: Implement IOSLLMBridge

Create `IOSLLMBridge.swift` in your iOS project:

```swift
import Foundation
import FoundationModels

@available(iOS 26.0, *)
@objc public class IOSLLMBridge: NSObject {
    
    private var session: LanguageModelSession?
    
    @objc public func checkAvailability() -> String {
        let availability = SystemLanguageModel.default.availability
        switch availability {
        case .available:
            return "AVAILABLE"
        case .unavailable(.deviceNotSupported):
            return "NOT_SUPPORTED"
        case .unavailable(.modelNotReady):
            return "MODEL_NOT_READY"
        default:
            return "UNKNOWN"
        }
    }
    
    @objc public func initialize(systemPrompt: String) -> Bool {
        do {
            let instructions = Instructions {
                systemPrompt
            }
            self.session = LanguageModelSession(
                model: .default,
                instructions: instructions
            )
            return true
        } catch {
            print("Failed to initialize LLM session: \(error)")
            return false
        }
    }
    
    @objc public func generate(
        prompt: String,
        completion: @escaping (String?, Error?) -> Void
    ) {
        guard let session = session else {
            completion(nil, NSError(
                domain: "BringABrain.LLM",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Session not initialized"]
            ))
            return
        }
        
        Task {
            do {
                let response = try await session.respond(to: prompt)
                await MainActor.run {
                    completion(response.content, nil)
                }
            } catch {
                await MainActor.run {
                    completion(nil, error)
                }
            }
        }
    }
    
    @objc public func generateStream(
        prompt: String,
        onToken: @escaping (String) -> Void,
        onComplete: @escaping (Error?) -> Void
    ) {
        guard let session = session else {
            onComplete(NSError(
                domain: "BringABrain.LLM",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Session not initialized"]
            ))
            return
        }
        
        Task {
            do {
                let stream = session.streamResponse(to: prompt)
                for try await partial in stream {
                    await MainActor.run {
                        onToken(partial.content)
                    }
                }
                await MainActor.run {
                    onComplete(nil)
                }
            } catch {
                await MainActor.run {
                    onComplete(error)
                }
            }
        }
    }
    
    @objc public func dispose() {
        session = nil
    }
}
```

## Step 3: Create Kotlin Bridge Wrapper

Modify `NativeLLMProvider.ios.kt` in your fork of the SDK:

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

actual fun createNativeLLMBridge(): NativeLLMBridge? {
    return IOSNativeLLMBridge()
}

class IOSNativeLLMBridge : NativeLLMBridge {
    private val bridge = IOSLLMBridge()
    
    override fun checkAvailability(): LLMAvailability {
        return when (bridge.checkAvailability()) {
            "AVAILABLE" -> LLMAvailability.AVAILABLE
            "NOT_SUPPORTED" -> LLMAvailability.NOT_SUPPORTED
            "MODEL_NOT_READY" -> LLMAvailability.MODEL_NOT_READY
            else -> LLMAvailability.UNKNOWN
        }
    }
    
    override suspend fun initialize(systemPrompt: String): Boolean {
        return bridge.initialize(systemPrompt)
    }
    
    override suspend fun generate(prompt: String): String = suspendCoroutine { cont ->
        bridge.generate(prompt) { result, error ->
            if (error != null) {
                cont.resumeWithException(Exception(error.localizedDescription))
            } else {
                cont.resume(result ?: "")
            }
        }
    }
    
    override fun streamGenerate(prompt: String): Flow<String> = callbackFlow {
        bridge.generateStream(
            prompt = prompt,
            onToken = { token -> trySend(token) },
            onComplete = { error ->
                if (error != null) {
                    close(Exception(error.localizedDescription))
                } else {
                    close()
                }
            }
        )
        awaitClose { }
    }
    
    override fun dispose() {
        bridge.dispose()
    }
}
```

## Step 4: Register with BrainSDK

In your iOS app initialization:

```swift
import BabLanguageSDK // The KMP framework

@main
struct MyApp: App {
    init() {
        // Check if native LLM is available and register it
        if #available(iOS 26.0, *) {
            let availability = IOSLLMBridge().checkAvailability()
            if availability == "AVAILABLE" {
                // The SDK will automatically use NativeLLMProvider
                print("Native LLM available - using on-device AI")
            }
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

## Step 5: Use Structured Output (Advanced)

For parsing structured responses, use the `@Generable` macro:

```swift
import FoundationModels

@available(iOS 26.0, *)
@Generable
struct DialogResponse {
    let textNative: String
    let textTranslated: String
    let sentiment: String?
    let suggestedReplies: [String]?
}

@available(iOS 26.0, *)
extension IOSLLMBridge {
    
    @objc public func generateStructured(
        prompt: String,
        completion: @escaping (String?, Error?) -> Void
    ) {
        guard let session = session else {
            completion(nil, NSError(domain: "LLM", code: 1))
            return
        }
        
        Task {
            do {
                let response: DialogResponse = try await session.respond(
                    to: prompt,
                    generating: DialogResponse.self
                )
                
                // Serialize to JSON for Kotlin
                let encoder = JSONEncoder()
                let data = try encoder.encode(response)
                let json = String(data: data, encoding: .utf8)
                
                await MainActor.run {
                    completion(json, nil)
                }
            } catch {
                await MainActor.run {
                    completion(nil, error)
                }
            }
        }
    }
}
```

## Memory Management

The on-device LLM uses significant RAM. Handle memory pressure:

```swift
import Foundation

@available(iOS 26.0, *)
class MemoryAwareLLMBridge: IOSLLMBridge {
    
    private var memorySource: DispatchSourceMemoryPressure?
    
    override init() {
        super.init()
        setupMemoryMonitoring()
    }
    
    private func setupMemoryMonitoring() {
        memorySource = DispatchSource.makeMemoryPressureSource(
            eventMask: [.warning, .critical],
            queue: .main
        )
        
        memorySource?.setEventHandler { [weak self] in
            guard let source = self?.memorySource else { return }
            
            if source.data == .critical {
                // Release LLM session to free memory
                self?.dispose()
                print("LLM session released due to memory pressure")
            }
        }
        
        memorySource?.resume()
    }
    
    deinit {
        memorySource?.cancel()
    }
}
```

## Fallback Strategy

When native LLM is unavailable, the SDK uses `MockAIProvider`. For production:

```kotlin
// In your app initialization
val aiProvider = NativeLLMFactory.createIfAvailable() 
    ?: CloudAIProvider(apiKey = "...")  // Your fallback
    ?: MockAIProvider()  // Development fallback

val sdk = BrainSDK(aiProvider = aiProvider)
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `MODEL_NOT_READY` | Wait for iOS to download the model, or trigger download in Settings |
| `NOT_SUPPORTED` | Device doesn't have Apple Silicon - use cloud fallback |
| Memory warnings | Implement memory pressure handling |
| Slow first response | First inference loads the model - show loading indicator |

## Testing

Since Foundation Models require iOS 26 device:

1. **Unit tests**: Use `MockAIProvider` (SDK default)
2. **Integration tests**: Test on physical device with iOS 26 beta
3. **Simulator**: Foundation Models not available - use mock

```swift
#if targetEnvironment(simulator)
    // Use mock provider in simulator
    let bridge: NativeLLMBridge? = nil
#else
    let bridge = IOSLLMBridge()
#endif
```
