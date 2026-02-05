package com.bablabs.bringabrainlanguage.infrastructure.network

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.PacketPayload
import com.bablabs.bringabrainlanguage.domain.models.PacketType
import com.bablabs.bringabrainlanguage.domain.models.VectorClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackNetworkSessionTest {
    
    @Test
    fun loopbackSessionStartsInConnectedState() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        assertTrue(session.state.value is ConnectionState.Connected)
    }
    
    @Test
    fun sentPacketIsEchoedBackWithIncrementedVectorClock() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        val outgoingPacket = Packet(
            type = PacketType.GENERATE_REQUEST,
            senderId = "local",
            vectorClock = VectorClock(),
            payload = PacketPayload.GenerateRequest("test prompt")
        )
        
        session.send(outgoingPacket)
        
        val receivedPacket = session.incomingPackets.first()
        
        assertEquals(1L, receivedPacket.vectorClock.timestamps["local"])
        assertEquals(outgoingPacket.type, receivedPacket.type)
        assertEquals(outgoingPacket.payload, receivedPacket.payload)
    }
    
    @Test
    fun disconnectChangesStateToDisconnected() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        session.disconnect()
        
        assertEquals(ConnectionState.Disconnected, session.state.value)
    }
}
