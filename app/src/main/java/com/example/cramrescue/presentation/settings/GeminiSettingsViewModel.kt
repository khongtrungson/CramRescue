package com.example.cramrescue.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.StudyReminder
import com.example.cramrescue.domain.reminder.ReminderScheduler
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.repository.ReminderSettingsRepository
import com.example.cramrescue.domain.usecase.settings.CheckStoredGeminiCredentialUseCase
import com.example.cramrescue.domain.usecase.settings.CredentialCheckResult
import com.example.cramrescue.domain.usecase.settings.SaveGeminiCredentialUseCase
import com.example.cramrescue.presentation.analysis.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Deliberately holds no key: only whether one is configured, plus study reminders. */
data class GeminiSettingsUiState(
    val isConfigured: Boolean? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val reminders: List<StudyReminder> = emptyList()
)

class GeminiSettingsViewModel(
    private val credentialRepository: GeminiCredentialRepository,
    private val saveCredential: SaveGeminiCredentialUseCase,
    private val checkStoredCredential: CheckStoredGeminiCredentialUseCase,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(GeminiSettingsUiState())
    val uiState: StateFlow<GeminiSettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val configured = credentialRepository.hasCredential()
            val reminders = reminderSettingsRepository.getReminders()
            _uiState.update {
                it.copy(
                    isConfigured = configured,
                    reminders = reminders
                )
            }
        }
    }

    /** Validates then saves. [onSaved] lets the screen clear its input field. */
    fun save(rawKey: String, onSaved: () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        scope.launch {
            val result = saveCredential(rawKey)
            if (result == CredentialCheckResult.Valid) onSaved()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    isConfigured = credentialRepository.hasCredential(),
                    message = result.message(saved = true),
                    messageIsError = result != CredentialCheckResult.Valid
                )
            }
        }
    }

    fun checkConnection() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        scope.launch {
            val result = checkStoredCredential()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    message = result.message(saved = false),
                    messageIsError = result != CredentialCheckResult.Valid
                )
            }
        }
    }

    fun remove() {
        if (_uiState.value.isBusy) return
        scope.launch {
            credentialRepository.clearCredential()
            _uiState.update {
                it.copy(isConfigured = false, message = "Đã xóa API key khỏi thiết bị.", messageIsError = false)
            }
        }
    }

    fun addReminder(hour: Int = StudyReminder.DEFAULT_HOUR, minute: Int = StudyReminder.DEFAULT_MINUTE) {
        val newReminder = StudyReminder(
            enabled = true,
            hour = hour,
            minute = minute,
            message = StudyReminder.DEFAULT_MESSAGE
        )
        reminderSettingsRepository.addReminder(newReminder)
        reminderScheduler.scheduleReminder(newReminder)
        _uiState.update { it.copy(reminders = reminderSettingsRepository.getReminders()) }
    }

    fun toggleReminder(id: Long, enabled: Boolean) {
        val list = reminderSettingsRepository.getReminders()
        val item = list.firstOrNull { it.id == id } ?: return
        val updated = item.copy(enabled = enabled)
        reminderSettingsRepository.updateReminder(updated)
        if (enabled) {
            reminderScheduler.scheduleReminder(updated)
        } else {
            reminderScheduler.cancelReminder(id)
        }
        _uiState.update { it.copy(reminders = reminderSettingsRepository.getReminders()) }
    }

    fun updateReminderTime(id: Long, hour: Int, minute: Int) {
        val list = reminderSettingsRepository.getReminders()
        val item = list.firstOrNull { it.id == id } ?: return
        val updated = item.copy(hour = hour, minute = minute)
        reminderSettingsRepository.updateReminder(updated)
        if (updated.enabled) {
            reminderScheduler.scheduleReminder(updated)
        }
        _uiState.update { it.copy(reminders = reminderSettingsRepository.getReminders()) }
    }

    fun updateReminderMessage(id: Long, message: String) {
        val list = reminderSettingsRepository.getReminders()
        val item = list.firstOrNull { it.id == id } ?: return
        val updated = item.copy(message = message)
        reminderSettingsRepository.updateReminder(updated)
        if (updated.enabled) {
            reminderScheduler.scheduleReminder(updated)
        }
        _uiState.update { it.copy(reminders = reminderSettingsRepository.getReminders()) }
    }

    fun deleteReminder(id: Long) {
        reminderScheduler.cancelReminder(id)
        reminderSettingsRepository.deleteReminder(id)
        _uiState.update { it.copy(reminders = reminderSettingsRepository.getReminders()) }
    }

    private fun CredentialCheckResult.message(saved: Boolean): String = when (this) {
        CredentialCheckResult.Valid ->
            if (saved) "Key hợp lệ và đã được lưu an toàn trên thiết bị." else "Kết nối Gemini thành công."
        CredentialCheckResult.Blank -> "Hãy nhập API key."
        CredentialCheckResult.NotConfigured -> "Chưa có API key nào được lưu."
        is CredentialCheckResult.Failed ->
            failure.userMessage() + if (saved) " Key chưa được lưu." else ""
    }
}

class GeminiSettingsViewModelFactory(
    private val credentialRepository: GeminiCredentialRepository,
    private val saveCredential: SaveGeminiCredentialUseCase,
    private val checkStoredCredential: CheckStoredGeminiCredentialUseCase,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GeminiSettingsViewModel(
            credentialRepository,
            saveCredential,
            checkStoredCredential,
            reminderSettingsRepository,
            reminderScheduler
        ) as T
    }
}
