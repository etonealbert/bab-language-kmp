package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketFragmenterTest {
    
    // Use a larger chunk size that can fit a minimal HEARTBEAT packet
    private val fragmenter = PacketFragmenter(maxChunkSize = 200)
    
    @Test
    fun smallPacketFitsInSingleFragment() {
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.Heartbeat
        )
        
        val fragments = fragmenter.fragment(packet)
        
        assertEquals(1, fragments.size)
    }
    
    @Test
    fun largePacketSplitsIntoMultipleFragments() {
        val longText = "a".repeat(500)
        val smallChunkFragmenter = PacketFragmenter(maxChunkSize = 50)
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", longText, longText, 0L)
            )
        )
        
        val fragments = smallChunkFragmenter.fragment(packet)
        
        assertTrue(fragments.size > 1)
    }
    
    @Test
    fun fragmentsReassembleToOriginalPacket() {
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(mapOf("peer-a" to 5L)),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", "Hola mundo", "Hello world", 123L)
            )
        )
        
        val fragments = fragmenter.fragment(packet)
        val reassembled = fragmenter.reassemble(fragments)
        
        assertEquals(packet, reassembled)
    }
    
    @Test
    fun fragmentsHaveSequenceNumbers() {
        val longText = "a".repeat(500)
        val smallChunkFragmenter = PacketFragmenter(maxChunkSize = 50)
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", longText, longText, 0L)
            )
        )
        
        val fragments = smallChunkFragmenter.fragment(packet)
        
        fragments.forEachIndexed { index, fragment ->
            assertEquals(index, fragment.sequenceNumber)
            assertEquals(fragments.size, fragment.totalFragments)
        }
    }
}
