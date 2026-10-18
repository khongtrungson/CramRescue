package com.example.cramrescue.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.campaign.CampaignListPolicy
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// One shared, coarse tick for the whole list (minutes are the finest unit shown), not a
// per-campaign one-second timer. Only re-derives rows from the loaded campaigns; no database work.
private const val LIST_TICK_MILLIS = 30_000L

/**
 * Home = the list of every persisted Campaign (M14). Selecting a row navigates with that row's id;
 * there is no "latest/current campaign" state anywhere.
 */
class HomeViewModel(
    private val campaignRepository: CampaignRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var campaigns: List<Campaign>? = null
    private val examFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(clock.zone)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(LIST_TICK_MILLIS)
                publish()
            }
        }
    }

    /** Reloads the list from Room — on every screen entry, e.g. back from creating/editing/deleting. */
    fun refresh() {
        viewModelScope.launch {
            campaigns = campaignRepository.getCampaigns()
            publish()
        }
    }

    private fun publish() {
        val loaded = campaigns ?: return
        val now = Instant.now(clock)
        val sections = CampaignListPolicy.sections(loaded, now)
        _uiState.update {
            HomeUiState(
                isLoading = false,
                upcoming = sections.upcoming.map { row(it, now) },
                past = sections.past.map { row(it, now) }
            )
        }
    }

    private fun row(campaign: Campaign, now: Instant): CampaignRowUi {
        val countdown = CampaignCountdown.from(campaign.examDateTime, now)
        return CampaignRowUi(
            id = campaign.id,
            name = campaign.name,
            archetype = campaign.subjectArchetype.displayName,
            examLabel = "Ngày thi: ${examFormat.format(campaign.examDateTime)}",
            remainingLabel = if (countdown.isExamReached) "Đã đến giờ thi" else "Còn ${remaining(countdown.remaining)}",
            status = when {
                countdown.isExamReached -> CampaignRowStatus.EXAM_REACHED
                countdown.isEmergency -> CampaignRowStatus.EMERGENCY
                else -> CampaignRowStatus.UPCOMING
            }
        )
    }

    private fun remaining(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return if (hours > 0) "$hours giờ $minutes phút" else "$minutes phút"
    }
}

class HomeViewModelFactory(
    private val campaignRepository: CampaignRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(campaignRepository) as T
    }
}
