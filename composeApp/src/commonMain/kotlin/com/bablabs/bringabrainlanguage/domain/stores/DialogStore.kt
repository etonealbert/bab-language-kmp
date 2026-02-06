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
    }
}
