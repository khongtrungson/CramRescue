package com.example.cramrescue.domain.reminder

import com.example.cramrescue.domain.model.StudyReminder

interface ReminderScheduler {
    fun scheduleReminder(reminder: StudyReminder)
    fun cancelReminder(reminderId: Long)
    fun rescheduleAll(reminders: List<StudyReminder>)
}
