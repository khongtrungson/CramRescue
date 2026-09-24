package com.example.cramrescue.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.usecase.settings.CheckStoredGeminiCredentialUseCase
import com.example.cramrescue.domain.usecase.settings.CredentialCheckResult
import com.example.cramrescue.domain.usecase.settings.SaveGeminiCredentialUseCase
import com.example.cramrescue.presentation.analysis.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Deliberately holds no key: only whether one is configured. */
data class GeminiSettingsUiState(
    val isConfigured: Boolean? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
)

class GeminiSettingsViewModel(
    private val credentialRepository: GeminiCredentialRepository,
    private val saveCredential: SaveGeminiCredentialUseCase,
    private val checkStoredCredential: CheckStoredGeminiCredentialUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeminiSettingsUiState())
    val uiState: StateFlow<GeminiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = credentialRepository.hasCredential()
            _uiState.update { it.copy(isConfigured = configured) }
        }
    }

    /** Validates then saves. [onSaved] lets the screen clear its input field. */
    fun save(rawKey: String, onSaved: () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            credentialRepository.clearCredential()
            _uiState.update {
                it.copy(isConfigured = false, message = "Đã xóa API key khỏi thiết bị.", messageIsError = false)
            }
        }
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
    private val checkStoredCredential: CheckStoredGeminiCredentialUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GeminiSettingsViewModel(credentialRepository, saveCredential, checkStoredCredential) as T
    }
}
