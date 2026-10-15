package com.example.cramrescue.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignUseCase
import com.example.cramrescue.domain.usecase.campaign.UpdateCampaignUseCase
import com.example.cramrescue.presentation.campaign.manage.ManageCampaignScreen
import com.example.cramrescue.presentation.campaign.manage.ManageCampaignViewModelFactory
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
import com.example.cramrescue.presentation.grounding.SourceGroundingScreen
import com.example.cramrescue.presentation.grounding.SourceGroundingViewModelFactory
import com.example.cramrescue.presentation.recall.ActiveRecallScreen
import com.example.cramrescue.presentation.recall.ActiveRecallViewModelFactory
import com.example.cramrescue.presentation.recall.StudyMode
import com.example.cramrescue.presentation.settings.GeminiSettingsScreen
import com.example.cramrescue.presentation.settings.GeminiSettingsViewModelFactory
import com.example.cramrescue.presentation.quiz.QuizListScreen
import com.example.cramrescue.presentation.quiz.QuizListViewModelFactory
import com.example.cramrescue.presentation.quiz.QuizExamScreen
import com.example.cramrescue.presentation.quiz.QuizExamViewModelFactory
import com.example.cramrescue.presentation.quiz.QuizResultScreen
import com.example.cramrescue.presentation.quiz.QuizResultViewModelFactory

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
                },
                onBack = { navController.popBackStack() }
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
            val factory = CockpitViewModelFactory(
                application.campaignRepository,
                campaignId,
                application.getStudyPlanUseCase,
                application.startMicroMissionUseCase,
                application.quizRepository
            )
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
                },
                onStartMission = { id ->
                    navController.navigate(Screen.ActiveRecall.createRoute(id, StudyMode.MISSION))
                },
                onStartSpeedRun = { id ->
                    navController.navigate(Screen.ActiveRecall.createRoute(id, StudyMode.SPEED_RUN))
                },
                onManageCampaign = { id -> navController.navigate(Screen.ManageCampaign.createRoute(id)) },
                onOpenQuizList = { id -> navController.navigate(Screen.QuizList.createRoute(id)) }
            )
        }

        composable(
            route = Screen.ManageCampaign.route,
            arguments = listOf(navArgument(Screen.ManageCampaign.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.ManageCampaign.ARG_CAMPAIGN_ID) ?: 0L
            val factory = ManageCampaignViewModelFactory(
                campaignId,
                application.campaignRepository,
                UpdateCampaignUseCase(application.campaignRepository),
                DeleteCampaignUseCase(application.campaignRepository)
            )
            ManageCampaignScreen(
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onCampaignDeleted = {
                    // Clears Cockpit + Manage (and anything else of the deleted Campaign) so Back
                    // can never reopen it; Home re-reads the list.
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
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
                application.importWordDocumentUseCase,
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
                application.documentTextRepository,
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
            arguments = listOf(
                navArgument(Screen.ActiveRecall.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.ActiveRecall.ARG_MODE) {
                    type = NavType.StringType
                    defaultValue = StudyMode.ALL.name
                }
            )
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.ActiveRecall.ARG_CAMPAIGN_ID) ?: 0L
            val mode = backStackEntry.arguments?.getString(Screen.ActiveRecall.ARG_MODE)
                ?.let { name -> StudyMode.entries.firstOrNull { it.name == name } } ?: StudyMode.ALL
            ActiveRecallScreen(
                viewModelFactory = ActiveRecallViewModelFactory(
                    campaignId,
                    mode,
                    application.getMicroCardsUseCase,
                    application.getMissionCardsUseCase,
                    application.getSpeedRunCardsUseCase,
                    application.recordRecallAssessmentUseCase
                ),
                onBack = { navController.popBackStack() },
                onOpenSource = { cardId, documentId, pageIndex ->
                    navController.navigate(Screen.SourceGrounding.createRoute(campaignId, cardId, documentId, pageIndex))
                }
            )
        }

        composable(
            route = Screen.SourceGrounding.route,
            arguments = listOf(
                navArgument(Screen.SourceGrounding.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.SourceGrounding.ARG_CARD_ID) { type = NavType.LongType },
                navArgument(Screen.SourceGrounding.ARG_DOCUMENT_ID) { type = NavType.LongType },
                navArgument(Screen.SourceGrounding.ARG_PAGE_INDEX) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            SourceGroundingScreen(
                viewModelFactory = SourceGroundingViewModelFactory(
                    campaignId = args?.getLong(Screen.SourceGrounding.ARG_CAMPAIGN_ID) ?: 0L,
                    cardId = args?.getLong(Screen.SourceGrounding.ARG_CARD_ID) ?: 0L,
                    documentId = args?.getLong(Screen.SourceGrounding.ARG_DOCUMENT_ID) ?: 0L,
                    pageIndex = args?.getInt(Screen.SourceGrounding.ARG_PAGE_INDEX) ?: 0,
                    getGroundedSource = application.getGroundedSourceUseCase,
                    loader = application.sourcePageLoader
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
                onBack = { navController.popBackStack() },
                onOpenSource = { documentId, pageIndex ->
                    navController.navigate(Screen.SourceGrounding.createRoute(campaignId, cardId, documentId, pageIndex))
                }
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

        composable(
            route = Screen.QuizList.route,
            arguments = listOf(navArgument(Screen.QuizList.ARG_CAMPAIGN_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.QuizList.ARG_CAMPAIGN_ID) ?: 0L
            val factory = QuizListViewModelFactory(
                campaignId = campaignId,
                quizRepository = application.quizRepository,
                generateQuizUseCase = application.generateQuizUseCase
            )
            QuizListScreen(
                campaignId = campaignId,
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onStartExam = { quizId ->
                    navController.navigate(Screen.QuizExam.createRoute(campaignId, quizId))
                }
            )
        }

        composable(
            route = Screen.QuizExam.route,
            arguments = listOf(
                navArgument(Screen.QuizExam.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.QuizExam.ARG_QUIZ_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.QuizExam.ARG_CAMPAIGN_ID) ?: 0L
            val quizId = backStackEntry.arguments?.getLong(Screen.QuizExam.ARG_QUIZ_ID) ?: 0L
            val factory = QuizExamViewModelFactory(
                quizId = quizId,
                quizRepository = application.quizRepository,
                submitQuizAttempt = application.submitQuizAttemptUseCase
            )
            QuizExamScreen(
                campaignId = campaignId,
                quizId = quizId,
                viewModelFactory = factory,
                onBack = { navController.popBackStack() },
                onExamFinished = { cId, qId, attemptId ->
                    navController.navigate(Screen.QuizResult.createRoute(cId, qId, attemptId)) {
                        popUpTo(Screen.QuizList.createRoute(cId))
                    }
                }
            )
        }

        composable(
            route = Screen.QuizResult.route,
            arguments = listOf(
                navArgument(Screen.QuizResult.ARG_CAMPAIGN_ID) { type = NavType.LongType },
                navArgument(Screen.QuizResult.ARG_QUIZ_ID) { type = NavType.LongType },
                navArgument(Screen.QuizResult.ARG_ATTEMPT_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val campaignId = backStackEntry.arguments?.getLong(Screen.QuizResult.ARG_CAMPAIGN_ID) ?: 0L
            val quizId = backStackEntry.arguments?.getLong(Screen.QuizResult.ARG_QUIZ_ID) ?: 0L
            val attemptId = backStackEntry.arguments?.getLong(Screen.QuizResult.ARG_ATTEMPT_ID) ?: 0L
            val factory = QuizResultViewModelFactory(
                quizId = quizId,
                attemptId = attemptId,
                quizRepository = application.quizRepository
            )
            QuizResultScreen(
                campaignId = campaignId,
                quizId = quizId,
                attemptId = attemptId,
                viewModelFactory = factory,
                onBackToList = {
                    navController.navigate(Screen.QuizList.createRoute(campaignId)) {
                        popUpTo(Screen.QuizList.createRoute(campaignId)) { inclusive = true }
                    }
                },
                onRetakeQuiz = { qId ->
                    navController.navigate(Screen.QuizExam.createRoute(campaignId, qId)) {
                        popUpTo(Screen.QuizList.createRoute(campaignId))
                    }
                }
            )
        }
    }
}
