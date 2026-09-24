package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Milestone 9. Append-only self-assessment history.
//  - campaignId CASCADE: a campaign is the ownership root; its study history goes with it.
//  - microCardId SET NULL: rebuilding cards (M8 replaces the whole set) must neither fail nor
//    erase history. The attempt keeps a triage/title snapshot, and a new card never inherits it.
@Entity(
    tableName = "study_attempts",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MicroCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["microCardId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("campaignId"), Index("microCardId")]
)
data class StudyAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val microCardId: Long?,
    val triage: String,
    val cardTitle: String,
    val assessment: String,
    val attemptedAtEpochMillis: Long
)
