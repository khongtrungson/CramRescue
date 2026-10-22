package com.example.cramrescue.domain.model

import java.util.UUID

data class StudyReminder(
    val id: Long = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE),
    val enabled: Boolean = true,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    val message: String = DEFAULT_MESSAGE
) {
    companion object {
        const val DEFAULT_MESSAGE = "Đã đến giờ ôn tập! Dành 15 phút để ôn tập ngay nào 🚀"
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 0
    }
}
