package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Milestone 8. Cards are owned by the campaign, not by analysis_priorities: priorities are
// replaced on every analysis regeneration, while a card set must survive that (shown as stale)
// until the user explicitly rebuilds it. analysisGeneratedAtEpochMillis records which analysis
// generation the card came from. bullets: at most 3 budgeted lines joined by '\n'.
@Entity(
    tableName = "micro_cards",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class MicroCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val analysisGeneratedAtEpochMillis: Long,
    val orderIndex: Int,
    val triage: String,
    val title: String,
    val bullets: String,
    val notes: String?,
    val createdAtEpochMillis: Long
)

// Same stable block identity as analysis_source_refs: (document, page, block order) resolves to
// an ocr_text_blocks row and its bounding box for future Source Grounding.
@Entity(
    tableName = "micro_card_source_refs",
    foreignKeys = [
        ForeignKey(
            entity = MicroCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["microCardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("microCardId"), Index("documentId")]
)
data class MicroCardSourceRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val microCardId: Long,
    val documentId: Long,
    val pageIndex: Int,
    val blockOrderIndex: Int
)
