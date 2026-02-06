package com.bablabs.bringabrainlanguage.domain.services

import com.bablabs.bringabrainlanguage.domain.models.XPBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XPCalculatorTest {
    
    @Test
    fun `calculateSessionXP returns base dialog completion XP`() {
        val breakdown = XPCalculator.calculateSessionXP(
            linesSpoken = 0,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(50, breakdown.dialogCompletion)
    }
    
    @Test
    fun `calculateSessionXP awards 10 XP per new vocabulary word`() {
        val breakdown = XPCalculator.calculateSessionXP(
            linesSpoken = 5,
            newVocabulary = 3,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(30, breakdown.vocabularyBonus)
    }
    
    @Test
    fun `calculateSessionXP calculates accuracy bonus based on error rate`() {
        val noErrors = XPCalculator.calculateSessionXP(
            linesSpoken = 10,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(30, noErrors.accuracyBonus)
        
        val halfErrors = XPCalculator.calculateSessionXP(
            linesSpoken = 10,
            newVocabulary = 0,
            errorsDetected = 5,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(15, halfErrors.accuracyBonus)
    }
    
    @Test
    fun `calculateSessionXP gives zero accuracy bonus when no lines spoken`() {
        val breakdown = XPCalculator.calculateSessionXP(
            linesSpoken = 0,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(0, breakdown.accuracyBonus)
    }
    
    @Test
    fun `calculateSessionXP awards collaboration bonus only in multiplayer`() {
        val solo = XPCalculator.calculateSessionXP(
            linesSpoken = 5,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 2,
            currentStreak = 0
        )
        
        assertEquals(0, solo.collaborationBonus)
        
        val multiplayer = XPCalculator.calculateSessionXP(
            linesSpoken = 5,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = true,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(25, multiplayer.collaborationBonus)
    }
    
    @Test
    fun `calculateSessionXP awards extra collaboration XP for help given`() {
        val breakdown = XPCalculator.calculateSessionXP(
            linesSpoken = 5,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = true,
            helpGiven = 3,
            currentStreak = 0
        )
        
        assertEquals(55, breakdown.collaborationBonus)
    }
    
    @Test
    fun `calculateSessionXP applies streak bonus at 5 percent per day`() {
        val noStreak = XPCalculator.calculateSessionXP(
            linesSpoken = 0,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 0
        )
        
        assertEquals(0, noStreak.streakBonus)
        
        val twoDayStreak = XPCalculator.calculateSessionXP(
            linesSpoken = 0,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 2
        )
        
        assertEquals(5, twoDayStreak.streakBonus)
    }
    
    @Test
    fun `calculateSessionXP caps streak bonus at 50 percent`() {
        val tenDayStreak = XPCalculator.calculateSessionXP(
            linesSpoken = 10,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 10
        )
        
        val twentyDayStreak = XPCalculator.calculateSessionXP(
            linesSpoken = 10,
            newVocabulary = 0,
            errorsDetected = 0,
            errorsCorrected = 0,
            isMultiplayer = false,
            helpGiven = 0,
            currentStreak = 20
        )
        
        assertEquals(tenDayStreak.streakBonus, twentyDayStreak.streakBonus)
    }
    
    @Test
    fun `total returns sum of all XP components`() {
        val breakdown = XPBreakdown(
            dialogCompletion = 50,
            vocabularyBonus = 20,
            accuracyBonus = 15,
            collaborationBonus = 25,
            streakBonus = 10
        )
        
        assertEquals(120, breakdown.total)
    }
    
    @Test
    fun `levelFromXP returns level 1 for zero XP`() {
        assertEquals(1, XPCalculator.levelFromXP(0))
    }
    
    @Test
    fun `levelFromXP calculates level correctly for small XP values`() {
        assertEquals(1, XPCalculator.levelFromXP(50))
        assertEquals(1, XPCalculator.levelFromXP(99))
        assertEquals(2, XPCalculator.levelFromXP(100))
        assertEquals(2, XPCalculator.levelFromXP(299))
        assertEquals(3, XPCalculator.levelFromXP(300))
    }
    
    @Test
    fun `levelFromXP handles large XP values`() {
        val level = XPCalculator.levelFromXP(10000)
        assertTrue(level > 10)
    }
}
