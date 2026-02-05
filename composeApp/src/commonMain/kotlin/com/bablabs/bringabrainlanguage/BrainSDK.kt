package com.bablabs.bringabrainlanguage

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.flow.MutableStateFlow

// This is your new "Entry Point" for iOS and Android devs
class BrainSDK {
    // Just a test function to prove the SDK is alive
    fun initialize(): String {
        return "Brain SDK Initialized! Ready for Logic."
    }

    // Example of exposing state without Compose
    val connectionState = MutableStateFlow("Disconnected")
}