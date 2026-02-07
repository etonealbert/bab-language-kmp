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
    NAVIGATION_SYNC,
    LINE_COMPLETED,
    LINE_SKIPPED,
    ROLE_ASSIGNED,
    PLAYER_READY,
    GAME_STARTED,
    TURN_ADVANCED,
    LEADERBOARD_UPDATE
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
    
    @Serializable
    data class LineCompleted(
        val lineId: String,
        val result: PronunciationResult
    ) : PacketPayload()
    
    @Serializable
    data class LineSkipped(val lineId: String) : PacketPayload()
    
    @Serializable
    data class RoleAssigned(
        val playerId: String,
        val roleId: String
    ) : PacketPayload()
    
    @Serializable
    data class PlayerReady(
        val playerId: String,
        val isReady: Boolean
    ) : PacketPayload()
    
    @Serializable
    data object GameStarted : PacketPayload()
    
    @Serializable
    data class TurnAdvanced(
        val nextPlayerId: String,
        val pendingLine: DialogLine?
    ) : PacketPayload()
    
    @Serializable
    data class LeaderboardUpdate(
        val leaderboard: SessionLeaderboard
    ) : PacketPayload()
}

@Serializable
enum class VoteAction {
    CHANGE_SCENE,
    END_GAME,
    SKIP_LINE
}
