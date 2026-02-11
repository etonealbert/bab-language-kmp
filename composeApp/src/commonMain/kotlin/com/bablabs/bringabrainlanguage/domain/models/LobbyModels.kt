package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class LobbyPlayer(
    val peerId: String,
    val displayName: String,
    val assignedRole: Role?,
    val isReady: Boolean,
    val connectionQuality: ConnectionQuality = ConnectionQuality.GOOD
)

@Serializable
enum class ConnectionQuality { 
    EXCELLENT, 
    GOOD, 
    FAIR, 
    POOR 
}

@Serializable
data class ConnectedPeer(
    val peerId: String,
    val displayName: String,
    val isHost: Boolean = false,
    val lastSeen: Long = 0L
)

/**
 * Lightweight player identity used during lobby phase for state sync.
 * 
 * Sent via [PacketPayload.ClientJoin] when a client connects to the host.
 */
@Serializable
data class PlayerProfile(
    val playerId: String,
    val displayName: String,
    val avatarId: String? = null
)

/**
 * Pre-session lobby state synchronized between Host and all Clients.
 * 
 * The Host updates this state (scenario, difficulty, readiness) and broadcasts
 * it to all connected Clients via [PacketPayload.LobbyUpdate]. Clients observe
 * changes through [SessionState.lobbyState].
 */
@Serializable
data class LobbyState(
    val selectedScenarioId: String = "",
    val difficultyLevel: String = "normal",
    val hostReady: Boolean = false,
    val connectedPlayers: List<PlayerProfile> = emptyList()
)
