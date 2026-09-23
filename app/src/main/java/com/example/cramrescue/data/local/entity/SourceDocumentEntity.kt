package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Deletion behavior: CASCADE. A SourceDocument has no meaning without its owning Campaign,
// so if a Campaign is ever deleted, its documents should go with it. There is no
// delete-campaign feature yet (Milestone 4), but this keeps referential integrity honest
// rather than leaving orphaned rows for a feature that doesn't exist yet.
@Entity(
    tableName = "source_documents",
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
data class SourceDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val sourceType: String,
    val localPath: String,
    val createdAtEpochMillis: Long
)
