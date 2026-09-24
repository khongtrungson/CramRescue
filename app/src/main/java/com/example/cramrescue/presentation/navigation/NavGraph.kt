package com.example.cramrescue.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cramrescue.CramRescueApplication
import com.example.cramrescue.domain.usecase.campaign.CreateCampaignUseCase
import com.example.cramrescue.presentation.analysis.CampaignAnalysisScreen
import com.example.cramrescue.presentation.analysis.CampaignAnalysisViewModelFactory
import com.example.cramrescue.presentation.campaign.confirmation.CampaignConfirmationScreen
import com.example.cramrescue.presentation.campaign.confirmation.CampaignConfirmationViewModelFactory
import com.example.cramrescue.presentation.campaign.create.CreateCampaignScreen
import com.example.cramrescue.presentation.campaign.create.CreateCampaignViewModelFactory
import com.example.cramrescue.presentation.cockpit.CockpitScreen
import com.example.cramrescue.presentation.cockpit.CockpitViewModelFactory
import com.example.cramrescue.presentation.home.HomeScreen
import com.example.cramrescue.presentation.home.HomeViewModelFactory
import com.example.cramrescue.presentation.material.add.AddMaterialScreen
import com.example.cramrescue.presentation.material.add.AddMaterialViewModelFactory
import com.example.cramrescue.presentation.material.detail.MaterialDetailScreen
import com.example.cramrescue.presentation.material.detail.MaterialDetailViewModelFactory
import com.example.cramrescue.presentation.microcard.detail.MicroCardDetailScreen
import com.example.cramrescue.presentation.microcard.detail.MicroCardDetailViewModelFactory
import com.example.cramrescue.presentation.microcard.list.MicroCardsScreen
import com.example.cramrescue.presentation.microcard.list.MicroCardsViewModelFactory
import com.example.cramrescue.presentation.recall.ActiveRecallScreen
import com.example.cramrescue.presentation.recall.ActiveRecallViewModelFactory
import com.example.cramrescue.presentation.settings.GeminiSettingsScreen
import com.example.cramrescue.presentation.settings.GeminiSettingsViewModelFactory

