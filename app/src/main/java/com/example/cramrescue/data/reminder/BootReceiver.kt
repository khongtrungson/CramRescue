package com.example.cramrescue.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cramrescue.data.repository.ReminderSettingsRepositoryImpl

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = ReminderSettingsRepositoryImpl(context)
            val reminders = repository.getReminders()

            val scheduler = AndroidReminderScheduler(context)
            scheduler.rescheduleAll(reminders)
        }
    }
}
