package com.example.cramrescue.presentation.cockpit

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.CampaignCountdown

data class CockpitUiState(
    val isLoading: Boolean = true,
    val campaign: Campaign? = null,
    val countdown: CampaignCountdown? = null,
    val campaignNotFound: Boolean = false
)