@Composable
fun NavGraph(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as CramRescueApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val factory = HomeViewModelFactory(application.campaignRepository)
            HomeScreen(
                viewModelFactory = factory,
                onCreateCampaignClick = { navController.navigate(Screen.CreateCampaign.route) },
                onOpenCockpit = { campaignId ->
                    navController.navigate(Screen.Cockpit.createRoute(campaignId))
                }
            )
        }

        composable(Screen.CreateCampaign.route) {
            val useCase = CreateCampaignUseCase(application.campaignRepository)
            val factory = CreateCampaignViewModelFactory(useCase)
            CreateCampaignScreen(
                viewModelFactory = factory,
                onCampaignCreated = { campaignId ->
                    navController.navigate(Screen.CampaignConfirmation.createRoute(campaignId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.CampaignConfirmation.route,
            arguments = listOf(navArgument(Screen.CampaignConfirmation.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.CampaignConfirmation.ARG_CAMPAIGN_ID) ?: 0L
            val factory = CampaignConfirmationViewModelFactory(application.campaignRepository, campaignId)
            CampaignConfirmationScreen(
                viewModelFactory = factory,
                onContinueToCockpit = { id ->
                    navController.navigate(Screen.Cockpit.createRoute(id)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBackToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Cockpit.route,
            arguments = listOf(navArgument(Screen.Cockpit.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.Cockpit.ARG_CAMPAIGN_ID) ?: 0L
            val factory = CockpitViewModelFactory(application.campaignRepository, campaignId)
            CockpitScreen(
                viewModelFactory = factory,
                onBackToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onAddMaterials = { id ->
                    navController.navigate(Screen.AddMaterial.createRoute(id))
                },
                onOpenAnalysis = { id ->
                    navController.navigate(Screen.CampaignAnalysis.createRoute(id))
                },
                onOpenGeminiSettings = { navController.navigate(Screen.GeminiSettings.route) },
                onOpenMicroCards = { id ->
                    navController.navigate(Screen.MicroCards.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.AddMaterial.route,
            arguments = listOf(navArgument(Screen.AddMaterial.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.AddMaterial.ARG_CAMPAIGN_ID) ?: 0L
            val factory = AddMaterialViewModelFactory(
                application.materialFileStore,
                application.sourceDocumentRepository,
                application.pdfPageRenderer,
                campaignId
            )
            AddMaterialScreen(
                viewModelFactory = factory,
                onOpenMaterial = { documentId ->
                    navController.navigate(Screen.MaterialDetail.createRoute(campaignId, documentId))
                },
                onBackToCockpit = {
                    navController.navigate(Screen.Cockpit.createRoute(campaignId)) {
                        popUpTo(Screen.Cockpit.createRoute(campaignId)) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.MaterialDetail.route,
            arguments = listOf(
                navArgument(Screen.MaterialDetail.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.MaterialDetail.ARG_DOCUMENT_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.MaterialDetail.ARG_CAMPAIGN_ID) ?: 0L
            val documentId = backStackEntry.arguments?.getLong(Screen.MaterialDetail.ARG_DOCUMENT_ID) ?: 0L
            val factory = MaterialDetailViewModelFactory(
                application.sourceDocumentRepository,
                application.ocrRepository,
                application.processDocumentOcrUseCase,
                application.deleteSourceDocumentUseCase,
                campaignId,
                documentId
            )
            MaterialDetailScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CampaignAnalysis.route,
            arguments = listOf(navArgument(Screen.CampaignAnalysis.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.CampaignAnalysis.ARG_CAMPAIGN_ID) ?: 0L
            val factory = CampaignAnalysisViewModelFactory(
                campaignId,
                application.campaignRepository,
                application.geminiCredentialRepository,
                application.getCampaignAnalysisUseCase,
                application.generateCampaignAnalysisUseCase
            )
            CampaignAnalysisScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.GeminiSettings.route) },
                onOpenMicroCards = { navController.navigate(Screen.MicroCards.createRoute(campaignId)) }
            )
        }

        composable(
            route = Screen.MicroCards.route,
            arguments = listOf(navArgument(Screen.MicroCards.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.MicroCards.ARG_CAMPAIGN_ID) ?: 0L
            val factory = MicroCardsViewModelFactory(
                campaignId,
                application.campaignRepository,
                application.getMicroCardsUseCase,
                application.createMicroCardsUseCase,
                application.studyAttemptRepository
            )
            MicroCardsScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onOpenAnalysis = { navController.navigate(Screen.CampaignAnalysis.createRoute(campaignId)) },
                onOpenCard = { cardId ->
                    navController.navigate(Screen.MicroCardDetail.createRoute(campaignId, cardId))
                },
                onStartStudy = { navController.navigate(Screen.ActiveRecall.createRoute(campaignId)) }
            )
        }

        composable(
            route = Screen.ActiveRecall.route,
            arguments = listOf(navArgument(Screen.ActiveRecall.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.ActiveRecall.ARG_CAMPAIGN_ID) ?: 0L
            ActiveRecallScreen(
                viewModelFactory = ActiveRecallViewModelFactory(
                    campaignId,
                    application.getMicroCardsUseCase,
                    application.recordRecallAssessmentUseCase
                ),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MicroCardDetail.route,
            arguments = listOf(
                navArgument(Screen.MicroCardDetail.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.MicroCardDetail.ARG_CARD_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.MicroCardDetail.ARG_CAMPAIGN_ID) ?: 0L
            val cardId = backStackEntry.arguments?.getLong(Screen.MicroCardDetail.ARG_CARD_ID) ?: 0L
            MicroCardDetailScreen(
                viewModelFactory = MicroCardDetailViewModelFactory(campaignId, cardId, application.getMicroCardsUseCase),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GeminiSettings.route) {
            val factory = GeminiSettingsViewModelFactory(
                application.geminiCredentialRepository,
                application.saveGeminiCredentialUseCase,
                application.checkStoredGeminiCredentialUseCase
            )
            GeminiSettingsScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
