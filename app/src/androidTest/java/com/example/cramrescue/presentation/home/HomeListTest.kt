package com.example.cramrescue.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home list (M14): empty state, sections, emergency badge, and exact-id selection. */
@RunWith(AndroidJUnit4::class)
class HomeListTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: Long, name: String, status: CampaignRowStatus, remaining: String) =
        CampaignRowUi(id, name, "Xã hội / Lý thuyết", "Thi: 26/09/2026 18:00", remaining, status)

    @Test
    fun emptyStateOffersCreateOnly() {
        var created = 0
        compose.setContent { HomeContent(HomeUiState(isLoading = false), { created++ }, { error("no campaign to open") }) }

        compose.onNodeWithText("Chưa có chiến dịch nào", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tạo chiến dịch sinh tồn").performClick()
        assertEquals(1, created)
    }

    @Test
    fun everyCampaignIsListedAndOpensWithItsOwnId() {
        val opened = mutableListOf<Long>()
        val state = HomeUiState(
            isLoading = false,
            upcoming = listOf(row(7, "Giải tích 2", CampaignRowStatus.EMERGENCY, "Còn 4 giờ 10 phút"), row(3, "Kinh tế vi mô", CampaignRowStatus.UPCOMING, "Còn 30 giờ 0 phút")),
            past = listOf(row(1, "Dược lý", CampaignRowStatus.EXAM_REACHED, "Đã đến giờ thi"))
        )
        compose.setContent { HomeContent(state, {}, { opened += it }) }

        compose.onNodeWithText("Sắp thi").assertIsDisplayed()
        compose.onNodeWithText("Đã qua giờ thi").assertIsDisplayed()
        compose.onNodeWithText("Khẩn cấp").assertIsDisplayed()
        compose.onNodeWithText("Thi: 26/09/2026 18:00 · Đã đến giờ thi").assertIsDisplayed()

        compose.onNodeWithTag("campaign-row-3").performClick()
        compose.onNodeWithTag("campaign-row-7").performClick()
        compose.onNodeWithTag("campaign-row-1").performClick()
        assertEquals(listOf(3L, 7L, 1L), opened)
    }
}
