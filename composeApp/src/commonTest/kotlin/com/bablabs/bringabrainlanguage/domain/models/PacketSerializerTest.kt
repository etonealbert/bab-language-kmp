package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PacketSerializerTest {
    
    @Test
    fun encodeAndDecodeDialogLinePacket() {
        val line = DialogLine(
            id = "line-1",
            speakerId = "player-1",
            roleName = "Customer",
            textNative = "Hola mundo",
            textTranslated = "Hello world",
            timestamp = 1000L
        )
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "player-1",
            vectorClock = VectorClock(mapOf("player-1" to 1)),
            payload = PacketPayload.DialogLineAdded(line)
        )
        
        val encoded = PacketSerializer.encode(packet)
        val decoded = PacketSerializer.decode(encoded)
        
        assertNotNull(decoded)
        assertEquals(PacketType.DIALOG_LINE_ADDED, decoded.type)
        assertEquals("player-1", decoded.senderId)
    }
    
    @Test
    fun encodeAndDecodeLineCompletedPacket() {
        val result = PronunciationResult(
            accuracy = 0.95f,
            duration = 2500L,
            errorCount = 1,
            wordErrors = emptyList(),
            skipped = false
        )
        val packet = Packet(
            type = PacketType.LINE_COMPLETED,
            senderId = "player-2",
            vectorClock = VectorClock(mapOf("player-2" to 5)),
            payload = PacketPayload.LineCompleted("line-abc", result)
        )
        
        val encoded = PacketSerializer.encode(packet)
        val decoded = PacketSerializer.decode(encoded)
        
        assertNotNull(decoded)
        assertEquals(PacketType.LINE_COMPLETED, decoded.type)
        val payload = decoded.payload as PacketPayload.LineCompleted
        assertEquals("line-abc", payload.lineId)
        assertEquals(0.95f, payload.result.accuracy)
    }
    
    @Test
    fun encodeAndDecodeTurnAdvancedPacket() {
        val pendingLine = DialogLine(
            id = "line-next",
            speakerId = "player-3",
            roleName = "Barista",
            textNative = "Es tu turno",
            textTranslated = "Your turn",
            timestamp = 2000L
        )
        val packet = Packet(
            type = PacketType.TURN_ADVANCED,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 10)),
            payload = PacketPayload.TurnAdvanced("player-3", pendingLine)
        )
        
        val encoded = PacketSerializer.encode(packet)
        val decoded = PacketSerializer.decode(encoded)
        
        assertNotNull(decoded)
        val payload = decoded.payload as PacketPayload.TurnAdvanced
        assertEquals("player-3", payload.nextPlayerId)
        assertNotNull(payload.pendingLine)
        assertEquals("Your turn", payload.pendingLine?.textTranslated)
    }
    
    @Test
    fun encodeAndDecodeLeaderboardUpdatePacket() {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val leaderboard = SessionLeaderboard(
            rankings = listOf(
                PlayerRanking(rank = 1, playerId = "player-1", displayName = "Player 1", score = 150),
                PlayerRanking(rank = 2, playerId = "player-2", displayName = "Player 2", score = 120)
            ),
            updatedAt = now
        )
        val packet = Packet(
            type = PacketType.LEADERBOARD_UPDATE,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 15)),
            payload = PacketPayload.LeaderboardUpdate(leaderboard)
        )
        
        val encoded = PacketSerializer.encode(packet)
        val decoded = PacketSerializer.decode(encoded)
        
        assertNotNull(decoded)
        val payload = decoded.payload as PacketPayload.LeaderboardUpdate
        assertEquals(2, payload.leaderboard.rankings.size)
        assertEquals(150, payload.leaderboard.rankings[0].score)
    }
    
    @Test
    fun decodeInvalidDataReturnsNull() {
        val invalidData = "not a valid json packet".encodeToByteArray()
        val decoded = PacketSerializer.decode(invalidData)
        assertNull(decoded)
    }
    
    @Test
    fun decodeEmptyDataReturnsNull() {
        val emptyData = byteArrayOf()
        val decoded = PacketSerializer.decode(emptyData)
        assertNull(decoded)
    }
    
    @Test
    fun encodeAndDecodeHeartbeatPacket() {
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "player-1",
            vectorClock = VectorClock(mapOf("player-1" to 100)),
            payload = PacketPayload.Heartbeat
        )
        
        val encoded = PacketSerializer.encode(packet)
        val decoded = PacketSerializer.decode(encoded)
        
        assertNotNull(decoded)
        assertEquals(PacketType.HEARTBEAT, decoded.type)
    }
}
