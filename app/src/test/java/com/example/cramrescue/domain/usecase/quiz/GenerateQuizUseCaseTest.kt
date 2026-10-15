package com.example.cramrescue.domain.usecase.quiz

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.AnalysisSourceBlock
import com.example.cramrescue.domain.analysis.AnalysisSourcePage
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.quiz.DraftQuizQuestion
import com.example.cramrescue.domain.quiz.QuizClient
import com.example.cramrescue.domain.quiz.QuizDraft
import com.example.cramrescue.domain.quiz.QuizPrompt
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.repository.QuizRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateQuizUseCaseTest {

    private class FakeQuizRepository : QuizRepository {
        val savedQuizzes = mutableListOf<Quiz>()
        val savedQuestions = mutableListOf<QuizQuestion>()

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long {
            savedQuizzes.add(quiz.copy(id = 100L))
            savedQuestions.addAll(questions.map { it.copy(quizId = 100L) })
            return 100L
        }

        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = savedQuizzes
        override suspend fun getQuizById(quizId: Long): Quiz? = savedQuizzes.firstOrNull { it.id == quizId }
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? = null
        override suspend fun saveAttempt(attempt: QuizAttempt): Long = 1L
        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? = null
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {}
    }

    private class FakeCampaignRepository(private val campaign: Campaign?) : CampaignRepository {
        override suspend fun createCampaign(campaign: Campaign): Long = 1L
        override suspend fun getCampaignById(id: Long): Campaign? = campaign
        override suspend fun getCampaigns(): List<Campaign> = listOfNotNull(campaign)
        override suspend fun updateCampaign(campaign: Campaign): Boolean = true
        override suspend fun deleteCampaign(campaignId: Long): com.example.cramrescue.domain.repository.CampaignDeletion? = null
    }

    private class FakeCredentialRepo(private val key: String?) : GeminiCredentialRepository {
        override suspend fun hasCredential(): Boolean = !key.isNullOrBlank()
        override suspend fun getCredential(): String? = key
        override suspend fun saveCredential(apiKey: String) {}
        override suspend fun clearCredential() {}
    }

    private class FakeQuizClient(val draft: QuizDraft) : QuizClient {
        override val modelId: String = "gemini-3.5-flash"
        override suspend fun generateQuiz(apiKey: String, prompt: QuizPrompt): QuizDraft = draft
    }

    @Test
    fun generatesAndPersistsValidQuiz() = runBlocking {
        val campaign = Campaign(1L, "Giải tích", SubjectArchetype.NATURAL_COMPUTATION, Instant.now().plusSeconds(3600), Instant.now())
        val source = AnalysisSource(
            campaignId = 1L,
            pages = listOf(
                AnalysisSourcePage(
                    documentNumber = 1,
                    documentLabel = "Tài liệu 1",
                    documentId = 10L,
                    pageIndex = 0,
                    isLowText = false,
                    blocks = listOf(
                        AnalysisSourceBlock("B1", SourceRef(10L, 0, 0), "Đạo hàm của sin(x) là cos(x).")
                    )
                )
            ),
            documentsWithoutOcr = 0,
            emptyPageCount = 0,
            fingerprint = "fp123"
        )

        val draft = QuizDraft(
            title = "Đề thi Giải tích 1",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Đạo hàm của sin(x) là gì?",
                    options = listOf("cos(x)", "-cos(x)", "sin(x)", "-sin(x)"),
                    correctOptionIndex = 0,
                    explanation = "Theo định nghĩa đạo hàm cơ bản.",
                    sourceBlockIds = listOf("B1")
                )
            )
        )

        val quizRepo = FakeQuizRepository()
        val useCase = GenerateQuizUseCase(
            campaignRepository = FakeCampaignRepository(campaign),
            getAnalysisSource = { source },
            credentialRepository = FakeCredentialRepo("valid-api-key"),
            quizClient = FakeQuizClient(draft),
            quizRepository = quizRepo
        )

        val result = useCase(campaignId = 1L, questionCount = 10)
        assertTrue(result is GenerateQuizResult.Success)
        assertEquals(100L, (result as GenerateQuizResult.Success).quizId)
        assertEquals(1, quizRepo.savedQuizzes.size)
        assertEquals("Đề thi Giải tích 1", quizRepo.savedQuizzes.first().title)
        assertEquals(1, quizRepo.savedQuestions.size)
    }

    @Test
    fun returnsNoMaterialsWhenSourceHasNoBlocks() = runBlocking {
        val campaign = Campaign(1L, "Giải tích", SubjectArchetype.NATURAL_COMPUTATION, Instant.now().plusSeconds(3600), Instant.now())
        val emptySource = AnalysisSource(1L, emptyList(), 0, 0, "fp0")

        val useCase = GenerateQuizUseCase(
            campaignRepository = FakeCampaignRepository(campaign),
            getAnalysisSource = { emptySource },
            credentialRepository = FakeCredentialRepo("valid-api-key"),
            quizClient = FakeQuizClient(QuizDraft("", emptyList())),
            quizRepository = FakeQuizRepository()
        )

        val result = useCase(campaignId = 1L, questionCount = 10)
        assertTrue(result is GenerateQuizResult.NoMaterials)
    }
}
