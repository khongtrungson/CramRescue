package com.example.cramrescue.presentation.home

enum class CampaignRowStatus { UPCOMING, EMERGENCY, EXAM_REACHED }

/** One Home list row. Carries only the id for navigation — never the Campaign itself. */
data class CampaignRowUi(
    val id: Long,
    val name: String,
    val archetype: String,
    /** e.g. "Thi: 26/09/2026 18:00". */
    val examLabel: String,
    /** e.g. "Còn 38 giờ 29 phút" / "Đã đến giờ thi". */
    val remainingLabel: String,
    val status: CampaignRowStatus
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val upcoming: List<CampaignRowUi> = emptyList(),
    val past: List<CampaignRowUi> = emptyList()
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}
