package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import kotlin.test.Test
import kotlin.test.assertTrue

class BleHostSessionTest {
    
    @Test
    fun hostSessionImplementsNetworkSession() {
        val session: NetworkSession = BleHostSession(localPeerId = "host-1")
        
        assertTrue(session.state.value is ConnectionState.Disconnected)
    }
    
    @Test
    fun startAdvertisingChangesStateToConnecting() {
        val session = BleHostSession(localPeerId = "host-1")
        
        session.startAdvertising()
        
        assertTrue(session.state.value is ConnectionState.Connecting)
    }
    
    @Test
    fun stopAdvertisingChangesStateToDisconnected() {
        val session = BleHostSession(localPeerId = "host-1")
        
        session.startAdvertising()
        session.stopAdvertising()
        
        assertTrue(session.state.value is ConnectionState.Disconnected)
    }
}
