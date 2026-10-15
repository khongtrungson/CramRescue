package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.chat.ChatPrompt
import com.example.cramrescue.domain.chat.ChatPromptMessage
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender

class BuildChatPromptUseCase {

    operator fun invoke(
        campaignName: String,
        source: AnalysisSource,
        history: List<ChatMessage>,
        newQuestion: String
    ): ChatPrompt {
        val documentContext = buildString {
            appendLine("=== TÀI LIỆU ÔN TẬP CỦA CHIẾN DỊCH: \"$campaignName\" ===")
            source.pages.forEach { page ->
                val header = if (page.isWord) {
                    "--- [Tài liệu: ${page.documentLabel}] ---"
                } else {
                    "--- [Tài liệu: ${page.documentLabel} - Trang ${page.pageIndex + 1}] ---"
                }
                appendLine(header)
                page.blocks.forEach { block ->
                    appendLine(block.text)
                }
                appendLine()
            }
            appendLine("=== HẾT TÀI LIỆU ===")
        }

        val systemInstruction = """
            Bạn là Trợ Lý Hỏi Đáp Sinh Tồn (Campaign Q&A Assistant) cho môn học "$campaignName" thuộc ứng dụng CramRescue 48h.
            Học sinh đang trong giai đoạn ôn thi cấp tốc 24–48 giờ. Bạn có nhiệm vụ giải đáp các thắc mắc về kiến thức dựa DUY NHẤT trên tài liệu được cung cấp dưới đây.

            $documentContext

            QUY TẮC BẮT BUỘC (STRICT DIRECTIVES):
            1. Tuyệt đối không ảo giác (Zero Hallucination): Chỉ trả lời dựa trên nội dung có trong tài liệu được cung cấp bên trên. Nếu câu hỏi không thể trả lời được từ tài liệu, hãy nói rõ: "Tài liệu môn học không đề cập đến nội dung này." Không suy đoán hoặc đưa thông tin ngoài lề không có trong đề cương.
            2. Trích dẫn nguồn (Source-Grounding): Khi nêu định nghĩa, công thức hay luận điểm từ tài liệu, bắt buộc phải trích dẫn theo định dạng [Tên tài liệu - Trang X] (đối với tài liệu có trang) hoặc [Tên tài liệu] (đối với tài liệu Word). Ví dụ: [Slide 1 - Trang 5].
            3. Phong cách ngắn gọn, súc tích (Emergency Speed): Trực diện, không dài dòng, không dùng giọng điệu hư cấu hay lời chào rườm rà. Dùng danh sách gạch đầu dòng (bullet points) để học sinh dễ nắm bắt kiến thức cốt lõi nhanh chóng.
        """.trimIndent()

        val promptHistory = mutableListOf<ChatPromptMessage>()
        history.forEach { msg ->
            val role = if (msg.sender == ChatSender.USER) "user" else "model"
            promptHistory.add(ChatPromptMessage(role = role, text = msg.content))
        }
        promptHistory.add(ChatPromptMessage(role = "user", text = newQuestion))

        return ChatPrompt(
            systemInstruction = systemInstruction,
            history = promptHistory
        )
    }
}
