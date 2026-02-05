package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import kotlinx.coroutines.flow.Flow

interface AIProvider {
    suspend fun generate(context: DialogContext): DialogLine
    fun streamGenerate(context: DialogContext): Flow<String>
}

data class DialogContext(
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val previousLines: List<DialogLine>,
    val targetLanguage: String = "Spanish",
    val nativeLanguage: String = "English"
)
