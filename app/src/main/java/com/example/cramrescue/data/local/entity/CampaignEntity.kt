package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val subjectArchetype: String,
    val examDateTimeEpochMillis: Long,
    val createdAtEpochMillis: Long
)
