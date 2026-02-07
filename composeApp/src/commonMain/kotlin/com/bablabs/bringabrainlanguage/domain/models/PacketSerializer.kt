package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Packet serialization utilities for BLE transport.
 * 
 * Native BLE layer receives ByteArray from peripheral/central and calls
 * SDK's onDataReceived(). These utilities handle Packet <-> ByteArray conversion.
 */
object PacketSerializer {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    
    /**
     * Serialize a Packet to ByteArray for BLE transmission.
     * 
     * Called by SDK when broadcasting state changes to peers.
     * Native layer receives this ByteArray via outgoingPackets Flow.
     */
    fun encode(packet: Packet): ByteArray {
        return json.encodeToString(packet).encodeToByteArray()
    }
    
    /**
     * Deserialize ByteArray back to Packet.
     * 
     * Called by SDK when processing data received from BLE.
     * Returns null if deserialization fails (corrupted/invalid data).
     */
    fun decode(data: ByteArray): Packet? {
        return try {
            json.decodeFromString<Packet>(data.decodeToString())
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Try to decode, throwing exception on failure.
     * Use when you want to handle errors explicitly.
     */
    fun decodeOrThrow(data: ByteArray): Packet {
        return json.decodeFromString<Packet>(data.decodeToString())
    }
}
