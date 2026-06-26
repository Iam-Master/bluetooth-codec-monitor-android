package com.iammaster.codecmonitor.data.stability

import java.util.ArrayDeque

enum class StabilityLabel(val display: String) {
    STABLE("Stable"),
    OCCASIONAL_DROPS("Occasional drops"),
    UNSTABLE("Unstable")
}

data class StabilityStatus(val label: StabilityLabel, val eventsIn10Min: Int)

/**
 * Port of monitor.py's compute_connection_stability: tracks disconnect/downgrade
 * timestamps in a 10-minute rolling window.
 */
class StabilityTracker {
    private val events = ArrayDeque<Long>()
    private val lock = Any()

    companion object {
        private const val WINDOW_MS = 10L * 60_000
        private const val MAX_EVENTS = 200

        val CODEC_RANK = mapOf(
            "PCM" to 5,
            "LDAC" to 4,
            "aptX HD" to 3,
            "aptX" to 2,
            "AAC" to 1,
            "SBC" to 0
        )
    }

    fun recordEvent(atMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            events.addLast(atMs)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    /** Returns true if [to] is a downgrade relative to [from] by codec rank. */
    fun isDowngrade(from: String?, to: String?): Boolean {
        val fromRank = CODEC_RANK[from] ?: return false
        val toRank = CODEC_RANK[to] ?: return false
        return toRank < fromRank
    }

    fun computeStability(now: Long = System.currentTimeMillis()): StabilityStatus {
        val recent = synchronized(lock) { events.filter { now - it <= WINDOW_MS } }
        val n = recent.size
        val label = when {
            n == 0 -> StabilityLabel.STABLE
            n <= 2 -> StabilityLabel.OCCASIONAL_DROPS
            else -> StabilityLabel.UNSTABLE
        }
        return StabilityStatus(label, n)
    }
}
