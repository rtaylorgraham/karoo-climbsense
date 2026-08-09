package com.rtaylor.climbsense.extension

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Base for plain numeric gradient fields: streams a nullable percentage,
 * rendered natively by Karoo with the built-in grade formatting (1 decimal).
 *
 * Display taxonomy learned on device:
 * - no stream attached -> "No Sensor"; StreamState.NotAvailable -> "No Sensor";
 *   StreamState.Idle -> "0"; Streaming(v) -> v.
 * - ShowCustomStreamState("--") overrides the no-value text, but is cleared once
 *   values stream and the display is last-write-wins — so the dashes are re-emitted
 *   shortly AFTER each transition back to no-value.
 */
abstract class NumericDataType(
    extension: String,
    typeId: String,
    private val scope: CoroutineScope,
    private val formatDataTypeId: String = DataType.Type.ELEVATION_GRADE,
) : DataTypeImpl(extension, typeId) {

    /** The field's value stream; null means "no current value" (shown as --). */
    protected abstract fun values(): Flow<Double?>

    /** How raw values become display values (default: gradient, 1 decimal half-up). */
    protected open fun displayValue(value: Double): Double = StreamRules.roundTenth(value)

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = scope.launch {
            values()
                // Emit immediately so the tile never sits on a silent stream
                .onStart { emit(null) }
                .map { value -> value?.let(::displayValue) }
                // No distinctUntilChanged: keep streaming at source cadence (~1 Hz)
                // like native sensors so the display always reflects current state.
                .collect { value ->
                    emitter.onNext(
                        if (value == null) {
                            StreamState.NotAvailable
                        } else {
                            StreamState.Streaming(
                                DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value)),
                            )
                        },
                    )
                }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Borrow a native field's formatting (grade: percent 1-decimal; power: watts)
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = formatDataTypeId))
        emitter.onNext(customDashes())
        val job = scope.launch {
            values()
                .map { it == null }
                .distinctUntilChanged()
                .collectLatest { isNull ->
                    if (isNull) {
                        // Land AFTER the stream-side NotAvailable (last-write-wins);
                        // collectLatest cancels this if a value returns first.
                        delay(600)
                        emitter.onNext(customDashes())
                    }
                }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun customDashes() =
        // Mid-gray: readable on both the night theme's black tiles and the day
        // theme's white tiles (white text disappears in day mode, null renders
        // invisibly at night)
        ShowCustomStreamState(message = "--", color = 0xFF8A8A8A.toInt())
}
