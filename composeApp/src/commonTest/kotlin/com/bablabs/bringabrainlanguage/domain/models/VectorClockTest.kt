package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class VectorClockTest {
    
    @Test
    fun `increment increases peer timestamp by 1`() {
        val clock = VectorClock()
        val incremented = clock.increment("peer-a")
        
        assertEquals(1L, incremented.timestamps["peer-a"])
    }
    
    @Test
    fun `increment preserves other peer timestamps`() {
        val clock = VectorClock(mapOf("peer-a" to 5L, "peer-b" to 3L))
        val incremented = clock.increment("peer-a")
        
        assertEquals(6L, incremented.timestamps["peer-a"])
        assertEquals(3L, incremented.timestamps["peer-b"])
    }
    
    @Test
    fun `merge takes max of each peer timestamp`() {
        val clock1 = VectorClock(mapOf("peer-a" to 5L, "peer-b" to 3L))
        val clock2 = VectorClock(mapOf("peer-a" to 2L, "peer-b" to 7L, "peer-c" to 1L))
        
        val merged = clock1.merge(clock2)
        
        assertEquals(5L, merged.timestamps["peer-a"])
        assertEquals(7L, merged.timestamps["peer-b"])
        assertEquals(1L, merged.timestamps["peer-c"])
    }
    
    @Test
    fun `compare detects BEFORE relationship`() {
        val older = VectorClock(mapOf("peer-a" to 1L))
        val newer = VectorClock(mapOf("peer-a" to 2L))
        
        assertEquals(VectorClock.Comparison.BEFORE, older.compare(newer))
    }
    
    @Test
    fun `compare detects AFTER relationship`() {
        val newer = VectorClock(mapOf("peer-a" to 2L))
        val older = VectorClock(mapOf("peer-a" to 1L))
        
        assertEquals(VectorClock.Comparison.AFTER, newer.compare(older))
    }
    
    @Test
    fun `compare detects CONCURRENT relationship`() {
        val clock1 = VectorClock(mapOf("peer-a" to 2L, "peer-b" to 1L))
        val clock2 = VectorClock(mapOf("peer-a" to 1L, "peer-b" to 2L))
        
        assertEquals(VectorClock.Comparison.CONCURRENT, clock1.compare(clock2))
    }
    
    @Test
    fun `compare detects EQUAL relationship`() {
        val clock1 = VectorClock(mapOf("peer-a" to 2L))
        val clock2 = VectorClock(mapOf("peer-a" to 2L))
        
        assertEquals(VectorClock.Comparison.EQUAL, clock1.compare(clock2))
    }
}
