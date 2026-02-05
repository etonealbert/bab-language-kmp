package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BleConstantsTest {
    
    @Test
    fun serviceUuidIsValidFormat() {
        val uuid = BleConstants.SERVICE_UUID
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }
    
    @Test
    fun characteristicUuidsAreDifferent() {
        val uuids = listOf(
            BleConstants.CHAR_COMMAND_UUID,
            BleConstants.CHAR_STATE_UUID,
            BleConstants.CHAR_STREAM_UUID
        )
        assertEquals(3, uuids.toSet().size)
    }
    
    @Test
    fun mtuDefaultIsReasonable() {
        assertTrue(BleConstants.DEFAULT_MTU >= 23)
        assertTrue(BleConstants.DEFAULT_MTU <= 517)
    }
}
