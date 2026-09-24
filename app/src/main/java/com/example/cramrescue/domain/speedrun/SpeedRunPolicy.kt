package com.example.cramrescue.domain.speedrun

import com.example.cramrescue.domain.mission.MissionSelectionPolicy
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.progress.CardMastery
import com.example.cramrescue.domain.progress.mastery

/** Whether Emergency Speed-run can be started right now. */
enum class SpeedRunWindow { BEFORE_EMERGENCY, OPEN, EXAM_REACHED }

/**
 * Emergency Speed-run (requirements.md FR-13): "rà soát nhanh toàn bộ thẻ Code Red chưa đạt trạng
 * thái 'Đã thuộc' trong vòng 10 phút" — "bỏ qua các thẻ Code Yellow và các thẻ đã thuộc".
 *
 * PRD-defined: Code Red only; Yellow and mastered cards skipped; ALL such cards (no count cap —
 * the 10 minutes is a target, not a card limit); results are ordinary study results.
 * Implementation assumptions (PRD silent): order within Red reuses the Micro-mission urgency
 * (Chưa nhớ → never studied → Tạm ổn, then card order); the mode is offered only inside the
 * FR-8 emergency window of [CampaignCountdown] and never once the exam time is reached.
 */
object SpeedRunPolicy {

    /** Target duration shown to the learner (FR-13); not enforced — study time isn't recorded. */
    const val TARGET_MINUTES = 10

    /** Single source of truth for "emergency" is [CampaignCountdown] (FR-8); exam reached is separate. */
    fun window(countdown: CampaignCountdown): SpeedRunWindow = when {
        countdown.isExamReached -> SpeedRunWindow.EXAM_REACHED
        countdown.isEmergency -> SpeedRunWindow.OPEN
        else -> SpeedRunWindow.BEFORE_EMERGENCY
    }

    fun select(cards: List<MicroCard>, latest: Map<Long, RecallAssessment>): List<MicroCard> =
        cards.filter { it.triage == TriageLevel.CODE_RED && it.mastery(latest) != CardMastery.MASTERED }
            .sortedWith(compareBy({ MissionSelectionPolicy.priority(it.mastery(latest)) }, { it.orderIndex }, { it.id }))
}
