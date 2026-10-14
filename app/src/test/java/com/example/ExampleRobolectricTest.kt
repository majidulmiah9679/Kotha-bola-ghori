package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TimerPreferences
import com.example.model.ActiveTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Voice Time Reminder", appName)
    }

    @Test
    fun testTimerPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = TimerPreferences(context)
        prefs.clearActiveTimer()

        val timer = ActiveTimer(
            id = "test-timer",
            durationMillis = 60000L,
            triggerAtMillis = System.currentTimeMillis() + 60000L,
            labelBn = "১ মিনিট",
            labelEn = "1 Minute",
            createdAtMillis = System.currentTimeMillis()
        )

        prefs.saveActiveTimer(timer)
        val loaded = prefs.getActiveTimer()
        assertNotNull(loaded)
        assertEquals("test-timer", loaded?.id)

        prefs.clearActiveTimer()
        assertEquals(null, prefs.getActiveTimer())
    }
}
