package com.example.cramrescue.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.cramrescue.domain.model.StudyReminder
import com.example.cramrescue.domain.repository.ReminderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ReminderSettingsRepositoryImpl(context: Context) : ReminderSettingsRepository {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _remindersFlow = MutableStateFlow(readFromPrefs())

    private fun readFromPrefs(): List<StudyReminder> {
        val json = prefs.getString(KEY_REMINDERS_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val list = mutableListOf<StudyReminder>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        StudyReminder(
                            id = obj.optLong("id", System.currentTimeMillis()),
                            enabled = obj.optBoolean("enabled", true),
                            hour = obj.optInt("hour", StudyReminder.DEFAULT_HOUR),
                            minute = obj.optInt("minute", StudyReminder.DEFAULT_MINUTE),
                            message = obj.optString("message", StudyReminder.DEFAULT_MESSAGE)
                        )
                    )
                }
                return list
            } catch (_: Exception) {
                // Malformed JSON fallback
            }
        }

        // Migrate legacy single reminder if present
        if (prefs.contains(KEY_LEGACY_ENABLED)) {
            val enabled = prefs.getBoolean(KEY_LEGACY_ENABLED, false)
            val hour = prefs.getInt(KEY_LEGACY_HOUR, StudyReminder.DEFAULT_HOUR)
            val minute = prefs.getInt(KEY_LEGACY_MINUTE, StudyReminder.DEFAULT_MINUTE)
            val message = prefs.getString(KEY_LEGACY_MESSAGE, StudyReminder.DEFAULT_MESSAGE) ?: StudyReminder.DEFAULT_MESSAGE
            val migrated = listOf(
                StudyReminder(
                    id = 1L,
                    enabled = enabled,
                    hour = hour,
                    minute = minute,
                    message = message
                )
            )
            saveReminders(migrated)
            return migrated
        }

        // Default initial reminder
        val initial = listOf(
            StudyReminder(
                id = 1L,
                enabled = false,
                hour = StudyReminder.DEFAULT_HOUR,
                minute = StudyReminder.DEFAULT_MINUTE,
                message = StudyReminder.DEFAULT_MESSAGE
            )
        )
        return initial
    }

    override fun getReminders(): List<StudyReminder> {
        return readFromPrefs()
    }

    override fun getRemindersFlow(): Flow<List<StudyReminder>> {
        return _remindersFlow.asStateFlow()
    }

    override fun saveReminders(reminders: List<StudyReminder>) {
        val array = JSONArray()
        for (item in reminders) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("enabled", item.enabled)
            obj.put("hour", item.hour)
            obj.put("minute", item.minute)
            obj.put("message", item.message)
            array.put(obj)
        }
        prefs.edit().putString(KEY_REMINDERS_JSON, array.toString()).apply()
        _remindersFlow.value = reminders
    }

    override fun addReminder(reminder: StudyReminder) {
        val current = readFromPrefs().toMutableList()
        current.add(reminder)
        saveReminders(current)
    }

    override fun updateReminder(reminder: StudyReminder) {
        val current = readFromPrefs().map {
            if (it.id == reminder.id) reminder else it
        }
        saveReminders(current)
    }

    override fun deleteReminder(id: Long) {
        val current = readFromPrefs().filterNot { it.id == id }
        saveReminders(current)
    }

    companion object {
        private const val PREFS_NAME = "cramrescue_reminder_prefs"
        private const val KEY_REMINDERS_JSON = "reminders_json"

        // Legacy keys for migration
        private const val KEY_LEGACY_ENABLED = "reminder_enabled"
        private const val KEY_LEGACY_HOUR = "reminder_hour"
        private const val KEY_LEGACY_MINUTE = "reminder_minute"
        private const val KEY_LEGACY_MESSAGE = "reminder_message"
    }
}
