package com.example.cramrescue.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CreateCampaign : Screen("create_campaign")
    data object CampaignConfirmation : Screen("campaign_confirmation/{campaignId}") {
        const val ARG_CAMPAIGN_ID = "campaignId"
        fun createRoute(campaignId: Long) = "campaign_confirmation/$campaignId"
    }
    data object Cockpit : Screen("cockpit/{campaignId}") {
        const val ARG_CAMPAIGN_ID = "campaignId"
        fun createRoute(campaignId: Long) = "cockpit/$campaignId"
    }
}
