package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketSerializationTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun dialogLineAddedPacketSerializesAndDeserializesCorrectly() {
        val line = DialogLine(
            id = "line-1",
            speakerId = "peer-a",
            roleName = "The Barista",
            textNative = "Hola, que deseas?",
            textTranslated = "Hello, what would you like?",
            timestamp = 1234567890L
        )
        
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(mapOf("peer-a" to 1L)),
            payload = PacketPayload.DialogLineAdded(line)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun generateRequestPacketSerializesCorrectly() {
        val packet = Packet(
            type = PacketType.GENERATE_REQUEST,
            senderId = "peer-b",
            vectorClock = VectorClock(mapOf("peer-b" to 5L)),
            payload = PacketPayload.GenerateRequest(prompt = "Generate next dialog line")
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun heartbeatPacketHasMinimalPayload() {
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.Heartbeat
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
}
