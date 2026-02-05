package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.Participant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NetworkSession {
    val localPeerId: String
    val state: StateFlow<ConnectionState>
    val incomingPackets: Flow<Packet>
    
    suspend fun send(packet: Packet, recipientId: String? = null)
    suspend fun disconnect()
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val peers: List<Participant> = emptyList()) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
}
