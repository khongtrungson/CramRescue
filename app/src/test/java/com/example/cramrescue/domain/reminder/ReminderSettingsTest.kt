package com.example.cramrescue.domain.reminder

import com.example.cramrescue.domain.model.StudyReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSettingsTest {

    @Test
    fun defaultStudyReminderHasExpectedValues() {
        val reminder = StudyReminder(id = 1L, enabled = false)
        assertFalse(reminder.enabled)
        assertEquals(20, reminder.hour)
        assertEquals(0, reminder.minute)
        assertEquals("Đã đến giờ ôn tập! Dành 15 phút để ôn tập ngay nào 🚀", reminder.message)
    }

    @Test
    fun customStudyReminderPreservesValues() {
        val reminder = StudyReminder(
            id = 2L,
            enabled = true,
            hour = 8,
            minute = 30,
            message = "Tập trung ôn đề cương môn Giải tích!"
        )
        assertTrue(reminder.enabled)
        assertEquals(8, reminder.hour)
        assertEquals(30, reminder.minute)
        assertEquals("Tập trung ôn đề cương môn Giải tích!", reminder.message)
    }
}
