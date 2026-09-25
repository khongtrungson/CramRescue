package com.example.cramrescue.presentation.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.usecase.mission.GetStudyPlanUseCase
import com.example.cramrescue.domain.usecase.mission.StartMicroMissionUseCase
import com.example.cramrescue.domain.usecase.mission.StartMissionResult
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
    private val getStudyPlan: GetStudyPlanUseCase,
    private val startMicroMission: StartMicroMissionUseCase,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CockpitUiState())
    val uiState: StateFlow<CockpitUiState> = _uiState.asStateFlow()

    init {
        // Recompute remaining time from the exam timestamp on every tick, rather than decrementing a
        // stored value, so drift can't accumulate. It always uses the most recently loaded Campaign,
        // so an exam time edited in M14's Manage screen takes effect on return. Nothing (Campaign,
        // Pass Probability, mission) is read from Room per tick — see refresh().
        viewModelScope.launch {
            while (isActive) {
                val campaign = _uiState.value.campaign
                if (campaign != null) {
                    val countdown = CampaignCountdown.from(campaign.examDateTime, Instant.now(clock))
                    _uiState.update { it.copy(countdown = countdown) }
                }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * Local re-read of the Campaign and its study state on screen (re)entry — e.g. back from a
     * mission or from editing the Campaign. Never calls Gemini.
     */
    fun refresh() {
        viewModelScope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            if (campaign == null) {
                _uiState.update { it.copy(isLoading = false, campaign = null, countdown = null, campaignNotFound = true) }
                return@launch
            }
            val countdown = CampaignCountdown.from(campaign.examDateTime, Instant.now(clock))
            _uiState.update { it.copy(isLoading = false, campaign = campaign, countdown = countdown, campaignNotFound = false) }
            val plan = getStudyPlan(campaignId)
            _uiState.update { it.copy(studyPlan = plan) }
        }
    }

    /** Explicit "Bắt đầu / Tiếp tục nhiệm vụ". [onReady] navigates to the mission's Active Recall. */
    fun startMission(onReady: () -> Unit) {
        if (_uiState.value.isStartingMission) return
        _uiState.update { it.copy(isStartingMission = true, missionMessage = null) }
        viewModelScope.launch {
            val result = startMicroMission(campaignId)
            val message = when (result) {
                is StartMissionResult.Started, is StartMissionResult.Resumed -> null
                StartMissionResult.NothingToStudy -> "Không còn thẻ nào cần ôn trong nhiệm vụ."
                StartMissionResult.ExamReached -> "Đã đến giờ thi."
                StartMissionResult.CampaignNotFound -> "Không tìm thấy chiến dịch."
            }
            _uiState.update { it.copy(isStartingMission = false, missionMessage = message) }
            if (message == null) onReady()
        }
    }
}

class CockpitViewModelFactory(
    private val campaignRepository: CampaignRepository,
    private val campaignId: Long,
    private val getStudyPlan: GetStudyPlanUseCase,
    private val startMicroMission: StartMicroMissionUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CockpitViewModel(campaignRepository, campaignId, getStudyPlan, startMicroMission) as T
    }
}
