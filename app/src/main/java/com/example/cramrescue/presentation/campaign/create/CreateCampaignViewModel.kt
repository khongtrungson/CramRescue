package com.example.cramrescue.presentation.campaign.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.usecase.campaign.CreateCampaignResult
import com.example.cramrescue.domain.usecase.campaign.CreateCampaignUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateCampaignViewModel(
    private val createCampaignUseCase: CreateCampaignUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCampaignUiState())
    val uiState: StateFlow<CreateCampaignUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errors = emptyList()) }
    }

    fun onArchetypeSelected(archetype: SubjectArchetype) {
        _uiState.update { it.copy(selectedArchetype = archetype, errors = emptyList()) }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(examDate = date, errors = emptyList()) }
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(examTime = time, errors = emptyList()) }
    }

    fun onNavigatedToConfirmation() {
        _uiState.update { it.copy(createdCampaignId = null) }
    }

    fun createCampaign() {
        val state = _uiState.value
        val examDateTime = if (state.examDate != null && state.examTime != null) {
            state.examDate.atTime(state.examTime).atZone(ZoneId.systemDefault()).toInstant()
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = createCampaignUseCase(state.name, state.selectedArchetype, examDateTime)) {
                is CreateCampaignResult.Success -> _uiState.update {
                    it.copy(isSaving = false, errors = emptyList(), createdCampaignId = result.campaign.id)
                }
                is CreateCampaignResult.Invalid -> _uiState.update {
                    it.copy(isSaving = false, errors = result.errors)
                }
            }
        }
    }
}

class CreateCampaignViewModelFactory(
    private val createCampaignUseCase: CreateCampaignUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CreateCampaignViewModel(createCampaignUseCase) as T
    }
}
