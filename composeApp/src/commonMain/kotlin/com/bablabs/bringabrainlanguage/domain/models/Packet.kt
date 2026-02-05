package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Packet(
    val type: PacketType,
    val senderId: String,
    val vectorClock: VectorClock,
    val payload: PacketPayload
)

@Serializable
enum class PacketType {
    HANDSHAKE,
    HEARTBEAT,
    GENERATE_REQUEST,
    DIALOG_LINE_ADDED,
    VOTE_REQUEST,
    VOTE_CAST,
    FULL_STATE_SNAPSHOT,
    NAVIGATION_SYNC
}

@Serializable
sealed class PacketPayload {
    
    @Serializable
    data class GenerateRequest(val prompt: String) : PacketPayload()
    
    @Serializable
    data class DialogLineAdded(val line: DialogLine) : PacketPayload()
    
    @Serializable
    data class VoteRequest(
        val proposerId: String,
        val action: VoteAction
    ) : PacketPayload()
    
    @Serializable
    data class VoteCast(
        val voterId: String,
        val vote: Boolean
    ) : PacketPayload()
    
    @Serializable
    data class FullStateSnapshot(val state: SessionState) : PacketPayload()
    
    @Serializable
    data class NavigationSync(val screenName: String) : PacketPayload()
    
    @Serializable
    data object Heartbeat : PacketPayload()
    
    @Serializable
    data class Handshake(val participant: Participant) : PacketPayload()
}

@Serializable
enum class VoteAction {
    CHANGE_SCENE,
    END_GAME,
    SKIP_LINE
}
