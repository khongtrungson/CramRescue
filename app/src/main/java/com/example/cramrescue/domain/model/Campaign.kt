package com.example.cramrescue.domain.model

import java.time.Instant

data class Campaign(
    val id: Long = 0L,
    val name: String,
    val subjectArchetype: SubjectArchetype,
    val examDateTime: Instant,
    val createdAt: Instant
)
