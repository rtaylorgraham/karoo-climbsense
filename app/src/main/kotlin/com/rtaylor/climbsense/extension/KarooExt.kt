package com.rtaylor.climbsense.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.take

/**
 * Flow adapters over the callback-based consumer API — the community-standard
 * helpers from the karoo-ext sample app's Extensions.kt.
 */

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        trySendBlocking(event.state)
    }
    awaitClose { removeConsumer(listenerId) }
}

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val listenerId = addConsumer<T> { event ->
        trySendBlocking(event)
    }
    awaitClose { removeConsumer(listenerId) }
}

/**
 * Defer a cold Karoo flow's consumer registration until the Karoo System
 * connection handshake has completed. Registering several consumers during
 * onCreate races the async connect and can silently lose subscriptions
 * (observed on device: OnNavigationState never delivering on some boots).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.afterConnected(connected: StateFlow<Boolean>): Flow<T> =
    connected.filter { it }.take(1).flatMapLatest { this }
