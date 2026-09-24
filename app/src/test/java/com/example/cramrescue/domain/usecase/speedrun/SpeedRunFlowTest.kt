package com.example.cramrescue.domain.usecase.speedrun

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
import com.example.cramrescue.domain.speedrun.SpeedRunWindow
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.mission.GetStudyPlanUseCase
import com.example.cramrescue.domain.usecase.mission.MissionPlan
import com.example.cramrescue.domain.usecase.mission.ReadinessState
import com.example.cramrescue.domain.usecase.mission.StartMicroMissionUseCase
import com.example.cramrescue.domain.usecase.recall.RecordRecallAssessmentUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Attempts : StudyAttemptRepository {
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

private class Cards : MicroCardRepository {
    private val cards = mutableMapOf<Long, List<MicroCard>>()
    private var nextId = 1L
    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }
    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

private class Missions : MicroMissionRepository {
    val missions = mutableMapOf<Long, MicroMission>()
    override suspend fun getMission(campaignId: Long) = missions[campaignId]
    override suspend fun saveMission(mission: MicroMission) {
        missions[mission.campaignId] = mission
    }
}

/**
 * Speed-run end to end on the real M8/M9/M10 use cases with in-memory repositories. No AI
 * client or key repository exists in this graph: Speed-run cannot reach Gemini or the network.
 * campaign(n) has its exam at NOW + 36h, so the emergency window is NOW + 30h .. NOW + 36h.
 */
class SpeedRunFlowTest {

    private val ocrRepository = FakeOcrRepository(mutableMapOf())
    private val documents = FakeSourceDocumentRepository(mutableListOf())
    private val analyses = FakeStudyAnalysisRepository()
    private val cardRepository = Cards()
    private val attempts = Attempts()
    private val missions = Missions()
    private val campaigns = FakeCampaignRepository(listOf(campaign(1), campaign(2)))
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
    private val getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)
    private val record = RecordRecallAssessmentUseCase(attempts, Clock.fixed(NOW, ZoneOffset.UTC))
    private val plan = GetStudyPlanUseCase(getCards, attempts, missions)

    private val inEmergency: Instant = NOW.plusSeconds(33 * 3600) // 3h before the exam
    private fun speedRun(at: Instant = inEmergency) =
        GetSpeedRunCardsUseCase(campaigns, getCards, attempts, Clock.fixed(at, ZoneOffset.UTC))

    private fun campaignWithCards(campaignId: Long, red: Int, yellow: Int): List<MicroCard> = runBlocking {
        documents.documents += document(campaignId, campaignId)
        ocrRepository.results[campaignId] = ocr(campaignId, mapOf(0 to List(red + yellow) { "$LONG_TEXT $it" }))
        val refs = getSource(campaignId).blocks.map { it.refId }
        val items = (0 until red + yellow).map { i ->
            item(title = "C$campaignId-$i", triage = if (i < red) "CODE_RED" else "CODE_YELLOW", bullets = listOf("Ý $i"), refs = listOf(refs[i]))
        }
        analyses.replaceAnalysis((StudyAnalysisValidator.validate(draft(*items.toTypedArray()), getSource(campaignId), "m", NOW) as ValidationResult.Valid).analysis)
        CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(campaignId)
        getCards(campaignId).cards
    }

    /** The existing M9 engine: frozen order, reveal before assessment, one attempt per card. */
    private suspend fun study(campaignId: Long, cards: List<MicroCard>, results: List<RecallAssessment>): RecallSession {
        var session = RecallSession.startInOrder(cards)
        results.forEach { level ->
            session = session.reveal().beginAssessment()
            // A second tap while saving is a no-op (can't start a second save).
            assertEquals(session, session.beginAssessment())
            record(campaignId, session.currentCard!!, level)
            session = session.assessmentSaved(level)
        }
        return session
    }

    private fun percent(campaignId: Long) = runBlocking {
        (plan(campaignId).readiness as ReadinessState.Available).snapshot.percent
    }

    @Test
    fun closedOutsideTheEmergencyWindowAndAfterTheExam() = runBlocking {
        campaignWithCards(1, red = 2, yellow = 1)
        assertEquals(SpeedRunStart.Closed(SpeedRunWindow.BEFORE_EMERGENCY), speedRun(at = NOW)(1))
        assertEquals(SpeedRunStart.Closed(SpeedRunWindow.EXAM_REACHED), speedRun(at = NOW.plusSeconds(36 * 3600))(1))
        assertTrue(speedRun()(1) is SpeedRunStart.Ready)
    }

    @Test
    fun speedRunStudiesOnlyRedThroughTheM9EngineAndUpdatesPassProbability() = runBlocking {
        val cards = campaignWithCards(1, red = 3, yellow = 2)
        assertEquals(0, percent(1))

        val start = speedRun()(1) as SpeedRunStart.Ready
        assertEquals(cards.take(3).map { it.id }, start.cards.map { it.id })
        assertEquals(start.cards.map { it.id }, plan(1).speedRunCards.map { it.id }) // Cockpit sees the same

        val session = study(1, start.cards, listOf(RecallAssessment.MASTERED, RecallAssessment.PARTIAL, RecallAssessment.NOT_REMEMBERED))
        assertEquals(RecallSession.Phase.COMPLETED, session.phase)
        // Ordinary study results: one per card, in the frozen order.
        assertEquals(start.cards.map { it.id }, attempts.attempts.map { it.microCardId })
        // FR-9 unchanged: Red 1/3 × 0.7 = 23.33 → 23.
        assertEquals(23, percent(1))

        // A new Speed-run recalculates: mastered card gone, Chưa nhớ first, then Tạm ổn.
        val next = speedRun()(1) as SpeedRunStart.Ready
        assertEquals(listOf(cards[2].id, cards[1].id), next.cards.map { it.id })
    }

    @Test
    fun sessionOrderStaysFrozenWhileLatestMasteryChanges() = runBlocking {
        campaignWithCards(1, red = 3, yellow = 0)
        val start = speedRun()(1) as SpeedRunStart.Ready
        var session = RecallSession.startInOrder(start.cards)
        session = session.reveal().beginAssessment()
        record(1, session.currentCard!!, RecallAssessment.NOT_REMEMBERED)
        session = session.assessmentSaved(RecallAssessment.NOT_REMEMBERED)
        // A fresh selection would now put card 1 first again — the running session does not change.
        assertEquals(start.cards[0].id, (speedRun()(1) as SpeedRunStart.Ready).cards.first().id)
        assertEquals(start.cards, session.cards)
        assertEquals(start.cards[1].id, session.currentCard?.id)
    }

    @Test
    fun allRedMasteredLeavesNothingToReview() = runBlocking {
        campaignWithCards(1, red = 2, yellow = 1)
        study(1, (speedRun()(1) as SpeedRunStart.Ready).cards, listOf(RecallAssessment.MASTERED, RecallAssessment.MASTERED))
        assertEquals(SpeedRunStart.NothingToReview, speedRun()(1))
        assertEquals(70, percent(1))
    }

    @Test
    fun noCardsMeansNothingToReview() = runBlocking {
        assertEquals(SpeedRunStart.NothingToReview, speedRun()(1))
    }

    @Test
    fun campaignsNeverShareSpeedRunCardsOrAttempts() = runBlocking {
        val cardsA = campaignWithCards(1, red = 2, yellow = 0)
        val cardsB = campaignWithCards(2, red = 2, yellow = 0)
        study(2, (speedRun()(2) as SpeedRunStart.Ready).cards, listOf(RecallAssessment.MASTERED, RecallAssessment.MASTERED))

        assertEquals(cardsA.map { it.id }, (speedRun()(1) as SpeedRunStart.Ready).cards.map { it.id })
        assertEquals(SpeedRunStart.NothingToReview, speedRun()(2))
        assertTrue(cardsB.none { b -> cardsA.any { it.id == b.id } })
    }

    @Test
    fun staleCardsCanBeSpeedRunButAreReportedStale() = runBlocking {
        campaignWithCards(1, red = 1, yellow = 0)
        documents.documents += document(9, 1) // material changed after cards were built
        val start = speedRun()(1) as SpeedRunStart.Ready
        assertTrue(start.cardsStale)
    }

    /**
     * Documents existing MVP semantics (not changed by M12): mission progress is "any attempt
     * newer than the mission baseline", so a Speed-run result on a card of an unfinished mission
     * advances that mission. Distinguishing them would need attempt provenance (schema change).
     */
    @Test
    fun speedRunResultOnAnUnfinishedMissionCardAdvancesThatMission() = runBlocking {
        val cards = campaignWithCards(1, red = 3, yellow = 0)
        StartMicroMissionUseCase(campaigns, getCards, attempts, missions, Clock.fixed(inEmergency, ZoneOffset.UTC))(1)
        assertEquals(3, (plan(1).mission as MissionPlan.InProgress).remaining.size)

        study(1, (speedRun()(1) as SpeedRunStart.Ready).cards.take(1), listOf(RecallAssessment.PARTIAL))

        val inProgress = plan(1).mission as MissionPlan.InProgress
        assertEquals(listOf(cards[1].id, cards[2].id), inProgress.remaining.map { it.id })
    }
}
