package com.viberunning.data.model

/**
 * Personal records on the Stats screen. A run can be excluded from any of them
 * individually (e.g. a GPS glitch that produced an impossible top speed) by setting
 * [flag] in [Activity.excludedRecords]. The record queries in ActivityDao use these
 * flag values directly.
 */
enum class PersonalRecord(val flag: Int, val label: String) {
    TOP_SPEED(1, "Top Speed"),
    LONGEST_DISTANCE(2, "Longest Distance"),
    BEST_PACE(4, "Best Pace"),
    LONGEST_RUN(8, "Longest Run");

    companion object {
        val ALL_FLAGS = entries.sumOf { it.flag }
    }
}
