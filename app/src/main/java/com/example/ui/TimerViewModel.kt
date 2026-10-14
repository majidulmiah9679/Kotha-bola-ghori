package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.TimerPreferences
import com.example.model.ActiveTimer
import com.example.model.ReminderHistoryItem
import com.example.tts.VoiceAnnouncementManager
import com.example.util.TimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TimerUiState(
    val activeTimer: ActiveTimer? = null,
    val remainingMillis: Long = 0L,
    val progress: Float = 0f,
    val selectedMinutes: Int = 15,
    val isExactAlarmAllowed: Boolean = true,
    val isNotificationAllowed: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val isBengaliTtsSupported: Boolean = false,
    val isTtsReady: Boolean = false,
    val history: List<ReminderHistoryItem> = emptyList(),
    val messageBanner: String? = null,
    val isTestingTts: Boolean = false,

    // Real-Time Clock Chime State
    val isClockChimeEnabled: Boolean = false,
    val clockChimeInterval: Int = 15, // 15, 30, 60 minutes
    val clockChimeStartHour: Int = 8,
    val clockChimeStartMinute: Int = 0,
    val clockChimeEndHour: Int = 22,
    val clockChimeEndMinute: Int = 0,
    val clockChimeNextTriggerMillis: Long = 0L
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = TimerPreferences(application)
    private var voiceManager: VoiceAnnouncementManager? = null

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        initVoiceManager()
        refreshAll()
    }

    private fun initVoiceManager() {
        voiceManager = VoiceAnnouncementManager(getApplication()) { isInit, isBengali ->
            _uiState.update {
                it.copy(
                    isTtsReady = isInit,
                    isBengaliTtsSupported = isBengali
                )
            }
        }
    }

    fun refreshAll() {
        refreshPermissions()
        loadActiveTimer()
        loadClockChimeState()
        loadHistory()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val canExact = AlarmScheduler.canScheduleExactAlarms(context)

        val notifAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }

        _uiState.update {
            it.copy(
                isExactAlarmAllowed = canExact,
                isNotificationAllowed = notifAllowed,
                isIgnoringBatteryOptimizations = batteryIgnored
            )
        }
    }

    private fun loadActiveTimer() {
        val timer = prefs.getActiveTimer()
        val now = System.currentTimeMillis()

        if (timer != null && timer.triggerAtMillis > now) {
            _uiState.update {
                it.copy(
                    activeTimer = timer,
                    remainingMillis = timer.remainingMillis(now),
                    progress = timer.progress(now)
                )
            }
            startCountdownLoop()
        } else {
            if (timer != null) {
                prefs.clearActiveTimer()
            }
            _uiState.update {
                it.copy(
                    activeTimer = null,
                    remainingMillis = 0L,
                    progress = 0f
                )
            }
            countdownJob?.cancel()
        }
    }

    private fun loadClockChimeState() {
        val enabled = prefs.isClockChimeEnabled()
        val interval = prefs.getClockChimeInterval()
        val startH = prefs.getClockChimeStartHour()
        val startM = prefs.getClockChimeStartMinute()
        val endH = prefs.getClockChimeEndHour()
        val endM = prefs.getClockChimeEndMinute()
        val nextTrigger = prefs.getClockChimeNextTrigger()

        _uiState.update {
            it.copy(
                isClockChimeEnabled = enabled,
                clockChimeInterval = interval,
                clockChimeStartHour = startH,
                clockChimeStartMinute = startM,
                clockChimeEndHour = endH,
                clockChimeEndMinute = endM,
                clockChimeNextTriggerMillis = nextTrigger
            )
        }
    }

    fun loadHistory() {
        val historyList = prefs.getHistory()
        _uiState.update { it.copy(history = historyList) }
    }

    fun clearHistory() {
        prefs.clearHistory()
        loadHistory()
    }

    private fun startCountdownLoop() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value.activeTimer
                if (current == null) break

                val now = System.currentTimeMillis()
                val remaining = current.remainingMillis(now)
                val prog = current.progress(now)

                if (remaining <= 0L) {
                    _uiState.update {
                        it.copy(
                            activeTimer = null,
                            remainingMillis = 0L,
                            progress = 1f
                        )
                    }
                    loadHistory()
                    break
                }

                _uiState.update {
                    it.copy(
                        remainingMillis = remaining,
                        progress = prog
                    )
                }
                delay(1000L)
            }
        }
    }

    fun setSelectedMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        _uiState.update { it.copy(selectedMinutes = clamped) }
    }

    fun adjustMinutes(delta: Int) {
        val current = _uiState.value.selectedMinutes
        setSelectedMinutes(current + delta)
    }

    fun startTimerWithMinutes(minutes: Int = _uiState.value.selectedMinutes) {
        val clampedMinutes = minutes.coerceIn(1, 60)
        val durationMillis = clampedMinutes * 60 * 1000L

        val labelBn = if (clampedMinutes == 60) {
            "১ ঘণ্টা"
        } else {
            "${TimeFormatter.toBengaliDigits(clampedMinutes.toString())} মিনিট"
        }

        val labelEn = if (clampedMinutes == 60) {
            "1 Hour"
        } else {
            "$clampedMinutes Minute" + if (clampedMinutes > 1) "s" else ""
        }

        val context = getApplication<Application>()
        val triggerAt = System.currentTimeMillis() + durationMillis

        val timer = ActiveTimer(
            id = UUID.randomUUID().toString(),
            durationMillis = durationMillis,
            triggerAtMillis = triggerAt,
            labelBn = labelBn,
            labelEn = labelEn,
            createdAtMillis = System.currentTimeMillis()
        )

        val scheduled = AlarmScheduler.scheduleAlarm(context, timer)
        if (scheduled) {
            prefs.saveActiveTimer(timer)
            _uiState.update {
                it.copy(
                    activeTimer = timer,
                    remainingMillis = durationMillis,
                    progress = 0f,
                    selectedMinutes = clampedMinutes,
                    messageBanner = "Reminder set for $labelEn ($labelBn)"
                )
            }
            startCountdownLoop()
        } else {
            _uiState.update {
                it.copy(messageBanner = "Could not schedule alarm. Please verify permissions.")
            }
        }
    }

    fun cancelActiveTimer() {
        val context = getApplication<Application>()
        AlarmScheduler.cancelAlarm(context)
        prefs.clearActiveTimer()
        countdownJob?.cancel()

        _uiState.update {
            it.copy(
                activeTimer = null,
                remainingMillis = 0L,
                progress = 0f,
                messageBanner = "Reminder cancelled"
            )
        }
    }

    // Real-Time Clock Chime Actions
    fun setClockChimeEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        prefs.setClockChimeEnabled(enabled)

        if (enabled) {
            val nextTrigger = AlarmScheduler.scheduleNextClockChime(context)
            _uiState.update {
                it.copy(
                    isClockChimeEnabled = true,
                    clockChimeNextTriggerMillis = nextTrigger ?: 0L,
                    messageBanner = "Real-Time Clock Chime activated!"
                )
            }
        } else {
            AlarmScheduler.cancelClockChime(context)
            _uiState.update {
                it.copy(
                    isClockChimeEnabled = false,
                    clockChimeNextTriggerMillis = 0L,
                    messageBanner = "Clock Chime turned off"
                )
            }
        }
    }

    fun setClockChimeInterval(intervalMinutes: Int) {
        val clamped = if (intervalMinutes in listOf(15, 30, 60)) intervalMinutes else 15
        prefs.setClockChimeInterval(clamped)
        _uiState.update { it.copy(clockChimeInterval = clamped) }

        if (_uiState.value.isClockChimeEnabled) {
            val nextTrigger = AlarmScheduler.scheduleNextClockChime(getApplication())
            _uiState.update { it.copy(clockChimeNextTriggerMillis = nextTrigger ?: 0L) }
        }
    }

    fun setClockChimeTimeWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs.setClockChimeStartHour(startHour)
        prefs.setClockChimeStartMinute(startMinute)
        prefs.setClockChimeEndHour(endHour)
        prefs.setClockChimeEndMinute(endMinute)

        _uiState.update {
            it.copy(
                clockChimeStartHour = startHour,
                clockChimeStartMinute = startMinute,
                clockChimeEndHour = endHour,
                clockChimeEndMinute = endMinute
            )
        }

        if (_uiState.value.isClockChimeEnabled) {
            val nextTrigger = AlarmScheduler.scheduleNextClockChime(getApplication())
            _uiState.update { it.copy(clockChimeNextTriggerMillis = nextTrigger ?: 0L) }
        }
    }

    fun testTtsVoice() {
        _uiState.update { it.copy(isTestingTts = true) }
        voiceManager?.speakReminder(timeMillis = System.currentTimeMillis(), isTest = true) {
            _uiState.update { it.copy(isTestingTts = false) }
        }
    }

    fun stopTtsVoice() {
        voiceManager?.stop()
        _uiState.update { it.copy(isTestingTts = false) }
    }

    fun dismissMessageBanner() {
        _uiState.update { it.copy(messageBanner = null) }
    }

    @SuppressLint("BatteryLife")
    fun getBatteryOptimizationIntent(): Intent? {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } else {
            null
        }
    }

    fun getTtsSettingsIntent(): Intent {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        voiceManager?.stop()
        voiceManager?.shutdown()
        voiceManager = null
    }
}
