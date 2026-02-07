package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DialogStoreTurnAndXPTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    private fun createTestNetworkSession(peerId: String = "test-player"): TestNetworkSession {
        return TestNetworkSession(peerId)
    }
    
    private fun createStore(
        networkSession: NetworkSession = createTestNetworkSession(),
        aiProvider: AIProvider = TestAIProvider()
    ): DialogStore {
        return DialogStore(
            networkSession = networkSession,
            aiProvider = aiProvider,
            coroutineContext = testDispatcher
        )
    }
    
    @Test
    fun completeLineCalculatesXP() = runTest(testDispatcher) {
        val store = createStore()
        
        val scenario = Scenario(
            id = "test",
            name = "Test",
            description = "Test scenario",
            availableRoles = listOf(Role("role1", "Role", "Test role"))
        )
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles[0]))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        
        val lineId = store.state.value.dialogHistory.firstOrNull()?.id ?: return@runTest
        
        val result = PronunciationResult(
            accuracy = 1.0f,
            duration = 2000L,
            errorCount = 0,
            wordErrors = emptyList(),
            skipped = false
        )
        store.accept(DialogStore.Intent.CompleteLine(lineId, result))
        advanceUntilIdle()
        
        val state = store.state.value
        val stats = state.playerStats.values.firstOrNull()
        
        assertTrue(stats != null && stats.xpEarned > 0, "XP should be calculated")
        assertEquals(1, stats?.linesCompleted)
        assertEquals(1, stats?.perfectLines)
    }
    
    @Test
    fun completeLineUpdatesStreak() = runTest(testDispatcher) {
        val store = createStore()
        
        val scenario = Scenario(
            id = "test",
            name = "Test",
            description = "Test",
            availableRoles = listOf(Role("role1", "Role", "Test"))
        )
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles[0]))
        advanceUntilIdle()
        
        repeat(3) { i ->
            store.accept(DialogStore.Intent.Generate)
            advanceUntilIdle()
            
            val lineId = store.state.value.dialogHistory.lastOrNull()?.id ?: return@runTest
            val result = PronunciationResult(
                accuracy = 0.95f,
                duration = 2000L,
                errorCount = 0,
                wordErrors = emptyList(),
                skipped = false
            )
            store.accept(DialogStore.Intent.CompleteLine(lineId, result))
            advanceUntilIdle()
        }
        
        val stats = store.state.value.playerStats.values.firstOrNull()
        assertEquals(3, stats?.currentStreak)
    }
    
    @Test
    fun lowAccuracyBreaksStreak() = runTest(testDispatcher) {
        val store = createStore()
        
        val scenario = Scenario(
            id = "test",
            name = "Test",
            description = "Test",
            availableRoles = listOf(Role("role1", "Role", "Test"))
        )
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles[0]))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        val line1Id = store.state.value.dialogHistory.lastOrNull()?.id ?: return@runTest
        store.accept(DialogStore.Intent.CompleteLine(line1Id, PronunciationResult(
            accuracy = 0.95f, duration = 2000L, errorCount = 0, wordErrors = emptyList(), skipped = false
        )))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        val line2Id = store.state.value.dialogHistory.lastOrNull()?.id ?: return@runTest
        store.accept(DialogStore.Intent.CompleteLine(line2Id, PronunciationResult(
            accuracy = 0.5f, duration = 2000L, errorCount = 5, wordErrors = emptyList(), skipped = false
        )))
        advanceUntilIdle()
        
        val stats = store.state.value.playerStats.values.firstOrNull()
        assertEquals(0, stats?.currentStreak)
    }
    
    @Test
    fun leaderboardUpdatesAfterLineCompletion() = runTest(testDispatcher) {
        val store = createStore()
        
        val scenario = Scenario(
            id = "test",
            name = "Test",
            description = "Test",
            availableRoles = listOf(Role("role1", "Role", "Test"))
        )
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles[0]))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        
        val lineId = store.state.value.dialogHistory.lastOrNull()?.id ?: return@runTest
        store.accept(DialogStore.Intent.CompleteLine(lineId, PronunciationResult(
            accuracy = 1.0f, duration = 2000L, errorCount = 0, wordErrors = emptyList(), skipped = false
        )))
        advanceUntilIdle()
        
        val leaderboard = store.state.value.sessionLeaderboard
        assertTrue(leaderboard != null && leaderboard.rankings.isNotEmpty())
        assertEquals(1, leaderboard?.rankings?.first()?.rank)
    }
    
    class TestNetworkSession(override val localPeerId: String) : NetworkSession {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state
        
        private val _incomingPackets = MutableSharedFlow<Packet>()
        override val incomingPackets: Flow<Packet> = _incomingPackets
        
        val sentPackets = mutableListOf<Packet>()
        
        override suspend fun send(packet: Packet, recipientId: String?) {
            sentPackets.add(packet)
            _incomingPackets.emit(packet)
        }
        
        override suspend fun disconnect() {
            _state.value = ConnectionState.Disconnected
        }
    }
    
    class TestAIProvider : AIProvider {
        private var lineCounter = 0
        
        override suspend fun generate(context: DialogContext): DialogLine {
            lineCounter++
            return DialogLine(
                id = "line-$lineCounter",
                speakerId = "ai",
                roleName = context.aiRole,
                textNative = "Línea generada $lineCounter",
                textTranslated = "Generated line $lineCounter",
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        }
        
        override fun streamGenerate(context: DialogContext): kotlinx.coroutines.flow.Flow<String> {
            return kotlinx.coroutines.flow.flowOf("Generated line")
        }
    }
}
