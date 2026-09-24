package com.example.cramrescue.domain.usecase.mission

import com.example.cramrescue.domain.analysis.FakeCampaignRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.analysis.campaign
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.mission.MicroMission
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.recall.RecallSession
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.repository.MicroMissionRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.recall.RecordRecallAssessmentUseCase
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryAttempts : StudyAttemptRepository {
    val attempts = mutableListOf<StudyAttempt>()
    override suspend fun recordAttempt(attempt: StudyAttempt): Long {
        val id = attempts.size + 1L
        attempts += attempt.copy(id = id)
        return id
    }
    override suspend fun getAttemptsForCampaign(campaignId: Long) = attempts.filter { it.campaignId == campaignId }
    override suspend fun getAttemptsForCard(campaignId: Long, microCardId: Long) =
        attempts.filter { it.campaignId == campaignId && it.microCardId == microCardId }
}

private class InMemoryMissions : MicroMissionRepository {
    val missions = mutableMapOf<Long, MicroMission>()
    override suspend fun getMission(campaignId: Long) = missions[campaignId]
    override suspend fun saveMission(mission: MicroMission) {
        missions[mission.campaignId] = mission
    }
}

private class InMemoryCards : MicroCardRepository {
    private val cards = mutableMapOf<Long, List<MicroCard>>()
    private var nextId = 1L
    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }
    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

/**
 * The M10 use cases on top of the real M7 validator and M8/M9 use cases, with in-memory
 * repositories. No StudyAnalysisClient or GeminiCredentialRepository exists anywhere in this
 * graph, so none of it can make a Gemini request, read the key, or need a network.
 */
class StudyPlanUseCasesTest {

