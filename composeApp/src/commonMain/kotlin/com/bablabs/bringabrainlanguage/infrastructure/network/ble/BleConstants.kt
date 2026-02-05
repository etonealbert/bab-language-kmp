package com.bablabs.bringabrainlanguage.infrastructure.network.ble

object BleConstants {
    const val SERVICE_UUID = "0000bab1-0000-1000-8000-00805f9b34fb"
    
    const val CHAR_COMMAND_UUID = "0000bab2-0000-1000-8000-00805f9b34fb"
    const val CHAR_STATE_UUID = "0000bab3-0000-1000-8000-00805f9b34fb"
    const val CHAR_STREAM_UUID = "0000bab4-0000-1000-8000-00805f9b34fb"
    
    const val DEFAULT_MTU = 185
    const val MAX_MTU = 517
    const val SUPERVISION_TIMEOUT_MS = 4000
    
    const val MAX_PACKET_SIZE = 512
    const val HEADER_SIZE = 4
}
