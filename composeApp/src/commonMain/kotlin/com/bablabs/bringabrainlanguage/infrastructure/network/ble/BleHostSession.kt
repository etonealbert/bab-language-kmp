package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.Participant
import com.bablabs.bringabrainlanguage.domain.models.VectorClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class BleHostSession(
    override val localPeerId: String
) : NetworkSession {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    private var currentClock = VectorClock()
    private val fragmenter = PacketFragmenter()
    
    private val connectedPeers = mutableListOf<Participant>()
    
    fun startAdvertising() {
        _state.value = ConnectionState.Connecting
    }
    
    fun stopAdvertising() {
        _state.value = ConnectionState.Disconnected
    }
    
    fun onPeerConnected(peer: Participant) {
        connectedPeers.add(peer)
        _state.value = ConnectionState.Connected(connectedPeers.toList())
    }
    
    fun onPeerDisconnected(peerId: String) {
        connectedPeers.removeAll { it.id == peerId }
        if (connectedPeers.isEmpty()) {
            _state.value = ConnectionState.Connecting
        } else {
            _state.value = ConnectionState.Connected(connectedPeers.toList())
        }
    }
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        currentClock = currentClock.increment(localPeerId)
        val updatedPacket = packet.copy(vectorClock = currentClock)
        
        val fragments = fragmenter.fragment(updatedPacket)
        
        if (recipientId != null) {
            sendToPeer(recipientId, fragments)
        } else {
            connectedPeers.forEach { peer ->
                sendToPeer(peer.id, fragments)
            }
        }
        
        packetChannel.send(updatedPacket)
    }
    
    private fun sendToPeer(peerId: String, fragments: List<Fragment>) {
    }
    
    suspend fun onPacketReceivedFromPeer(packet: Packet) {
        packetChannel.send(packet)
    }
    
    override suspend fun disconnect() {
        stopAdvertising()
        connectedPeers.clear()
        packetChannel.close()
    }
}
