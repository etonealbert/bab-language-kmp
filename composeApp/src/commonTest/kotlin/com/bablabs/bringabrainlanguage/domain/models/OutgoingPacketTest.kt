package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutgoingPacketTest {
    
    @Test
    fun broadcastFactoryCreatesPacketWithNullTarget() {
        val data = "test data".encodeToByteArray()
        val packet = OutgoingPacket.broadcast(data)
        
        assertEquals(null, packet.targetPeerId)
        assertTrue(packet.data.contentEquals(data))
    }
    
    @Test
    fun unicastFactoryCreatesPacketWithSpecificTarget() {
        val peerId = "peer-123"
        val data = "unicast data".encodeToByteArray()
        val packet = OutgoingPacket.unicast(peerId, data)
        
        assertEquals(peerId, packet.targetPeerId)
        assertTrue(packet.data.contentEquals(data))
    }
    
    @Test
    fun equalsWorksCorrectlyForSameData() {
        val data = "same data".encodeToByteArray()
        val packet1 = OutgoingPacket(targetPeerId = "peer-1", data = data)
        val packet2 = OutgoingPacket(targetPeerId = "peer-1", data = data.copyOf())
        
        assertEquals(packet1, packet2)
    }
    
    @Test
    fun equalsReturnsFalseForDifferentData() {
        val packet1 = OutgoingPacket(targetPeerId = "peer-1", data = "data1".encodeToByteArray())
        val packet2 = OutgoingPacket(targetPeerId = "peer-1", data = "data2".encodeToByteArray())
        
        assertTrue(packet1 != packet2)
    }
    
    @Test
    fun hashCodeIsConsistentForEqualPackets() {
        val data = "hash test".encodeToByteArray()
        val packet1 = OutgoingPacket(targetPeerId = null, data = data)
        val packet2 = OutgoingPacket(targetPeerId = null, data = data.copyOf())
        
        assertEquals(packet1.hashCode(), packet2.hashCode())
    }
}
