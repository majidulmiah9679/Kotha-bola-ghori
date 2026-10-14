package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarm.AlarmScheduler
import com.example.data.TimerPreferences
import com.example.model.ReminderHistoryItem
import com.example.service.TimeAnnouncementService
import com.example.util.TimeFormatter
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "AlarmReceiver onReceive action=$action")

        if (action == AlarmScheduler.ACTION_DISMISS_ALARM) {
            val stopIntent = Intent(context, TimeAnnouncementService::class.java).apply {
                this.action = AlarmScheduler.ACTION_DISMISS_ALARM
            }
            context.startService(stopIntent)
            return
        }

        if (action == AlarmScheduler.ACTION_ALARM_TRIGGER || action == AlarmScheduler.ACTION_CLOCK_CHIME_TRIGGER) {
            val isClockChime = (action == AlarmScheduler.ACTION_CLOCK_CHIME_TRIGGER)
            val pendingResult = goAsync()

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceTimeReminder:AlarmReceiverWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10000L)
            }

            try {
                val timerId = intent.getStringExtra(AlarmScheduler.EXTRA_TIMER_ID) ?: ""
                val labelBn = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL_BN)
                    ?: if (isClockChime) "রিয়েল-টাইম ক্লক ঘোষণা" else ""
                val labelEn = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL_EN)
                    ?: if (isClockChime) "Real-Time Clock Chime" else ""
                val triggerTime = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_TIME, System.currentTimeMillis())

                val prefs = TimerPreferences(context)
                val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
                val spokenTime = TimeFormatter.getSpokenTimeString(cal, isBengali = true)

                prefs.addHistory(
                    ReminderHistoryItem(
                        id = timerId.ifEmpty { System.currentTimeMillis().toString() },
                        timestamp = triggerTime,
                        durationLabel = labelEn,
                        announcedTime = spokenTime
                    )
                )

                if (!isClockChime) {
                    prefs.clearActiveTimer()
                }

                // Launch Foreground Service for speech
                val serviceIntent = Intent(context, TimeAnnouncementService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_TRIGGER_TIME, triggerTime)
                    putExtra(AlarmScheduler.EXTRA_LABEL_BN, labelBn)
                    putExtra(AlarmScheduler.EXTRA_LABEL_EN, labelEn)
                }

                ContextCompat.startForegroundService(context, serviceIntent)

                // If this is a recurring clock chime, immediately schedule the NEXT milestone!
                if (isClockChime && prefs.isClockChimeEnabled()) {
                    AlarmScheduler.scheduleNextClockChime(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm trigger in AlarmReceiver", e)
            } finally {
                try {
                    wakeLock?.release()
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
