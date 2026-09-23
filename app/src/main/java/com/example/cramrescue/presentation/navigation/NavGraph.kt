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
                campaignId
            )
            AddMaterialScreen(
                viewModelFactory = factory,
                onOpenMaterial = { documentId ->
                    navController.navigate(Screen.MaterialDetail.createRoute(documentId))
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
            arguments = listOf(navArgument(Screen.MaterialDetail.ARG_DOCUMENT_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong(Screen.MaterialDetail.ARG_DOCUMENT_ID) ?: 0L
            val factory = MaterialDetailViewModelFactory(
                application.sourceDocumentRepository,
                application.ocrRepository,
                application.processDocumentOcrUseCase,
                documentId
            )
            MaterialDetailScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
