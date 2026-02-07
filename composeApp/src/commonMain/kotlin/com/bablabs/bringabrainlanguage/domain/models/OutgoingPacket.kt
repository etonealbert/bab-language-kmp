package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class OutgoingPacket(
    val targetPeerId: String?,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as OutgoingPacket
        return targetPeerId == other.targetPeerId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = targetPeerId?.hashCode() ?: 0
        result = 31 * result + data.contentHashCode()
        return result
    }
    
    companion object {
        fun broadcast(data: ByteArray) = OutgoingPacket(targetPeerId = null, data = data)
        fun unicast(peerId: String, data: ByteArray) = OutgoingPacket(targetPeerId = peerId, data = data)
    }
}
