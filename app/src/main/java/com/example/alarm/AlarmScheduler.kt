package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.MainActivity
import com.example.data.TimerPreferences
import com.example.model.ActiveTimer
import com.example.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_ALARM_TRIGGER = "com.example.ACTION_ALARM_TRIGGER"
    const val ACTION_CLOCK_CHIME_TRIGGER = "com.example.ACTION_CLOCK_CHIME_TRIGGER"
    const val ACTION_DISMISS_ALARM = "com.example.ACTION_DISMISS_ALARM"

    const val EXTRA_TIMER_ID = "extra_timer_id"
    const val EXTRA_LABEL_BN = "extra_label_bn"
    const val EXTRA_LABEL_EN = "extra_label_en"
    const val EXTRA_TRIGGER_TIME = "extra_trigger_time"
    const val EXTRA_IS_CLOCK_CHIME = "extra_is_clock_chime"

    const val REQUEST_CODE_ALARM = 1001
    const val REQUEST_CODE_CLOCK_CHIME = 1002

    private const val TAG = "AlarmScheduler"

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun getExactAlarmSettingIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
    }

    fun scheduleAlarm(context: Context, timer: ActiveTimer): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available")
            return false
        }

        cancelAlarm(context)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TIMER_ID, timer.id)
            putExtra(EXTRA_LABEL_BN, timer.labelBn)
            putExtra(EXTRA_LABEL_EN, timer.labelEn)
            putExtra(EXTRA_TRIGGER_TIME, timer.triggerAtMillis)
            putExtra(EXTRA_IS_CLOCK_CHIME, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = timer.triggerAtMillis

        try {
            if (canScheduleExactAlarms(context)) {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Exact AlarmClock scheduled at $triggerAtMillis")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.w(TAG, "Exact alarms not allowed; scheduled fallback setAndAllowWhileIdle at $triggerAtMillis")
            }
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm, falling back to setAndAllowWhileIdle", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling", ex)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling alarm", e)
            return false
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarm cancelled successfully")
        }
    }

    // Real-Time Clock Chime Calculation & Scheduling
    fun calculateNextClockChimeTrigger(
        intervalMinutes: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        fromMillis: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val step = if (intervalMinutes in listOf(15, 30, 60)) intervalMinutes else 15

        // Advance to the very next clock aligned milestone
        while (true) {
            cal.add(Calendar.MINUTE, 1)
            val min = cal.get(Calendar.MINUTE)
            if (min % step == 0) {
                if (cal.timeInMillis > fromMillis) {
                    break
                }
            }
        }

        // Verify within active hours window [start, end]
        var targetMillis = cal.timeInMillis

        fun isTimeInWindow(testCal: Calendar): Boolean {
            val totalMins = testCal.get(Calendar.HOUR_OF_DAY) * 60 + testCal.get(Calendar.MINUTE)
            val startMins = startHour * 60 + startMinute
            val endMins = endHour * 60 + endMinute

            return if (startMins <= endMins) {
                totalMins in startMins..endMins
            } else {
                // Overnight window (e.g. 20:00 to 06:00)
                totalMins >= startMins || totalMins <= endMins
            }
        }

        var candidateCal = Calendar.getInstance().apply { timeInMillis = targetMillis }

        // If outside window, jump until next valid active time
        var attempts = 0
        while (!isTimeInWindow(candidateCal) && attempts < 1440) {
            candidateCal.add(Calendar.MINUTE, step)
            attempts += step
        }

        return candidateCal.timeInMillis
    }

    fun scheduleNextClockChime(context: Context): Long? {
        val prefs = TimerPreferences(context)
        if (!prefs.isClockChimeEnabled()) {
            cancelClockChime(context)
            return null
        }

        val interval = prefs.getClockChimeInterval()
        val startH = prefs.getClockChimeStartHour()
        val startM = prefs.getClockChimeStartMinute()
        val endH = prefs.getClockChimeEndHour()
        val endM = prefs.getClockChimeEndMinute()

        val nextTrigger = calculateNextClockChimeTrigger(
            intervalMinutes = interval,
            startHour = startH,
            startMinute = startM,
            endHour = endH,
            endMinute = endM
        )

        prefs.setClockChimeNextTrigger(nextTrigger)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CLOCK_CHIME_TRIGGER
            putExtra(EXTRA_TRIGGER_TIME, nextTrigger)
            putExtra(EXTRA_IS_CLOCK_CHIME, true)
            putExtra(EXTRA_LABEL_EN, "Clock Chime (${interval}m)")
            putExtra(EXTRA_LABEL_BN, "রিয়েল-টাইম ক্লক (${interval} মি)")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CLOCK_CHIME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms(context)) {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTrigger, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Clock Chime scheduled at $nextTrigger for interval $interval min")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger,
                    pendingIntent
                )
            }
            return nextTrigger
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling Clock Chime", e)
            return null
        }
    }

    fun cancelClockChime(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CLOCK_CHIME_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CLOCK_CHIME,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Clock Chime cancelled")
        }

        TimerPreferences(context).setClockChimeNextTrigger(0L)
    }
}
