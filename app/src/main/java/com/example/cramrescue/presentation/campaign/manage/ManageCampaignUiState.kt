package com.example.cramrescue.presentation.campaign.manage

import com.example.cramrescue.domain.usecase.campaign.CampaignValidationError
import java.time.LocalDate
import java.time.LocalTime

data class ManageCampaignUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** Name as currently saved (dialog/title text), separate from the edited [name]. */
    val savedName: String = "",
    val archetype: String = "",
    val name: String = "",
    val examDate: LocalDate? = null,
    val examTime: LocalTime? = null,
    val isSaving: Boolean = false,
    val errors: List<CampaignValidationError> = emptyList(),
    val message: String? = null,
    /** Delete runs only from the explicit confirmation dialog. */
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    /** Set once the Campaign is gone; the screen then leaves (after reporting any leftover files). */
    val deleted: Boolean = false,
    val undeletedFileCount: Int = 0
)
