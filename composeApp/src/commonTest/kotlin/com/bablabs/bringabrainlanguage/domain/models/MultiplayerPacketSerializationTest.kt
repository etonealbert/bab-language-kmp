package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiplayerPacketSerializationTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun lineCompletedPayloadSerializesCorrectly() {
        val result = PronunciationResult(
            errorCount = 1,
            accuracy = 0.92f,
            wordErrors = listOf(WordError("hola", 0, "ola", "olla")),
            skipped = false,
            duration = 2500L
        )
        
        val packet = Packet(
            type = PacketType.LINE_COMPLETED,
            senderId = "peer-a",
            vectorClock = VectorClock(mapOf("peer-a" to 5L)),
            payload = PacketPayload.LineCompleted(lineId = "line-1", result = result)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun lineSkippedPayloadSerializesCorrectly() {
        val packet = Packet(
            type = PacketType.LINE_SKIPPED,
            senderId = "peer-b",
            vectorClock = VectorClock(mapOf("peer-b" to 3L)),
            payload = PacketPayload.LineSkipped(lineId = "line-2")
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun roleAssignedPayloadSerializesCorrectly() {
        val packet = Packet(
            type = PacketType.ROLE_ASSIGNED,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 2L)),
            payload = PacketPayload.RoleAssigned(playerId = "peer-c", roleId = "barista")
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun playerReadyPayloadSerializesCorrectly() {
        val packet = Packet(
            type = PacketType.PLAYER_READY,
            senderId = "peer-d",
            vectorClock = VectorClock(mapOf("peer-d" to 1L)),
            payload = PacketPayload.PlayerReady(playerId = "peer-d", isReady = true)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun gameStartedPayloadSerializesCorrectly() {
        val packet = Packet(
            type = PacketType.GAME_STARTED,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 10L)),
            payload = PacketPayload.GameStarted
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun turnAdvancedPayloadSerializesWithPendingLine() {
        val pendingLine = DialogLine(
            id = "line-5",
            speakerId = "host",
            roleName = "Barista",
            textNative = "¿Qué desea tomar?",
            textTranslated = "What would you like to drink?",
            timestamp = 1234567890L,
            assignedToPlayerId = "peer-e",
            visibility = LineVisibility.PRIVATE
        )
        
        val packet = Packet(
            type = PacketType.TURN_ADVANCED,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 15L)),
            payload = PacketPayload.TurnAdvanced(nextPlayerId = "peer-e", pendingLine = pendingLine)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun turnAdvancedPayloadSerializesWithNullPendingLine() {
        val packet = Packet(
            type = PacketType.TURN_ADVANCED,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 16L)),
            payload = PacketPayload.TurnAdvanced(nextPlayerId = "peer-f", pendingLine = null)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun leaderboardUpdatePayloadSerializesCorrectly() {
        val leaderboard = SessionLeaderboard(
            rankings = listOf(
                PlayerRanking(1, "p1", "Alice", 500, "Perfect Score"),
                PlayerRanking(2, "p2", "Bob", 400, null)
            ),
            updatedAt = 1234567890L
        )
        
        val packet = Packet(
            type = PacketType.LEADERBOARD_UPDATE,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 20L)),
            payload = PacketPayload.LeaderboardUpdate(leaderboard = leaderboard)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun dialogLineWithMultiplayerFieldsSerializesCorrectly() {
        val result = PronunciationResult.perfect(2000L)
        
        val line = DialogLine(
            id = "line-10",
            speakerId = "peer-g",
            roleName = "Customer",
            textNative = "Buenos días",
            textTranslated = "Good morning",
            timestamp = 1234567890L,
            assignedToPlayerId = "peer-g",
            visibility = LineVisibility.COMMITTED,
            pronunciationResult = result
        )
        
        val serialized = json.encodeToString(line)
        val deserialized = json.decodeFromString<DialogLine>(serialized)
        
        assertEquals(line, deserialized)
        assertEquals(LineVisibility.COMMITTED, deserialized.visibility)
        assertEquals(result, deserialized.pronunciationResult)
    }
}
