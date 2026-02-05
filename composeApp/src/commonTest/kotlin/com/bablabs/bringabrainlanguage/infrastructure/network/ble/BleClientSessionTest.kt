package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import kotlin.test.Test
import kotlin.test.assertTrue

class BleClientSessionTest {
    
    @Test
    fun clientSessionImplementsNetworkSession() {
        val session: NetworkSession = BleClientSession(
            localPeerId = "client-1",
            hostDeviceId = "host-device-uuid"
        )
        
        assertTrue(session.state.value is ConnectionState.Disconnected)
    }
    
    @Test
    fun initiateConnectionChangesStateToConnecting() {
        val session = BleClientSession(
            localPeerId = "client-1",
            hostDeviceId = "host-device-uuid"
        )
        
        session.initiateConnection()
        
        assertTrue(session.state.value is ConnectionState.Connecting)
    }
    
    @Test
    fun onConnectedChangesStateToConnected() {
        val session = BleClientSession(
            localPeerId = "client-1",
            hostDeviceId = "host-device-uuid"
        )
        
        session.initiateConnection()
        session.onConnected()
        
        assertTrue(session.state.value is ConnectionState.Connected)
    }
}
