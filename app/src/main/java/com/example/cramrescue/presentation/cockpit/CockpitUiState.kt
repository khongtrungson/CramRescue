package com.example.cramrescue.presentation.cockpit

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.usecase.mission.StudyPlan

data class CockpitUiState(
    val isLoading: Boolean = true,
    val campaign: Campaign? = null,
    val countdown: CampaignCountdown? = null,
    val campaignNotFound: Boolean = false,
    /** Loaded on screen entry, not on each countdown tick. */
    val studyPlan: StudyPlan? = null,
    val isStartingMission: Boolean = false,
    val missionMessage: String? = null
)
