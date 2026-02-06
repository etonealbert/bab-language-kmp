package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class VectorClock(val timestamps: Map<String, Long> = emptyMap()) {
    
    fun increment(peerId: String): VectorClock {
        val next = (timestamps[peerId] ?: 0L) + 1
        return VectorClock(timestamps + (peerId to next))
    }

    fun merge(other: VectorClock): VectorClock {
        val allKeys = timestamps.keys + other.timestamps.keys
        val merged = allKeys.associateWith { key ->
            maxOf(timestamps[key] ?: 0L, other.timestamps[key] ?: 0L)
        }
        return VectorClock(merged)
    }

    fun compare(other: VectorClock): Comparison {
        val keys = timestamps.keys + other.timestamps.keys
        var hasGreater = false
        var hasSmaller = false
        for (key in keys) {
            val local = timestamps[key] ?: 0L
            val remote = other.timestamps[key] ?: 0L
            if (local > remote) hasGreater = true
            if (local < remote) hasSmaller = true
        }
        return when {
            hasGreater && hasSmaller -> Comparison.CONCURRENT
            hasGreater -> Comparison.AFTER
            hasSmaller -> Comparison.BEFORE
            else -> Comparison.EQUAL
        }
    }

    enum class Comparison { BEFORE, AFTER, CONCURRENT, EQUAL }
}
