package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import kotlinx.coroutines.flow.Flow

data class DiscoveredDevice(
    val id: String,
    val name: String?,
    val rssi: Int
)

interface BleScanner {
    fun scan(): Flow<DiscoveredDevice>
    fun stopScan()
}

expect fun createBleScanner(): BleScanner
