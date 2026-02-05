package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.models.Packet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class Fragment(
    val sequenceNumber: Int,
    val totalFragments: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fragment) return false
        return sequenceNumber == other.sequenceNumber &&
               totalFragments == other.totalFragments &&
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + totalFragments
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class PacketFragmenter(
    private val maxChunkSize: Int = BleConstants.DEFAULT_MTU - BleConstants.HEADER_SIZE
) {
    private val json = Json { encodeDefaults = true }
    
    fun fragment(packet: Packet): List<Fragment> {
        val serialized = json.encodeToString(packet).encodeToByteArray()
        
        if (serialized.size <= maxChunkSize) {
            return listOf(Fragment(0, 1, serialized))
        }
        
        val chunks = serialized.toList().chunked(maxChunkSize)
        return chunks.mapIndexed { index, chunk ->
            Fragment(index, chunks.size, chunk.toByteArray())
        }
    }
    
    fun reassemble(fragments: List<Fragment>): Packet {
        val sorted = fragments.sortedBy { it.sequenceNumber }
        val combined = sorted.flatMap { it.data.toList() }.toByteArray()
        val jsonString = combined.decodeToString()
        return json.decodeFromString(jsonString)
    }
}
