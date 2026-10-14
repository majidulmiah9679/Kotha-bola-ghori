package com.example.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.util.TimeFormatter
import java.util.Calendar
import java.util.Locale

class VoiceAnnouncementManager(
    private val context: Context,
    private val onInitListener: ((Boolean, Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isBengaliSupported = false
    private var activeLocale: Locale = Locale.getDefault()
    private var onSpeechDoneCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "VoiceManager"
        private const val UTTERANCE_ID = "voice_reminder_utterance_16k"
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
            isInitialized = false
            onInitListener?.invoke(false, false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return

            try {
                // High-priority audible alarm and speech configuration
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
                ttsEngine.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set AudioAttributes on TTS engine", e)
            }

            // Check Bengali Locales
            val bnBD = Locale("bn", "BD")
            val bnIN = Locale("bn", "IN")
            val bnGeneral = Locale("bn")

            val bdAvail = try { ttsEngine.isLanguageAvailable(bnBD) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            val inAvail = try { ttsEngine.isLanguageAvailable(bnIN) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            val genAvail = try { ttsEngine.isLanguageAvailable(bnGeneral) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }

            val bengaliLocale = when {
                bdAvail >= TextToSpeech.LANG_AVAILABLE -> bnBD
                inAvail >= TextToSpeech.LANG_AVAILABLE -> bnIN
                genAvail >= TextToSpeech.LANG_AVAILABLE -> bnGeneral
                else -> null
            }

            if (bengaliLocale != null) {
                val setRes = try { ttsEngine.setLanguage(bengaliLocale) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                if (setRes != TextToSpeech.LANG_MISSING_DATA && setRes != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isBengaliSupported = true
                    activeLocale = bengaliLocale
                }
            }

            if (!isBengaliSupported) {
                val defaultLocale = Locale.getDefault()
                val defAvail = try { ttsEngine.isLanguageAvailable(defaultLocale) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                if (defAvail >= TextToSpeech.LANG_AVAILABLE) {
                    try { ttsEngine.setLanguage(defaultLocale) } catch (_: Exception) {}
                    activeLocale = defaultLocale
                } else {
                    try { ttsEngine.setLanguage(Locale.US) } catch (_: Exception) {}
                    activeLocale = Locale.US
                }
            }

            try {
                ttsEngine.setSpeechRate(0.92f) // slightly relaxed rate for maximum clarity
                ttsEngine.setPitch(1.0f)
            } catch (_: Exception) {}

            try {
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS speech started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS speech completed: $utteranceId")
                        onSpeechDoneCallback?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS speech error on: $utteranceId")
                        onSpeechDoneCallback?.invoke()
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Error setting UtteranceProgressListener", e)
            }

            isInitialized = true
            onInitListener?.invoke(true, isBengaliSupported)
        } else {
            Log.e(TAG, "TTS initialization failed with status $status")
            isInitialized = false
            onInitListener?.invoke(false, false)
        }
    }

    fun isBengaliAvailable(): Boolean = isBengaliSupported

    fun isReady(): Boolean = isInitialized

    private fun ensureAudibleVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol < (maxVol * 0.75f).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVol * 0.85f).toInt(), 0)
            }

            // Also request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust audio stream volume", e)
        }
    }

    private fun playChimeTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
        } catch (e: Exception) {
            Log.w(TAG, "Chime tone generator exception", e)
        }
    }

    fun speakReminder(
        timeMillis: Long = System.currentTimeMillis(),
        isTest: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        ensureAudibleVolume()
        playChimeTone()

        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet")
            onDone?.invoke()
            return
        }

        this.onSpeechDoneCallback = onDone

        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val textToSpeak = if (isBengaliSupported) {
            val spokenTime = TimeFormatter.getSpokenTimeString(cal, isBengali = true)
            if (isTest) {
                "ভয়েস টেস্ট সফল হয়েছে। বর্তমান সময় $spokenTime।"
            } else {
                "সময় হয়েছে! এখন বাজে $spokenTime। আপনার নির্ধারিত সময় পূর্ণ হয়েছে।"
            }
        } else {
            val spokenTime = TimeFormatter.getSpokenTimeString(cal, isBengali = false)
            if (isTest) {
                "Voice test successful. Current time is $spokenTime."
            } else {
                "Time's up! The current time is $spokenTime. Your scheduled reminder is complete."
            }
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }

        try {
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tts.speak", e)
            onDone?.invoke()
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }

    fun getTtsSettingsIntent(): Intent {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