    private val ocrRepository = FakeOcrRepository(mutableMapOf())
    private val documents = FakeSourceDocumentRepository(mutableListOf())
    private val analyses = FakeStudyAnalysisRepository()
    private val cardRepository = InMemoryCards()
    private val attempts = InMemoryAttempts()
    private val missions = InMemoryMissions()
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
    private val getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)
    private val getPlan = GetStudyPlanUseCase(getCards, attempts, missions)
    private val record = RecordRecallAssessmentUseCase(attempts, Clock.fixed(NOW, ZoneOffset.UTC))
    private val getMissionCards = GetMissionCardsUseCase(getCards, attempts, missions)

    /** Campaign [campaignId] with document [campaignId] whose analysis has [red] Red and [yellow] Yellow items. */
    private fun campaignWithCards(campaignId: Long, red: Int, yellow: Int) = runBlocking {
        val blocks = red + yellow
        documents.documents += document(campaignId, campaignId)
        ocrRepository.results[campaignId] = ocr(campaignId, mapOf(0 to List(blocks) { "$LONG_TEXT $it" }))
        val refsBefore = getSource(campaignId).blocks.map { it.refId }
        val items = (0 until blocks).map { i ->
            item(title = "C$campaignId-$i", triage = if (i < red) "CODE_RED" else "CODE_YELLOW",
                bullets = listOf("Nội dung $i"), refs = listOf(refsBefore[i]))
        }
        analyses.replaceAnalysis(
            (StudyAnalysisValidator.validate(draft(*items.toTypedArray()), getSource(campaignId), "m", NOW) as ValidationResult.Valid).analysis
        )
        CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(campaignId)
        getCards(campaignId).cards
    }

    private fun startMission(campaignId: Long, clockAt: java.time.Instant = NOW) = runBlocking {
        StartMicroMissionUseCase(
            FakeCampaignRepository(listOf(campaign(1), campaign(2))), getCards, attempts, missions,
            Clock.fixed(clockAt, ZoneOffset.UTC)
        )(campaignId)
    }

    private suspend fun studyMission(campaignId: Long, results: List<RecallAssessment>) {
        var session = RecallSession.startInOrder(getMissionCards(campaignId))
        results.forEach { level ->
            session = session.reveal().beginAssessment()
            record(campaignId, session.currentCard!!, level)
            session = session.assessmentSaved(level)
        }
    }

    @Test
    fun noCardsIsUnavailableWithAReasonAndNoMission() = runBlocking {
        val plan = getPlan(1)
        assertEquals(ReadinessState.Unavailable(ReadinessUnavailableReason.NO_MATERIAL), plan.readiness)
        assertEquals(MissionPlan.NoCards, plan.mission)
        assertEquals(StartMissionResult.NothingToStudy, startMission(1))
    }

    @Test
    fun freshCardsGiveZeroPercentAndAMissionOfAllOfThem() = runBlocking {
        val cards = campaignWithCards(1, red = 2, yellow = 2)
        val plan = getPlan(1)
        assertEquals(0, (plan.readiness as ReadinessState.Available).snapshot.percent)
        val next = plan.mission as MissionPlan.Next
        // Fewer than 5 eligible: all 4, Red first, nothing duplicated.
        assertEquals(cards.map { it.id }, next.cards.map { it.id })
    }

    @Test
    fun missionFlowsThroughActiveRecallAndUpdatesReadinessAndNextMission() = runBlocking {
        val cards = campaignWithCards(1, red = 2, yellow = 2)
        assertEquals(StartMissionResult.Started(4), startMission(1))

        // Mission subset → the existing M9 session + recording → one normal attempt per card.
        studyMission(1, listOf(RecallAssessment.MASTERED, RecallAssessment.PARTIAL, RecallAssessment.MASTERED, RecallAssessment.NOT_REMEMBERED))
        assertEquals(4, attempts.attempts.size)

        val plan = getPlan(1)
        // Red 1/2 mastered, Yellow 1/2: 0.7 × 0.5 + 0.3 × 0.5 = 50.
        assertEquals(50, (plan.readiness as ReadinessState.Available).snapshot.percent)
        // Next mission from updated results: Red Tạm ổn, then Yellow Chưa nhớ; mastered excluded.
        assertEquals(listOf(cards[1].id, cards[3].id), (plan.mission as MissionPlan.Next).cards.map { it.id })
    }

    @Test
    fun pausedMissionResumesWithTheRemainingCardsInFrozenOrder() = runBlocking {
        val cards = campaignWithCards(1, red = 3, yellow = 0)
        startMission(1)
        studyMission(1, listOf(RecallAssessment.MASTERED)) // then "pause"

        val plan = getPlan(1)
        val inProgress = plan.mission as MissionPlan.InProgress
        assertEquals(listOf(cards[1].id, cards[2].id), inProgress.remaining.map { it.id })
        assertEquals(3, inProgress.total)
        assertEquals(StartMissionResult.Resumed(2), startMission(1))
        assertEquals(listOf(cards[1].id, cards[2].id), getMissionCards(1).map { it.id })
    }

    @Test
    fun allMasteredMeansNoFabricatedMission() = runBlocking {
        campaignWithCards(1, red = 1, yellow = 1)
        startMission(1)
        studyMission(1, listOf(RecallAssessment.MASTERED, RecallAssessment.MASTERED))

        val plan = getPlan(1)
        assertEquals(100, (plan.readiness as ReadinessState.Available).snapshot.percent)
        assertEquals(MissionPlan.AllMastered, plan.mission)
        assertEquals(StartMissionResult.NothingToStudy, startMission(1))
    }

    @Test
    fun campaignsNeverShareReadinessOrMissions() = runBlocking {
        val cardsA = campaignWithCards(1, red = 2, yellow = 0)
        val cardsB = campaignWithCards(2, red = 2, yellow = 0)
        startMission(2)
        studyMission(2, listOf(RecallAssessment.MASTERED, RecallAssessment.MASTERED))

        assertEquals(0, (getPlan(1).readiness as ReadinessState.Available).snapshot.percent)
        assertEquals(100, (getPlan(2).readiness as ReadinessState.Available).snapshot.percent)
        assertEquals(cardsA.map { it.id }, (getPlan(1).mission as MissionPlan.Next).cards.map { it.id })
        assertTrue(getMissionCards(1).isEmpty())
        assertTrue(cardsB.none { b -> cardsA.any { it.id == b.id } })
    }

    @Test
    fun staleCardsKeepAnEstimateButItIsMarkedStale() = runBlocking {
        campaignWithCards(1, red = 1, yellow = 1)
        documents.documents += document(50, 1) // material changed after cards were built

        val plan = getPlan(1)
        assertTrue((plan.readiness as ReadinessState.Available).isStale)
        assertTrue(plan.cardsStale)
    }

    @Test
    fun noMissionStartsOnceTheExamTimeHasPassed() = runBlocking {
        campaignWithCards(1, red = 1, yellow = 0)
        // campaign(1) exam is NOW + 36h.
        assertEquals(StartMissionResult.ExamReached, startMission(1, clockAt = NOW.plusSeconds(37 * 3600)))
        assertTrue(missions.missions.isEmpty())
        // Readiness itself doesn't depend on time.
        assertEquals(0, (getPlan(1).readiness as ReadinessState.Available).snapshot.percent)
    }
}
