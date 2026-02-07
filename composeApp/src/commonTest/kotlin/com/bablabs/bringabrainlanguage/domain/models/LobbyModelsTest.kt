package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LobbyModelsTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun lobbyPlayerSerializesWithNullRole() {
        val player = LobbyPlayer(
            peerId = "peer-123",
            displayName = "Alice",
            assignedRole = null,
            isReady = false
        )
        
        val serialized = json.encodeToString(player)
        val deserialized = json.decodeFromString<LobbyPlayer>(serialized)
        
        assertEquals(player, deserialized)
        assertNull(deserialized.assignedRole)
    }
    
    @Test
    fun lobbyPlayerSerializesWithAssignedRole() {
        val role = Role(id = "barista", name = "Barista", description = "Coffee maker")
        val player = LobbyPlayer(
            peerId = "peer-456",
            displayName = "Bob",
            assignedRole = role,
            isReady = true,
            connectionQuality = ConnectionQuality.EXCELLENT
        )
        
        val serialized = json.encodeToString(player)
        val deserialized = json.decodeFromString<LobbyPlayer>(serialized)
        
        assertEquals(player, deserialized)
        assertEquals(role, deserialized.assignedRole)
    }
    
    @Test
    fun connectionQualityHasAllExpectedValues() {
        val qualities = ConnectionQuality.entries
        
        assertEquals(4, qualities.size)
        assertEquals(ConnectionQuality.EXCELLENT, ConnectionQuality.valueOf("EXCELLENT"))
        assertEquals(ConnectionQuality.GOOD, ConnectionQuality.valueOf("GOOD"))
        assertEquals(ConnectionQuality.FAIR, ConnectionQuality.valueOf("FAIR"))
        assertEquals(ConnectionQuality.POOR, ConnectionQuality.valueOf("POOR"))
    }
    
    @Test
    fun connectedPeerSerializesCorrectly() {
        val peer = ConnectedPeer(
            peerId = "peer-789",
            displayName = "Charlie",
            isHost = true,
            lastSeen = 1234567890L
        )
        
        val serialized = json.encodeToString(peer)
        val deserialized = json.decodeFromString<ConnectedPeer>(serialized)
        
        assertEquals(peer, deserialized)
    }
    
    @Test
    fun connectedPeerDefaultsAreCorrect() {
        val peer = ConnectedPeer(
            peerId = "peer-abc",
            displayName = "Diana"
        )
        
        assertEquals(false, peer.isHost)
        assertEquals(0L, peer.lastSeen)
    }
}
