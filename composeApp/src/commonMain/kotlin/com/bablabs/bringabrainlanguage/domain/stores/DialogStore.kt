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

class DialogStore(
    private val networkSession: NetworkSession,
    private val aiProvider: AIProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
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
        
        data object Generate : Intent()
        
        data object LeaveGame : Intent()
    }
}
