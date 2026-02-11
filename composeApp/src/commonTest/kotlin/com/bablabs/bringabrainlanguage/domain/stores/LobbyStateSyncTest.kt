package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyStateSyncTest {

    private fun createStore(testDispatcher: kotlinx.coroutines.test.TestDispatcher): DialogStore {
        return DialogStore(
            networkSession = LoopbackNetworkSession("host-local"),
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
    }

    // ==================== Intent Tests ====================

    @Test
    fun setLobbyScenarioUpdatesState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.accept(DialogStore.Intent.SetLobbyScenario("coffee-shop"))
        advanceUntilIdle()

        assertEquals("coffee-shop", store.state.value.lobbyState.selectedScenarioId)
    }

    @Test
    fun setLobbyDifficultyUpdatesState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.accept(DialogStore.Intent.SetLobbyDifficulty("hard"))
        advanceUntilIdle()

        assertEquals("hard", store.state.value.lobbyState.difficultyLevel)
    }

    @Test
    fun clientJoinAddsPlayerProfile() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        val profile = PlayerProfile(
            playerId = "player-1",
            displayName = "Alice"
        )

        store.accept(DialogStore.Intent.ClientJoinLobby(profile))
        advanceUntilIdle()

        val lobby = store.state.value.lobbyState
        assertEquals(1, lobby.connectedPlayers.size)
        assertEquals("player-1", lobby.connectedPlayers.first().playerId)
        assertEquals("Alice", lobby.connectedPlayers.first().displayName)
    }

    @Test
    fun multipleClientsJoinAccumulate() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.accept(DialogStore.Intent.ClientJoinLobby(
            PlayerProfile("p1", "Alice")
        ))
        store.accept(DialogStore.Intent.ClientJoinLobby(
            PlayerProfile("p2", "Bob")
        ))
        advanceUntilIdle()

        assertEquals(2, store.state.value.lobbyState.connectedPlayers.size)
    }

    @Test
    fun startGameTransitionsPhaseWhenScenarioSelected() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.accept(DialogStore.Intent.SetLobbyScenario("coffee-shop"))
        advanceUntilIdle()

        store.accept(DialogStore.Intent.StartGame)
        advanceUntilIdle()

        assertEquals(GamePhase.ACTIVE, store.state.value.currentPhase)
        assertFalse(store.state.value.isAdvertising)
    }

    @Test
    fun startGameBlockedWhenNoScenarioSelected() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        // Do NOT set scenario — lobbyState.selectedScenarioId is empty
        store.accept(DialogStore.Intent.StartGame)
        advanceUntilIdle()

        assertEquals(GamePhase.LOBBY, store.state.value.currentPhase)
    }

    @Test
    fun lobbyStateDefaultValues() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        val lobby = store.state.value.lobbyState
        assertEquals("", lobby.selectedScenarioId)
        assertEquals("normal", lobby.difficultyLevel)
        assertFalse(lobby.hostReady)
        assertTrue(lobby.connectedPlayers.isEmpty())
    }

    // ==================== Reducer Tests ====================

    @Test
    fun lobbyUpdatePacketReducesCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        val lobbyState = LobbyState(
            selectedScenarioId = "the-heist",
            difficultyLevel = "expert",
            hostReady = true,
            connectedPlayers = listOf(PlayerProfile("host-1", "Host"))
        )

        val packet = Packet(
            type = PacketType.LOBBY_UPDATE,
            senderId = "remote-host",
            vectorClock = VectorClock(),
            payload = PacketPayload.LobbyUpdate(lobbyState)
        )
        val data = PacketSerializer.encode(packet)

        store.accept(DialogStore.Intent.DataReceived("remote-host", data))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("the-heist", state.lobbyState.selectedScenarioId)
        assertEquals("expert", state.lobbyState.difficultyLevel)
        assertTrue(state.lobbyState.hostReady)
        assertEquals(1, state.lobbyState.connectedPlayers.size)
    }

    @Test
    fun clientJoinPacketReducesCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        val profile = PlayerProfile("new-player", "Charlie", "avatar-3")

        val packet = Packet(
            type = PacketType.CLIENT_JOIN,
            senderId = "new-player",
            vectorClock = VectorClock(),
            payload = PacketPayload.ClientJoin(profile)
        )
        val data = PacketSerializer.encode(packet)

        store.accept(DialogStore.Intent.DataReceived("new-player", data))
        advanceUntilIdle()

        val lobby = store.state.value.lobbyState
        assertEquals(1, lobby.connectedPlayers.size)
        assertEquals("new-player", lobby.connectedPlayers.first().playerId)
        assertEquals("avatar-3", lobby.connectedPlayers.first().avatarId)
    }

    @Test
    fun startGamePacketReducesCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        // First put into advertising state
        store.accept(DialogStore.Intent.StartAdvertising)
        advanceUntilIdle()
        assertTrue(store.state.value.isAdvertising)

        val packet = Packet(
            type = PacketType.START_GAME,
            senderId = "host",
            vectorClock = VectorClock(),
            payload = PacketPayload.StartGame
        )
        val data = PacketSerializer.encode(packet)

        store.accept(DialogStore.Intent.DataReceived("host", data))
        advanceUntilIdle()

        assertEquals(GamePhase.ACTIVE, store.state.value.currentPhase)
        assertFalse(store.state.value.isAdvertising)
    }

    // ==================== Serialization Roundtrip ====================

    @Test
    fun lobbyPacketsSerializationRoundTrip() {
        val lobbyState = LobbyState(
            selectedScenarioId = "coffee-shop",
            difficultyLevel = "hard",
            hostReady = true,
            connectedPlayers = listOf(
                PlayerProfile("p1", "Alice", "av1"),
                PlayerProfile("p2", "Bob")
            )
        )

        // LobbyUpdate roundtrip
        val lobbyPacket = Packet(
            type = PacketType.LOBBY_UPDATE,
            senderId = "host",
            vectorClock = VectorClock(mapOf("host" to 1)),
            payload = PacketPayload.LobbyUpdate(lobbyState)
        )
        val encodedLobby = PacketSerializer.encode(lobbyPacket)
        val decodedLobby = PacketSerializer.decodeOrThrow(encodedLobby)
        assertEquals(PacketType.LOBBY_UPDATE, decodedLobby.type)
        val lobbyPayload = decodedLobby.payload as PacketPayload.LobbyUpdate
        assertEquals("coffee-shop", lobbyPayload.lobbyState.selectedScenarioId)
        assertEquals(2, lobbyPayload.lobbyState.connectedPlayers.size)

        // ClientJoin roundtrip
        val joinPacket = Packet(
            type = PacketType.CLIENT_JOIN,
            senderId = "client",
            vectorClock = VectorClock(),
            payload = PacketPayload.ClientJoin(PlayerProfile("c1", "Charlie"))
        )
        val encodedJoin = PacketSerializer.encode(joinPacket)
        val decodedJoin = PacketSerializer.decodeOrThrow(encodedJoin)
        assertEquals(PacketType.CLIENT_JOIN, decodedJoin.type)
        val joinPayload = decodedJoin.payload as PacketPayload.ClientJoin
        assertEquals("c1", joinPayload.profile.playerId)

        // StartGame roundtrip
        val startPacket = Packet(
            type = PacketType.START_GAME,
            senderId = "host",
            vectorClock = VectorClock(),
            payload = PacketPayload.StartGame
        )
        val encodedStart = PacketSerializer.encode(startPacket)
        val decodedStart = PacketSerializer.decodeOrThrow(encodedStart)
        assertEquals(PacketType.START_GAME, decodedStart.type)
        assertTrue(decodedStart.payload is PacketPayload.StartGame)
    }
}
