package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.alarm.AlarmScheduler
import com.example.tts.VoiceAnnouncementManager
import com.example.util.TimeFormatter
import java.util.Calendar

class TimeAnnouncementService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var voiceManager: VoiceAnnouncementManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "voice_time_reminder_channel_v2"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "TimeAnnouncementService"
        private const val WAKELOCK_TIMEOUT_MS = 25000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceTimeReminder:AnnouncementWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == AlarmScheduler.ACTION_DISMISS_ALARM) {
            Log.d(TAG, "Dismiss action received, stopping service")
            stopVoiceAndFinish()
            return START_NOT_STICKY
        }

        val triggerTime = intent?.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_TIME, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val labelBn = intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL_BN) ?: ""

        val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
        val spokenTimeBn = TimeFormatter.getSpokenTimeString(cal, isBengali = true)
        val spokenTimeEn = TimeFormatter.getSpokenTimeString(cal, isBengali = false)

        val notification = buildForegroundNotification(spokenTimeBn, spokenTimeEn, labelBn)
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service notification", e)
        }

        autoStopRunnable = Runnable {
            Log.d(TAG, "Auto-stop timeout reached, cleaning up")
            stopVoiceAndFinish()
        }
        handler.postDelayed(autoStopRunnable!!, WAKELOCK_TIMEOUT_MS)

        voiceManager = VoiceAnnouncementManager(this) { isInit, _ ->
            if (isInit) {
                voiceManager?.speakReminder(timeMillis = triggerTime, isTest = false) {
                    Log.d(TAG, "Voice speech finished successfully")
                    handler.postDelayed({ stopVoiceAndFinish() }, 1000)
                }
            } else {
                Log.w(TAG, "Voice manager failed to initialize, stopping soon")
                handler.postDelayed({ stopVoiceAndFinish() }, 3000)
            }
        }

        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(
        spokenTimeBn: String,
        spokenTimeEn: String,
        labelBn: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, TimeAnnouncementService::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "সময় হয়েছে! • Time Alert"
        val message = "বর্তমান সময়: $spokenTimeBn ($spokenTimeEn)। আপনার রিমাইন্ডার সম্পন্ন হয়েছে।"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop),
                dismissPendingIntent
            )
            .build()
    }

    private fun stopVoiceAndFinish() {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        try {
            voiceManager?.stop()
            voiceManager?.shutdown()
            voiceManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning voice manager", e)
        }
        releaseWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceAndFinish()
    }
}
