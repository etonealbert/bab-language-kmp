package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.Packet
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

class BleClientSession(
    override val localPeerId: String,
    private val hostDeviceId: String
) : NetworkSession {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    private var currentClock = VectorClock()
    private val fragmenter = PacketFragmenter()
    private val reassemblyBuffer = mutableMapOf<Int, MutableList<Fragment>>()
    
    fun initiateConnection() {
        _state.value = ConnectionState.Connecting
    }
    
    fun onConnected() {
        _state.value = ConnectionState.Connected()
    }
    
    fun onDisconnected() {
        _state.value = ConnectionState.Disconnected
    }
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        currentClock = currentClock.increment(localPeerId)
        val updatedPacket = packet.copy(vectorClock = currentClock)
        
        val fragments = fragmenter.fragment(updatedPacket)
        sendToHost(fragments)
    }
    
    private fun sendToHost(fragments: List<Fragment>) {
    }
    
    suspend fun onFragmentReceived(fragment: Fragment, packetId: Int) {
        val buffer = reassemblyBuffer.getOrPut(packetId) { mutableListOf() }
        buffer.add(fragment)
        
        if (buffer.size == fragment.totalFragments) {
            val packet = fragmenter.reassemble(buffer)
            reassemblyBuffer.remove(packetId)
            packetChannel.send(packet)
        }
    }
    
    suspend fun onPacketReceived(packet: Packet) {
        packetChannel.send(packet)
    }
    
    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
        packetChannel.close()
    }
}
