package com.example.cramrescue.presentation.recall

/**
 * Which cards one Active Recall session studies. The engine (prompts, reveal, assessment,
 * attempts, sources) is the same for every mode; only card selection and labels differ.
 */
enum class StudyMode {
    /** All current cards, Code Red first (M9 "Ôn tất cả"). */
    ALL,

    /** The frozen, resumable Micro-mission (M10, FR-10). */
    MISSION,

    /** Emergency Speed-run (FR-13): every Code Red card not yet "Đã thuộc", selected at start. */
    SPEED_RUN
}
