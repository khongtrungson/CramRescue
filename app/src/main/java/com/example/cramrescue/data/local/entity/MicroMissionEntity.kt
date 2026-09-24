package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// Milestone 10. The campaign's current Micro-mission (FR-10 pause/resume), one row per campaign.
// cardIds: the frozen, ordered micro_cards ids as a comma-separated list (≤ 7). They are
// validated against the current card set on read, so cards replaced by a rebuild simply drop out
// instead of needing a foreign key. Progress is derived from study_attempts with
// id > baselineAttemptId — nothing about progress is stored here.
@Entity(
    tableName = "micro_missions",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MicroMissionEntity(
    @PrimaryKey val campaignId: Long,
    val cardIds: String,
    val baselineAttemptId: Long,
    val startedAtEpochMillis: Long
)
