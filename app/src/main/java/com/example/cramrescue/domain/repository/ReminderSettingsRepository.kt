package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.StudyReminder
import kotlinx.coroutines.flow.Flow

interface ReminderSettingsRepository {
    fun getReminders(): List<StudyReminder>
    fun getRemindersFlow(): Flow<List<StudyReminder>>
    fun saveReminders(reminders: List<StudyReminder>)
    fun addReminder(reminder: StudyReminder)
    fun updateReminder(reminder: StudyReminder)
    fun deleteReminder(id: Long)
}
