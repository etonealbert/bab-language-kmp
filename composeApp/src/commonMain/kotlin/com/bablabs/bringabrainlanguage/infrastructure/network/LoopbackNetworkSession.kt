package com.bablabs.bringabrainlanguage.infrastructure.network

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.VectorClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class LoopbackNetworkSession(
    override val localPeerId: String
) : NetworkSession {
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connected())
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    private var currentClock = VectorClock()
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        currentClock = currentClock.increment(localPeerId)
        
        val echoedPacket = packet.copy(vectorClock = currentClock)
        packetChannel.send(echoedPacket)
    }
    
    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
        packetChannel.close()
    }
}
