package com.example.cramrescue.presentation.campaign.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignResult
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignUseCase
import com.example.cramrescue.domain.usecase.campaign.UpdateCampaignResult
import com.example.cramrescue.domain.usecase.campaign.UpdateCampaignUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edit (name, exam time) and delete for exactly one Campaign, by explicit id. Local only: no OCR,
 * no Gemini, no key read. Validation is the shared domain rule set, not duplicated here.
 */
class ManageCampaignViewModel(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val updateCampaign: UpdateCampaignUseCase,
    private val deleteCampaign: DeleteCampaignUseCase,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageCampaignUiState())
    val uiState: StateFlow<ManageCampaignUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            if (campaign == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
            } else {
                showSaved(campaign)
            }
        }
    }

    private fun showSaved(campaign: Campaign) {
        val exam = campaign.examDateTime.atZone(zone)
        _uiState.update {
            it.copy(
                isLoading = false,
                savedName = campaign.name,
                archetype = campaign.subjectArchetype.displayName,
                name = campaign.name,
                examDate = exam.toLocalDate(),
                // Exact stored time, so an untouched exam time compares equal and isn't re-validated.
                examTime = exam.toLocalTime()
            )
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, errors = emptyList(), message = null) }

    fun onDateSelected(date: LocalDate) = _uiState.update { it.copy(examDate = date, errors = emptyList(), message = null) }

    fun onTimeSelected(time: LocalTime) = _uiState.update { it.copy(examTime = time, errors = emptyList(), message = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isDeleting) return
        val examDateTime = if (state.examDate != null && state.examTime != null) {
            state.examDate.atTime(state.examTime).atZone(zone).toInstant()
        } else {
            null
        }
        _uiState.update { it.copy(isSaving = true, errors = emptyList(), message = null) }
        viewModelScope.launch {
            when (val result = updateCampaign(campaignId, state.name, examDateTime)) {
                is UpdateCampaignResult.Success -> {
                    showSaved(result.campaign)
                    _uiState.update { it.copy(isSaving = false, message = "Đã lưu thay đổi.") }
                }
                is UpdateCampaignResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.errors) }
                UpdateCampaignResult.NotFound -> _uiState.update { it.copy(isSaving = false, notFound = true) }
                UpdateCampaignResult.Failed -> _uiState.update { it.copy(isSaving = false, message = "Không thể lưu thay đổi. Hãy thử lại.") }
            }
        }
    }

    fun requestDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = true, message = null) }
    }

    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirmation = false) }

    /** Acts only when the confirmation dialog is showing — a stray call can't delete anything. */
    fun confirmDelete() {
        val state = _uiState.value
        if (!state.showDeleteConfirmation || state.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }
        viewModelScope.launch {
            when (val result = deleteCampaign(campaignId)) {
                is DeleteCampaignResult.Deleted ->
                    _uiState.update { it.copy(isDeleting = false, deleted = true, undeletedFileCount = result.undeletedFileCount) }
                // Already gone (e.g. deleted elsewhere): same destination as a successful delete.
                DeleteCampaignResult.NotFound -> _uiState.update { it.copy(isDeleting = false, deleted = true) }
                DeleteCampaignResult.Failed -> _uiState.update {
                    it.copy(isDeleting = false, message = "Không thể xóa chiến dịch. Dữ liệu vẫn còn nguyên — hãy thử lại.")
                }
            }
        }
    }
}

class ManageCampaignViewModelFactory(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val updateCampaign: UpdateCampaignUseCase,
    private val deleteCampaign: DeleteCampaignUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ManageCampaignViewModel(campaignId, campaignRepository, updateCampaign, deleteCampaign) as T
    }
}
