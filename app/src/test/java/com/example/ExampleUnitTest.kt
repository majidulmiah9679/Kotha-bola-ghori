package com.example

import com.example.alarm.AlarmScheduler
import com.example.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun testBengaliDigits() {
        val converted = TimeFormatter.toBengaliDigits("1234567890")
        assertEquals("১২৩৪৫৬৭৮৯০", converted)
    }

    @Test
    fun testDurationFormatting() {
        val (bn15, en15) = TimeFormatter.formatDuration(15 * 60 * 1000L)
        assertEquals("১৫:০০", bn15)
        assertEquals("15:00", en15)

        val (bn1, en1) = TimeFormatter.formatDuration(60 * 1000L)
        assertEquals("০১:০০", bn1)
        assertEquals("01:00", en1)

        val (bn60, en60) = TimeFormatter.formatDuration(60 * 60 * 1000L)
        assertEquals("০১:০০:০০", bn60)
        assertEquals("01:00:00", en60)
    }

    @Test
    fun testSpokenTimeFormat() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
        }
        val spokenBn = TimeFormatter.getSpokenTimeString(cal, isBengali = true)
        val spokenEn = TimeFormatter.getSpokenTimeString(cal, isBengali = false)

        assertEquals("সকাল ১০টা ১৫ মিনিট", spokenBn)
        assertEquals("10:15 AM", spokenEn)
    }

    @Test
    fun testClockChimeCalculation() {
        // Assume from 10:07 AM
        val fromCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 7)
            set(Calendar.SECOND, 0)
        }

        val nextTrigger = AlarmScheduler.calculateNextClockChimeTrigger(
            intervalMinutes = 15,
            startHour = 8,
            startMinute = 0,
            endHour = 22,
            endMinute = 0,
            fromMillis = fromCal.timeInMillis
        )

        val targetCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
        assertEquals(10, targetCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, targetCal.get(Calendar.MINUTE))
        assertEquals(0, targetCal.get(Calendar.SECOND))
    }
}
