package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DialogStore(
    private val networkSession: NetworkSession,
    private val aiProvider: AIProvider,
    coroutineContext: CoroutineContext = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    
    private val _state = MutableStateFlow(SessionState(localPeerId = networkSession.localPeerId))
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    init {
        scope.launch {
            networkSession.incomingPackets.collect { packet ->
                reduce(packet)
            }
        }
    }
    
    fun accept(intent: Intent) {
        scope.launch {
            execute(intent)
        }
    }
    
    private suspend fun execute(intent: Intent) {
        when (intent) {
            is Intent.StartSoloGame -> {
                val packet = Packet(
                    type = PacketType.FULL_STATE_SNAPSHOT,
                    senderId = networkSession.localPeerId,
                    vectorClock = _state.value.vectorClock,
                    payload = PacketPayload.FullStateSnapshot(
                        _state.value.copy(
                            mode = SessionMode.SOLO,
                            connectionStatus = ConnectionStatus.CONNECTED,
                            scenario = intent.scenario,
                            roles = mapOf(
                                networkSession.localPeerId to intent.userRole,
                                "ai-robot" to Role("robot", "Robot", "AI partner")
                            ),
                            currentPhase = GamePhase.ACTIVE
                        )
                    )
                )
                networkSession.send(packet)
            }
            
            is Intent.HostGame -> {
                val packet = Packet(
                    type = PacketType.FULL_STATE_SNAPSHOT,
                    senderId = networkSession.localPeerId,
                    vectorClock = _state.value.vectorClock,
                    payload = PacketPayload.FullStateSnapshot(
                        _state.value.copy(
                            mode = SessionMode.HOST,
                            connectionStatus = ConnectionStatus.CONNECTING,
                            scenario = intent.scenario,
                            roles = mapOf(
                                networkSession.localPeerId to intent.userRole
                            ),
                            currentPhase = GamePhase.ACTIVE
                        )
                    )
                )
                networkSession.send(packet)
            }
            
            is Intent.JoinGame -> {
                val packet = Packet(
                    type = PacketType.FULL_STATE_SNAPSHOT,
                    senderId = networkSession.localPeerId,
                    vectorClock = _state.value.vectorClock,
                    payload = PacketPayload.FullStateSnapshot(
                        _state.value.copy(
                            mode = SessionMode.CLIENT,
                            connectionStatus = ConnectionStatus.CONNECTING,
                            roles = mapOf(
                                networkSession.localPeerId to intent.userRole
                            ),
                            currentPhase = GamePhase.WAITING
                        )
                    )
                )
                networkSession.send(packet)
            }
            
            is Intent.Generate -> {
                val currentState = _state.value
                val scenario = currentState.scenario ?: return
                
                val context = DialogContext(
                    scenario = scenario.name,
                    userRole = currentState.roles[networkSession.localPeerId]?.name ?: "User",
                    aiRole = currentState.roles["ai-robot"]?.name ?: "Robot",
                    previousLines = currentState.dialogHistory
                )
                
                val dialogLine = aiProvider.generate(context)
                
                val packet = Packet(
                    type = PacketType.DIALOG_LINE_ADDED,
                    senderId = networkSession.localPeerId,
                    vectorClock = currentState.vectorClock,
                    payload = PacketPayload.DialogLineAdded(dialogLine)
                )
                networkSession.send(packet)
            }
            
            is Intent.LeaveGame -> {
                networkSession.disconnect()
            }
            
            is Intent.RequestHint -> {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                _state.value = _state.value.copy(
                    recentFeedback = _state.value.recentFeedback + Feedback.PrivateNudge(
                        playerId = networkSession.localPeerId,
                        hintLevel = intent.level,
                        content = "Hint requested",
                        triggeredAt = now
                    )
                )
            }
            
            is Intent.TriggerPlotTwist -> {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val twist = PlotTwist(
                    id = "twist-${_state.value.vectorClock.timestamps.size}",
                    description = intent.description,
                    visualAsset = null,
                    triggeredAt = now,
                    affectsPlayers = _state.value.roles.keys.toList(),
                    expiresAt = null
                )
                _state.value = _state.value.copy(
                    activePlotTwist = twist,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.SetSecretObjective -> {
                val objective = SecretObjective(
                    playerId = intent.playerId,
                    objective = intent.objective
                )
                val currentContext = _state.value.playerContexts[intent.playerId] ?: PlayerContext(
                    playerId = intent.playerId,
                    proficiencyLevel = CEFRLevel.A1,
                    vocabularyHints = emptyList(),
                    grammarFocus = null,
                    secretObjective = null,
                    preferredComplexity = 1
                )
                val updatedContext = currentContext.copy(secretObjective = objective)
                _state.value = _state.value.copy(
                    playerContexts = _state.value.playerContexts + (intent.playerId to updatedContext),
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.EndSession -> {
                _state.value = _state.value.copy(
                    currentPhase = GamePhase.FINISHED,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
                networkSession.disconnect()
            }
            
            is Intent.StartAdvertising -> {
                _state.value = _state.value.copy(
                    isAdvertising = true,
                    currentPhase = GamePhase.LOBBY,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.StopAdvertising -> {
                _state.value = _state.value.copy(
                    isAdvertising = false,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.PeerConnected -> {
                val lobbyPlayer = LobbyPlayer(
                    peerId = intent.peerId,
                    displayName = intent.peerName,
                    assignedRole = null,
                    isReady = false
                )
                val connectedPeer = ConnectedPeer(
                    peerId = intent.peerId,
                    displayName = intent.peerName,
                    lastSeen = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                )
                _state.value = _state.value.copy(
                    lobbyPlayers = _state.value.lobbyPlayers + lobbyPlayer,
                    connectedPeers = _state.value.connectedPeers + connectedPeer,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.PeerDisconnected -> {
                _state.value = _state.value.copy(
                    lobbyPlayers = _state.value.lobbyPlayers.filter { it.peerId != intent.peerId },
                    connectedPeers = _state.value.connectedPeers.filter { it.peerId != intent.peerId },
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.DataReceived -> {
                // Deserialize and process incoming packet from BLE layer
                // This will be implemented when actual BLE transport is added
            }
            
            is Intent.CompleteLine -> {
                val currentState = _state.value
                val updatedHistory = currentState.dialogHistory.map { line ->
                    if (line.id == intent.lineId) {
                        line.copy(
                            visibility = LineVisibility.COMMITTED,
                            pronunciationResult = intent.result
                        )
                    } else line
                }
                
                val currentStats = currentState.playerStats[currentState.currentTurnPlayerId] 
                    ?: PlayerStats(playerId = currentState.currentTurnPlayerId ?: "")
                val updatedStats = currentStats.copy(
                    linesCompleted = currentStats.linesCompleted + 1,
                    totalErrors = currentStats.totalErrors + intent.result.errorCount,
                    perfectLines = if (intent.result.accuracy >= 1.0f) currentStats.perfectLines + 1 else currentStats.perfectLines,
                    currentStreak = if (intent.result.accuracy >= 1.0f) currentStats.currentStreak + 1 else 0
                )
                
                _state.value = currentState.copy(
                    dialogHistory = updatedHistory,
                    committedHistory = currentState.committedHistory + updatedHistory.last { it.id == intent.lineId },
                    pendingLine = null,
                    playerStats = currentState.playerStats + (updatedStats.playerId to updatedStats),
                    vectorClock = currentState.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.SkipLine -> {
                val currentState = _state.value
                val updatedHistory = currentState.dialogHistory.map { line ->
                    if (line.id == intent.lineId) {
                        line.copy(
                            visibility = LineVisibility.COMMITTED,
                            pronunciationResult = PronunciationResult.skipped()
                        )
                    } else line
                }
                _state.value = currentState.copy(
                    dialogHistory = updatedHistory,
                    committedHistory = currentState.committedHistory + updatedHistory.last { it.id == intent.lineId },
                    pendingLine = null,
                    vectorClock = currentState.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.AssignRole -> {
                val scenario = _state.value.scenario ?: return
                val role = scenario.availableRoles.find { it.id == intent.roleId } ?: return
                val updatedPlayers = _state.value.lobbyPlayers.map { player ->
                    if (player.peerId == intent.playerId) {
                        player.copy(assignedRole = role)
                    } else player
                }
                _state.value = _state.value.copy(
                    lobbyPlayers = updatedPlayers,
                    roles = _state.value.roles + (intent.playerId to role),
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.SetPlayerReady -> {
                val updatedPlayers = _state.value.lobbyPlayers.map { player ->
                    if (player.peerId == intent.playerId) {
                        player.copy(isReady = intent.isReady)
                    } else player
                }
                _state.value = _state.value.copy(
                    lobbyPlayers = updatedPlayers,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
            
            is Intent.StartMultiplayerGame -> {
                val allReady = _state.value.lobbyPlayers.all { it.isReady && it.assignedRole != null }
                if (!allReady) return
                
                val firstPlayer = _state.value.lobbyPlayers.firstOrNull() ?: return
                _state.value = _state.value.copy(
                    currentPhase = GamePhase.ACTIVE,
                    isAdvertising = false,
                    currentTurnPlayerId = firstPlayer.peerId,
                    vectorClock = _state.value.vectorClock.increment(networkSession.localPeerId)
                )
            }
        }
    }
    
    private fun reduce(packet: Packet) {
        val currentState = _state.value
        val mergedClock = currentState.vectorClock.merge(packet.vectorClock)
        
        val newState = when (val payload = packet.payload) {
            is PacketPayload.DialogLineAdded -> currentState.copy(
                dialogHistory = currentState.dialogHistory + payload.line,
                vectorClock = mergedClock
            )
            
            is PacketPayload.FullStateSnapshot -> payload.state.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.VoteRequest -> currentState.copy(
                pendingVote = PendingVote(
                    proposerId = payload.proposerId,
                    action = payload.action,
                    requiredVotes = currentState.peers.size
                ),
                currentPhase = GamePhase.VOTING,
                vectorClock = mergedClock
            )
            
            is PacketPayload.VoteCast -> currentState.copy(
                voteResults = currentState.voteResults + (payload.voterId to payload.vote),
                vectorClock = mergedClock
            )
            
            is PacketPayload.NavigationSync -> currentState.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.Handshake -> currentState.copy(
                peers = currentState.peers + payload.participant,
                vectorClock = mergedClock
            )
            
            is PacketPayload.Heartbeat -> currentState.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.GenerateRequest -> currentState.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.LineCompleted -> {
                val updatedHistory = currentState.dialogHistory.map { line ->
                    if (line.id == payload.lineId) {
                        line.copy(
                            visibility = LineVisibility.COMMITTED,
                            pronunciationResult = payload.result
                        )
                    } else line
                }
                currentState.copy(
                    dialogHistory = updatedHistory,
                    committedHistory = currentState.committedHistory + 
                        updatedHistory.first { it.id == payload.lineId },
                    pendingLine = null,
                    vectorClock = mergedClock
                )
            }
            
            is PacketPayload.LineSkipped -> {
                val updatedHistory = currentState.dialogHistory.map { line ->
                    if (line.id == payload.lineId) {
                        line.copy(
                            visibility = LineVisibility.COMMITTED,
                            pronunciationResult = PronunciationResult.skipped()
                        )
                    } else line
                }
                currentState.copy(
                    dialogHistory = updatedHistory,
                    committedHistory = currentState.committedHistory + 
                        updatedHistory.first { it.id == payload.lineId },
                    pendingLine = null,
                    vectorClock = mergedClock
                )
            }
            
            is PacketPayload.RoleAssigned -> {
                val scenario = currentState.scenario ?: return
                val role = scenario.availableRoles.find { it.id == payload.roleId } ?: return
                val updatedPlayers = currentState.lobbyPlayers.map { player ->
                    if (player.peerId == payload.playerId) {
                        player.copy(assignedRole = role)
                    } else player
                }
                currentState.copy(
                    lobbyPlayers = updatedPlayers,
                    roles = currentState.roles + (payload.playerId to role),
                    vectorClock = mergedClock
                )
            }
            
            is PacketPayload.PlayerReady -> {
                val updatedPlayers = currentState.lobbyPlayers.map { player ->
                    if (player.peerId == payload.playerId) {
                        player.copy(isReady = payload.isReady)
                    } else player
                }
                currentState.copy(
                    lobbyPlayers = updatedPlayers,
                    vectorClock = mergedClock
                )
            }
            
            is PacketPayload.GameStarted -> currentState.copy(
                currentPhase = GamePhase.ACTIVE,
                isAdvertising = false,
                vectorClock = mergedClock
            )
            
            is PacketPayload.TurnAdvanced -> currentState.copy(
                currentTurnPlayerId = payload.nextPlayerId,
                pendingLine = payload.pendingLine,
                vectorClock = mergedClock
            )
            
            is PacketPayload.LeaderboardUpdate -> currentState.copy(
                sessionLeaderboard = payload.leaderboard,
                vectorClock = mergedClock
            )
        }
        
        _state.value = newState
    }
    
    sealed class Intent {
        data class StartSoloGame(
            val scenario: Scenario,
            val userRole: Role
        ) : Intent()
        
        data class HostGame(
            val scenario: Scenario,
            val userRole: Role
        ) : Intent()
        
        data class JoinGame(
            val hostDeviceId: String,
            val userRole: Role
        ) : Intent()
        
        data object Generate : Intent()
        
        data object LeaveGame : Intent()
        
        data class RequestHint(val level: HintLevel) : Intent()
        
        data class TriggerPlotTwist(val description: String) : Intent()
        
        data class SetSecretObjective(
            val playerId: String,
            val objective: String
        ) : Intent()
        
        data object EndSession : Intent()
        
        data object StartAdvertising : Intent()
        
        data object StopAdvertising : Intent()
        
        data class PeerConnected(
            val peerId: String,
            val peerName: String
        ) : Intent()
        
        data class PeerDisconnected(val peerId: String) : Intent()
        
        data class DataReceived(
            val fromPeerId: String,
            val data: ByteArray
        ) : Intent() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as DataReceived
                return fromPeerId == other.fromPeerId && data.contentEquals(other.data)
            }
            override fun hashCode(): Int = 31 * fromPeerId.hashCode() + data.contentHashCode()
        }
        
        data class CompleteLine(
            val lineId: String,
            val result: PronunciationResult
        ) : Intent()
        
        data class SkipLine(val lineId: String) : Intent()
        
        data class AssignRole(
            val playerId: String,
            val roleId: String
        ) : Intent()
        
        data class SetPlayerReady(
            val playerId: String,
            val isReady: Boolean
        ) : Intent()
        
        data object StartMultiplayerGame : Intent()
    }
}
