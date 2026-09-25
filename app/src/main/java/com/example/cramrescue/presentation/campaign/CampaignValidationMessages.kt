package com.example.cramrescue.presentation.campaign

import com.example.cramrescue.domain.usecase.campaign.CampaignValidationError

/** User-facing text for the shared FR-1 rules (create and edit). */
internal fun CampaignValidationError.toMessage(): String = when (this) {
    CampaignValidationError.BlankName -> "Vui lòng nhập tên môn học."
    CampaignValidationError.MissingArchetype -> "Vui lòng chọn nhóm môn học."
    CampaignValidationError.MissingExamDateTime -> "Vui lòng chọn ngày và giờ thi."
    CampaignValidationError.ExamDateInPast -> "Thời điểm thi phải ở trong tương lai."
    CampaignValidationError.ExamDateTooFar -> "Thời điểm thi không được quá 7 ngày kể từ bây giờ."
}
