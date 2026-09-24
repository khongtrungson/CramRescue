package com.example.cramrescue.presentation.analysis

import com.example.cramrescue.domain.analysis.RemoteFailure

// FR-16: invalid key / quota errors must be clear and point to Google AI Studio.
const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"

/** User-facing text for each failure category. Never includes HTTP internals or the key. */
fun RemoteFailure.userMessage(): String = when (this) {
    RemoteFailure.InvalidCredential ->
        "Gemini từ chối API key (sai, hết hạn, bị giới hạn, hoặc là key tiêu chuẩn cũ không còn được chấp nhận). " +
            "Hãy tạo key mới trong Google AI Studio."
    is RemoteFailure.RateLimited ->
        "Gemini đang giới hạn tần suất (429)." +
            (retryAfterSeconds?.let { " Thử lại sau khoảng $it giây." } ?: " Đợi một lát rồi thử lại.")
    RemoteFailure.QuotaExhausted -> "Hạn ngạch hoặc tín dụng Gemini của key này đã hết."
    RemoteFailure.Network -> "Không có kết nối mạng. Kiểm tra Internet rồi thử lại."
    RemoteFailure.Timeout -> "Gemini phản hồi quá lâu. Thử lại."
    RemoteFailure.Service -> "Dịch vụ Gemini đang gặp sự cố. Thử lại sau."
    RemoteFailure.RequestRejected -> "Gemini từ chối yêu cầu (lỗi cấu hình ứng dụng)."
    RemoteFailure.MalformedResponse -> "Gemini trả về dữ liệu không đúng định dạng. Kết quả không được lưu."
}
