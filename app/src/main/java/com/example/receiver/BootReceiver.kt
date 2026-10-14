package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarm.AlarmScheduler
import com.example.data.TimerPreferences
import com.example.model.ReminderHistoryItem
import com.example.util.TimeFormatter
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver received action: $action")

        val prefs = TimerPreferences(context)

        // 1. Restore active one-shot timer if present
        val activeTimer = prefs.getActiveTimer()
        val now = System.currentTimeMillis()
        if (activeTimer != null) {
            if (activeTimer.triggerAtMillis > now) {
                val success = AlarmScheduler.scheduleAlarm(context, activeTimer)
                Log.d(TAG, "Restored active alarm for ${activeTimer.id}, success=$success")
            } else {
                Log.w(TAG, "Active alarm time passed while offline. Logging to history.")
                val cal = Calendar.getInstance().apply { timeInMillis = activeTimer.triggerAtMillis }
                val spokenTime = TimeFormatter.getSpokenTimeString(cal, isBengali = true)

                prefs.addHistory(
                    ReminderHistoryItem(
                        id = activeTimer.id,
                        timestamp = activeTimer.triggerAtMillis,
                        durationLabel = activeTimer.labelBn.ifEmpty { activeTimer.labelEn },
                        announcedTime = spokenTime
                    )
                )
                prefs.clearActiveTimer()
            }
        }

        // 2. Restore recurring Real-Time Clock Chime if enabled
        if (prefs.isClockChimeEnabled()) {
            val nextChime = AlarmScheduler.scheduleNextClockChime(context)
            Log.d(TAG, "Restored Real-Time Clock Chime at $nextChime")
        }
    }
}
