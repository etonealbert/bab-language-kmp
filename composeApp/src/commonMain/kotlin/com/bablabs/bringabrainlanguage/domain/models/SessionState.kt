package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionState(
    val mode: SessionMode = SessionMode.SOLO,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val peers: List<Participant> = emptyList(),
    val localPeerId: String = "",
    
    val scenario: Scenario? = null,
    val roles: Map<String, Role> = emptyMap(),
    val dialogHistory: List<DialogLine> = emptyList(),
    val currentPhase: GamePhase = GamePhase.LOBBY,
    
    val pendingVote: PendingVote? = null,
    val voteResults: Map<String, Boolean> = emptyMap(),
    
    val vectorClock: VectorClock = VectorClock(),
    val lastSyncTimestamp: Long = 0L,
    
    val playerContexts: Map<String, PlayerContext> = emptyMap(),
    val activePlotTwist: PlotTwist? = null,
    val recentFeedback: List<Feedback> = emptyList(),
    val sessionStats: SessionStats? = null,
    
    val lobbyPlayers: List<LobbyPlayer> = emptyList(),
    val isAdvertising: Boolean = false,
    val connectedPeers: List<ConnectedPeer> = emptyList(),
    
    val currentTurnPlayerId: String? = null,
    val pendingLine: DialogLine? = null,
    val committedHistory: List<DialogLine> = emptyList(),
    
    val playerStats: Map<String, PlayerStats> = emptyMap(),
    val sessionLeaderboard: SessionLeaderboard? = null,
    val sessionSummary: SessionSummary? = null
)

@Serializable
enum class SessionMode { SOLO, HOST, CLIENT }

@Serializable
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

@Serializable
enum class GamePhase { LOBBY, ROLE_SELECTION, WAITING, ACTIVE, VOTING, FINISHED }

@Serializable
data class Scenario(
    val id: String,
    val name: String,
    val description: String,
    val availableRoles: List<Role>
)

@Serializable
data class PendingVote(
    val proposerId: String,
    val action: VoteAction,
    val requiredVotes: Int
)
