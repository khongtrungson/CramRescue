package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Milestone 7. The current AI analysis of one campaign. campaignId is the primary key, so a
// campaign structurally cannot have two "current" analyses — regeneration replaces this row.
@Entity(
    tableName = "campaign_analyses",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CampaignAnalysisEntity(
    @PrimaryKey val campaignId: Long,
    val summary: String,
    val discardedGreenCount: Int,
    val analyzedPageCount: Int,
    val lowTextPageCount: Int,
    val sourceFingerprint: String,
    val modelId: String,
    val generatedAtEpochMillis: Long
)

// One Code Red / Code Yellow item. bullets holds at most 3 already-budgeted lines joined by '\n'
// (the budget strips newlines inside a bullet), so a separate bullet table isn't needed.
@Entity(
    tableName = "analysis_priorities",
    foreignKeys = [
        ForeignKey(
            entity = CampaignAnalysisEntity::class,
            parentColumns = ["campaignId"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class AnalysisPriorityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val orderIndex: Int,
    val triage: String,
    val title: String,
    val bullets: String,
    val notes: String?
)

// Links a priority to one OCR block by its stable identity (document, page, block order). The
// block's bounding box is looked up locally from ocr_text_blocks; it is never sent to Gemini.
@Entity(
    tableName = "analysis_source_refs",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisPriorityEntity::class,
            parentColumns = ["id"],
            childColumns = ["priorityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("priorityId"), Index("documentId")]
)
data class AnalysisSourceRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val priorityId: Long,
    val documentId: Long,
    val pageIndex: Int,
    val blockOrderIndex: Int
)
