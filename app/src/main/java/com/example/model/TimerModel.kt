package com.example.model

data class ActiveTimer(
    val id: String,
    val durationMillis: Long,
    val triggerAtMillis: Long,
    val labelBn: String,
    val labelEn: String,
    val createdAtMillis: Long,
    val isCompleted: Boolean = false
) {
    fun remainingMillis(currentTime: Long = System.currentTimeMillis()): Long {
        return (triggerAtMillis - currentTime).coerceAtLeast(0L)
    }

    fun progress(currentTime: Long = System.currentTimeMillis()): Float {
        if (durationMillis <= 0L) return 0f
        val remaining = remainingMillis(currentTime)
        val elapsed = (durationMillis - remaining).coerceIn(0L, durationMillis)
        return (elapsed.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }
}

data class ReminderHistoryItem(
    val id: String,
    val timestamp: Long,
    val durationLabel: String,
    val announcedTime: String
)
