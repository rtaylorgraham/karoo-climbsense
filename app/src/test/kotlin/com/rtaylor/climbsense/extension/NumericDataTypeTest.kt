package com.rtaylor.climbsense.extension

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NumericDataTypeTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val emitted = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) {
            emitted.add(t)
        }
        override fun onError(t: Throwable) = Unit
        override fun onComplete() = Unit
        override fun setCancellable(cancellable: () -> Unit) = Unit
        override fun cancel() = Unit
    }

    private class TestDataType(
        scope: kotlinx.coroutines.CoroutineScope,
        private val source: Flow<Double?>,
    ) : NumericDataType("climbsense", "test-field", scope) {
        override fun values(): Flow<Double?> = source
    }

    @Test
    fun `emits NotAvailable immediately even when the source flow is silent`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val silentSource = MutableSharedFlow<Double?>() // never emits
        val emitter = RecordingEmitter()

        TestDataType(scope, silentSource).startStream(emitter)
        scope.advanceUntilIdle()

        assertEquals(1, emitter.emitted.size)
        assertTrue(emitter.emitted.first() is StreamState.NotAvailable)
    }

    @Test
    fun `streams rounded values after the initial NotAvailable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val source = MutableSharedFlow<Double?>()
        val emitter = RecordingEmitter()

        TestDataType(scope, source).startStream(emitter)
        scope.advanceUntilIdle()
        source.emit(6.24)
        scope.advanceUntilIdle()

        assertEquals(2, emitter.emitted.size)
        val streaming = emitter.emitted[1] as StreamState.Streaming
        assertEquals(6.2, streaming.dataPoint.singleValue!!, 1e-9)
    }

    @Test
    fun `re-emits equal values at source cadence so the display self-heals`() = runTest {
        // The Karoo display is last-write-wins vs the view-side "--" message; a value
        // that rounds identically for minutes must still keep streaming.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val source = MutableSharedFlow<Double?>()
        val emitter = RecordingEmitter()

        TestDataType(scope, source).startStream(emitter)
        scope.advanceUntilIdle()
        source.emit(8.31)
        scope.advanceUntilIdle()
        source.emit(8.33) // rounds to the same 8.3
        scope.advanceUntilIdle()

        assertEquals(3, emitter.emitted.size)
        assertEquals(8.3, (emitter.emitted[2] as StreamState.Streaming).dataPoint.singleValue!!, 1e-9)
    }
}


