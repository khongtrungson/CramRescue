package com.example.cramrescue.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignCountdownTest {

    private val now = Instant.parse("2026-09-22T00:00:00Z")

    @Test
    fun remainingIsExactlyThirtySixHoursWhenExamIsThirtySixHoursAway() {
        val exam = now.plus(Duration.ofHours(36))

        val countdown = CampaignCountdown.from(exam, now)

        assertEquals(Duration.ofHours(36), countdown.remaining)
        assertFalse(countdown.isExamReached)
        assertFalse(countdown.isEmergency)
    }

    @Test
    fun justOverSixHoursRemainingIsNotEmergency() {
        val exam = now.plus(Duration.ofHours(6).plusSeconds(1))

        val countdown = CampaignCountdown.from(exam, now)

        assertFalse(countdown.isEmergency)
    }

    @Test
    fun exactlySixHoursRemainingIsEmergency() {
        val exam = now.plus(Duration.ofHours(6))

        val countdown = CampaignCountdown.from(exam, now)

        assertTrue(countdown.isEmergency)
    }

    @Test
    fun justUnderSixHoursRemainingIsEmergency() {
        val exam = now.plus(Duration.ofHours(5).plusMinutes(59).plusSeconds(59))

        val countdown = CampaignCountdown.from(exam, now)

        assertTrue(countdown.isEmergency)
    }

    @Test
    fun examTimeReachedExactlyYieldsZeroRemainingAndReachedFlag() {
        val exam = now

        val countdown = CampaignCountdown.from(exam, now)

        assertEquals(Duration.ZERO, countdown.remaining)
        assertTrue(countdown.isExamReached)
        assertTrue(countdown.isEmergency)
    }

    @Test
    fun examTimeInThePastNeverProducesNegativeRemaining() {
        val exam = now.minus(Duration.ofHours(2))

        val countdown = CampaignCountdown.from(exam, now)

        assertEquals(Duration.ZERO, countdown.remaining)
        assertFalse(countdown.remaining.isNegative)
        assertTrue(countdown.isExamReached)
        assertTrue(countdown.isEmergency)
    }
}
