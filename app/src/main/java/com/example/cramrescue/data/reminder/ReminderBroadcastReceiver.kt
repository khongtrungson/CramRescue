package com.example.cramrescue.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cramrescue.data.repository.ReminderSettingsRepositoryImpl
import com.example.cramrescue.domain.model.StudyReminder

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMINDER_TRIGGER) {
            val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 1L)
            val repository = ReminderSettingsRepositoryImpl(context)
            val reminders = repository.getReminders()
            val reminder = reminders.firstOrNull { it.id == reminderId }

            val message = intent.getStringExtra(EXTRA_MESSAGE)
                ?.takeIf { it.isNotBlank() }
                ?: reminder?.message
                ?: StudyReminder.DEFAULT_MESSAGE

            NotificationHelper.showStudyReminderNotification(
                context = context,
                message = message,
                notificationId = reminderId.toInt()
            )

            // Reschedule this reminder for the next day if still enabled
            if (reminder != null && reminder.enabled) {
                val scheduler = AndroidReminderScheduler(context)
                scheduler.scheduleReminder(reminder)
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_TRIGGER = "com.example.cramrescue.ACTION_REMINDER_TRIGGER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_MESSAGE = "extra_message"
    }
}
