package com.example.cramrescue.presentation.campaign.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CampaignConfirmationViewModel(
    private val campaignRepository: CampaignRepository,
    private val campaignId: Long
) : ViewModel() {

    private val _campaign = MutableStateFlow<Campaign?>(null)
    val campaign: StateFlow<Campaign?> = _campaign.asStateFlow()

    init {
        viewModelScope.launch {
            _campaign.value = campaignRepository.getCampaignById(campaignId)
        }
    }
}

class CampaignConfirmationViewModelFactory(
    private val campaignRepository: CampaignRepository,
    private val campaignId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CampaignConfirmationViewModel(campaignRepository, campaignId) as T
    }
}
