package com.example.cramrescue.presentation.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val TICK_INTERVAL_MILLIS = 1000L

class CockpitViewModel(
    private val campaignRepository: CampaignRepository,
    private val campaignId: Long,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CockpitUiState())
    val uiState: StateFlow<CockpitUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            if (campaign == null) {
                _uiState.update { it.copy(isLoading = false, campaignNotFound = true) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = false, campaign = campaign) }

            // Recompute remaining time from the exam timestamp on every tick rather than
            // decrementing a stored value, so drift can't accumulate and re-entry is safe.
            while (isActive) {
                val countdown = CampaignCountdown.from(campaign.examDateTime, Instant.now(clock))
                _uiState.update { it.copy(countdown = countdown) }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }
}

class CockpitViewModelFactory(
    private val campaignRepository: CampaignRepository,
    private val campaignId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CockpitViewModel(campaignRepository, campaignId) as T
    }
}
