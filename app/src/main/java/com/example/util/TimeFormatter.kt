package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TimeFormatter {

    fun toBengaliDigits(input: String): String {
        val enDigits = "0123456789"
        val bnDigits = "০১২৩৪৫৬৭৮৯"
        val sb = StringBuilder()
        for (ch in input) {
            val idx = enDigits.indexOf(ch)
            if (idx != -1) {
                sb.append(bnDigits[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatDuration(totalMillis: Long): Pair<String, String> {
        val totalSeconds = (totalMillis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val enText = if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
        val bnText = toBengaliDigits(enText)
        return Pair(bnText, enText)
    }

    fun getSpokenTimeString(calendar: Calendar, isBengali: Boolean): String {
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val hour12Raw = calendar.get(Calendar.HOUR)
        val hour12 = if (hour12Raw == 0) 12 else hour12Raw
        val minute = calendar.get(Calendar.MINUTE)

        if (isBengali) {
            val period = when (hour24) {
                in 4..5 -> "ভোর"
                in 6..11 -> "সকাল"
                in 12..14 -> "দুপুর"
                in 15..17 -> "বিকাল"
                in 18..19 -> "সন্ধ্যা"
                else -> "রাত"
            }

            val hourWord = when (hour12) {
                1 -> "১টা"
                2 -> "২টা"
                3 -> "৩টা"
                4 -> "৪টা"
                5 -> "৫টা"
                6 -> "৬টা"
                7 -> "সাতটা"
                8 -> "আটটা"
                9 -> "নয়টা"
                10 -> "১০টা"
                11 -> "১১টা"
                12 -> "১২টা"
                else -> "${toBengaliDigits(hour12.toString())}টা"
            }

            return if (minute == 0) {
                "$period $hourWord"
            } else {
                val minDigits = toBengaliDigits(String.format(Locale.US, "%02d", minute))
                "$period $hourWord $minDigits মিনিট"
            }
        } else {
            val amPm = if (hour24 >= 12) "PM" else "AM"
            return if (minute == 0) {
                String.format(Locale.US, "%d %s", hour12, amPm)
            } else {
                String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
            }
        }
    }

    fun formatDisplayTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(timestamp)
    }

    fun formatDisplayTimeBn(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return getSpokenTimeString(cal, isBengali = true)
    }
}
