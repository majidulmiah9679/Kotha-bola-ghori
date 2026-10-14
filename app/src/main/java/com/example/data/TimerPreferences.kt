package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ActiveTimer
import com.example.model.ReminderHistoryItem
import org.json.JSONArray
import org.json.JSONObject

class TimerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "voice_time_reminder_prefs"
        private const val KEY_ACTIVE_TIMER_ID = "active_timer_id"
        private const val KEY_ACTIVE_DURATION = "active_timer_duration"
        private const val KEY_ACTIVE_TRIGGER = "active_timer_trigger"
        private const val KEY_ACTIVE_LABEL_BN = "active_timer_label_bn"
        private const val KEY_ACTIVE_LABEL_EN = "active_timer_label_en"
        private const val KEY_ACTIVE_CREATED = "active_timer_created"
        private const val KEY_HAS_ACTIVE_TIMER = "has_active_timer"
        private const val KEY_HISTORY_JSON = "reminder_history_json"
        private const val KEY_LANGUAGE_MODE = "language_mode"

        // Real-Time Clock Chime Preferences
        private const val KEY_CLOCK_CHIME_ENABLED = "clock_chime_enabled"
        private const val KEY_CLOCK_CHIME_INTERVAL = "clock_chime_interval" // 15, 30, 60
        private const val KEY_CLOCK_CHIME_START_HOUR = "clock_chime_start_hour" // 0..23
        private const val KEY_CLOCK_CHIME_START_MIN = "clock_chime_start_min"
        private const val KEY_CLOCK_CHIME_END_HOUR = "clock_chime_end_hour" // 0..23
        private const val KEY_CLOCK_CHIME_END_MIN = "clock_chime_end_min"
        private const val KEY_CLOCK_CHIME_NEXT_TRIGGER = "clock_chime_next_trigger"
    }

    fun saveActiveTimer(timer: ActiveTimer) {
        prefs.edit()
            .putBoolean(KEY_HAS_ACTIVE_TIMER, true)
            .putString(KEY_ACTIVE_TIMER_ID, timer.id)
            .putLong(KEY_ACTIVE_DURATION, timer.durationMillis)
            .putLong(KEY_ACTIVE_TRIGGER, timer.triggerAtMillis)
            .putString(KEY_ACTIVE_LABEL_BN, timer.labelBn)
            .putString(KEY_ACTIVE_LABEL_EN, timer.labelEn)
            .putLong(KEY_ACTIVE_CREATED, timer.createdAtMillis)
            .apply()
    }

    fun getActiveTimer(): ActiveTimer? {
        if (!prefs.getBoolean(KEY_HAS_ACTIVE_TIMER, false)) return null
        val id = prefs.getString(KEY_ACTIVE_TIMER_ID, null) ?: return null
        val duration = prefs.getLong(KEY_ACTIVE_DURATION, 0L)
        val trigger = prefs.getLong(KEY_ACTIVE_TRIGGER, 0L)
        val labelBn = prefs.getString(KEY_ACTIVE_LABEL_BN, "") ?: ""
        val labelEn = prefs.getString(KEY_ACTIVE_LABEL_EN, "") ?: ""
        val created = prefs.getLong(KEY_ACTIVE_CREATED, 0L)

        return ActiveTimer(
            id = id,
            durationMillis = duration,
            triggerAtMillis = trigger,
            labelBn = labelBn,
            labelEn = labelEn,
            createdAtMillis = created
        )
    }

    fun clearActiveTimer() {
        prefs.edit()
            .putBoolean(KEY_HAS_ACTIVE_TIMER, false)
            .remove(KEY_ACTIVE_TIMER_ID)
            .remove(KEY_ACTIVE_DURATION)
            .remove(KEY_ACTIVE_TRIGGER)
            .remove(KEY_ACTIVE_LABEL_BN)
            .remove(KEY_ACTIVE_LABEL_EN)
            .remove(KEY_ACTIVE_CREATED)
            .apply()
    }

    fun addHistory(item: ReminderHistoryItem) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.add(0, item)
        val trimmed = if (currentHistory.size > 20) currentHistory.take(20) else currentHistory

        val jsonArray = JSONArray()
        for (h in trimmed) {
            val obj = JSONObject().apply {
                put("id", h.id)
                put("timestamp", h.timestamp)
                put("durationLabel", h.durationLabel)
                put("announcedTime", h.announcedTime)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).apply()
    }

    fun getHistory(): List<ReminderHistoryItem> {
        val jsonStr = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        val list = mutableListOf<ReminderHistoryItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ReminderHistoryItem(
                        id = obj.optString("id", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        durationLabel = obj.optString("durationLabel", ""),
                        announcedTime = obj.optString("announcedTime", "")
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore corrupted JSON
        }
        return list
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY_JSON).apply()
    }

    fun getLanguageMode(): String {
        return prefs.getString(KEY_LANGUAGE_MODE, "AUTO") ?: "AUTO"
    }

    fun setLanguageMode(mode: String) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode).apply()
    }

    // Clock Chime Preferences
    fun isClockChimeEnabled(): Boolean = prefs.getBoolean(KEY_CLOCK_CHIME_ENABLED, false)

    fun setClockChimeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOCK_CHIME_ENABLED, enabled).apply()
    }

    fun getClockChimeInterval(): Int = prefs.getInt(KEY_CLOCK_CHIME_INTERVAL, 15)

    fun setClockChimeInterval(intervalMinutes: Int) {
        prefs.edit().putInt(KEY_CLOCK_CHIME_INTERVAL, intervalMinutes).apply()
    }

    fun getClockChimeStartHour(): Int = prefs.getInt(KEY_CLOCK_CHIME_START_HOUR, 8)

    fun setClockChimeStartHour(hour: Int) {
        prefs.edit().putInt(KEY_CLOCK_CHIME_START_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getClockChimeStartMinute(): Int = prefs.getInt(KEY_CLOCK_CHIME_START_MIN, 0)

    fun setClockChimeStartMinute(minute: Int) {
        prefs.edit().putInt(KEY_CLOCK_CHIME_START_MIN, minute.coerceIn(0, 59)).apply()
    }

    fun getClockChimeEndHour(): Int = prefs.getInt(KEY_CLOCK_CHIME_END_HOUR, 22)

    fun setClockChimeEndHour(hour: Int) {
        prefs.edit().putInt(KEY_CLOCK_CHIME_END_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getClockChimeEndMinute(): Int = prefs.getInt(KEY_CLOCK_CHIME_END_MIN, 0)

    fun setClockChimeEndMinute(minute: Int) {
        prefs.edit().putInt(KEY_CLOCK_CHIME_END_MIN, minute.coerceIn(0, 59)).apply()
    }

    fun getClockChimeNextTrigger(): Long = prefs.getLong(KEY_CLOCK_CHIME_NEXT_TRIGGER, 0L)

    fun setClockChimeNextTrigger(triggerTimeMillis: Long) {
        prefs.edit().putLong(KEY_CLOCK_CHIME_NEXT_TRIGGER, triggerTimeMillis).apply()
    }
}
