package com.example.cramrescue.presentation.campaign.create

import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.usecase.campaign.CampaignValidationError
import java.time.LocalDate
import java.time.LocalTime

data class CreateCampaignUiState(
    val name: String = "",
    val selectedArchetype: SubjectArchetype? = null,
    val examDate: LocalDate? = null,
    val examTime: LocalTime? = null,
    val isSaving: Boolean = false,
    val errors: List<CampaignValidationError> = emptyList(),
    val createdCampaignId: Long? = null
)
