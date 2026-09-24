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
    data object AddMaterial : Screen("add_material/{campaignId}") {
        const val ARG_CAMPAIGN_ID = "campaignId"
        fun createRoute(campaignId: Long) = "add_material/$campaignId"
    }
    // campaignId is explicit so actions on the material (e.g. deletion) are scoped to the campaign
    // the user navigated from, never inferred from the document itself.
    data object MaterialDetail : Screen("material_detail/{campaignId}/{documentId}") {
        const val ARG_CAMPAIGN_ID = "campaignId"
        const val ARG_DOCUMENT_ID = "documentId"
        fun createRoute(campaignId: Long, documentId: Long) = "material_detail/$campaignId/$documentId"
    }
    data object CampaignAnalysis : Screen("campaign_analysis/{campaignId}") {
        const val ARG_CAMPAIGN_ID = "campaignId"
        fun createRoute(campaignId: Long) = "campaign_analysis/$campaignId"
    }

    // No arguments: the key is never passed through navigation.
    data object GeminiSettings : Screen("gemini_settings")
}
