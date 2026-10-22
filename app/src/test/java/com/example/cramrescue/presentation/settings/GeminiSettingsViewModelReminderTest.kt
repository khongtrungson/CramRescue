package com.example.cramrescue.presentation.settings

import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisClient
import com.example.cramrescue.domain.model.StudyReminder
import com.example.cramrescue.domain.reminder.ReminderScheduler
import com.example.cramrescue.domain.repository.ReminderSettingsRepository
import com.example.cramrescue.domain.usecase.settings.CheckStoredGeminiCredentialUseCase
import com.example.cramrescue.domain.usecase.settings.SaveGeminiCredentialUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeReminderSettingsRepository(
    initialReminders: List<StudyReminder> = listOf(
        StudyReminder(id = 1L, enabled = false, hour = 20, minute = 0)
    )
) : ReminderSettingsRepository {
    var storage: MutableList<StudyReminder> = initialReminders.toMutableList()
    private val flow = MutableStateFlow(storage.toList())

    override fun getReminders(): List<StudyReminder> = storage.toList()
    override fun getRemindersFlow(): Flow<List<StudyReminder>> = flow.asStateFlow()

    override fun saveReminders(reminders: List<StudyReminder>) {
        this.storage = reminders.toMutableList()
        flow.value = this.storage.toList()
    }

    override fun addReminder(reminder: StudyReminder) {
        storage.add(reminder)
        flow.value = storage.toList()
    }

    override fun updateReminder(reminder: StudyReminder) {
        val index = storage.indexOfFirst { it.id == reminder.id }
        if (index != -1) {
            storage[index] = reminder
            flow.value = storage.toList()
        }
    }

    override fun deleteReminder(id: Long) {
        storage.removeAll { it.id == id }
        flow.value = storage.toList()
    }
}

class FakeReminderScheduler : ReminderScheduler {
    val scheduledReminders = mutableMapOf<Long, StudyReminder>()
    val cancelledIds = mutableListOf<Long>()

    override fun scheduleReminder(reminder: StudyReminder) {
        scheduledReminders[reminder.id] = reminder
    }

    override fun cancelReminder(reminderId: Long) {
        scheduledReminders.remove(reminderId)
        cancelledIds.add(reminderId)
    }

    override fun rescheduleAll(reminders: List<StudyReminder>) {
        scheduledReminders.clear()
        for (r in reminders) {
            if (r.enabled) scheduleReminder(r)
        }
    }
}

class GeminiSettingsViewModelReminderTest {

    private val credRepo = FakeCredentialRepository(null)
    private val client = FakeStudyAnalysisClient()
    private val saveCred = SaveGeminiCredentialUseCase(credRepo, client)
    private val checkCred = CheckStoredGeminiCredentialUseCase(credRepo, client)

    private val reminderRepo = FakeReminderSettingsRepository()
    private val reminderScheduler = FakeReminderScheduler()
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private fun createViewModel(): GeminiSettingsViewModel {
        return GeminiSettingsViewModel(
            credentialRepository = credRepo,
            saveCredential = saveCred,
            checkStoredCredential = checkCred,
            reminderSettingsRepository = reminderRepo,
            reminderScheduler = reminderScheduler,
            coroutineScope = testScope
        )
    }

    @Test
    fun togglingReminderEnablesAlarmAndPersists() {
        val viewModel = createViewModel()

        viewModel.toggleReminder(1L, true)

        val updated = viewModel.uiState.value.reminders.first { it.id == 1L }
        assertTrue(updated.enabled)
        assertTrue(reminderScheduler.scheduledReminders.containsKey(1L))
    }

    @Test
    fun togglingReminderDisablesAlarmAndCancels() {
        reminderRepo.storage = mutableListOf(
            StudyReminder(id = 1L, enabled = true, hour = 20, minute = 0)
        )
        val viewModel = createViewModel()

        viewModel.toggleReminder(1L, false)

        val updated = viewModel.uiState.value.reminders.first { it.id == 1L }
        assertFalse(updated.enabled)
        assertFalse(reminderScheduler.scheduledReminders.containsKey(1L))
        assertTrue(reminderScheduler.cancelledIds.contains(1L))
    }

    @Test
    fun addingMultipleRemindersSchedulesEachSeparately() {
        val viewModel = createViewModel()

        viewModel.addReminder(hour = 8, minute = 30)
        viewModel.addReminder(hour = 14, minute = 0)

        val state = viewModel.uiState.value.reminders
        assertEquals(3, state.size)
        assertEquals(2, reminderScheduler.scheduledReminders.size)
    }

    @Test
    fun deletingReminderCancelsAlarmAndRemovesFromList() {
        val viewModel = createViewModel()
        viewModel.addReminder(hour = 9, minute = 0)
        val addedId = viewModel.uiState.value.reminders.last().id

        viewModel.deleteReminder(addedId)

        assertFalse(viewModel.uiState.value.reminders.any { it.id == addedId })
        assertFalse(reminderScheduler.scheduledReminders.containsKey(addedId))
        assertTrue(reminderScheduler.cancelledIds.contains(addedId))
    }
}
