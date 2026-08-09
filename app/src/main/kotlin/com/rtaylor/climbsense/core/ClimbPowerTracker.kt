package com.rtaylor.climbsense.core

/**
 * Average power for the active climb: arithmetic mean of ~1 Hz power samples
 * from the moment the climb started (zeros included, like native avg power).
 * [climbKey] identifies the climb (its start distance); null means off-climb.
 */
class ClimbPowerTracker {
    private var currentKey: Double? = null
    private var sum = 0.0
    private var count = 0

    fun sample(climbKey: Double?, watts: Double?): Double? {
        if (climbKey == null) {
            reset(null)
            return null
        }
        if (climbKey != currentKey) reset(climbKey)
        if (watts != null) {
            sum += watts
            count++
        }
        return if (count > 0) sum / count else null
    }

    private fun reset(key: Double?) {
        currentKey = key
        sum = 0.0
        count = 0
    }
}
