package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual fun createBleScanner(): BleScanner = IosBleScanner()

class IosBleScanner : BleScanner {
    private val scanner = Scanner {
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Warnings
        }
    }
    
    override fun scan(): Flow<DiscoveredDevice> {
        return scanner.advertisements.map { ad ->
            DiscoveredDevice(
                id = ad.identifier.toString(),
                name = ad.name,
                rssi = ad.rssi
            )
        }
    }
    
    override fun stopScan() {
    }
}
