package com.example.cramrescue.domain.recall

import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.TriageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallPromptBuilderTest {

    private fun card(title: String, vararg bullets: String, notes: String? = null) = MicroCard(
        id = 7,
        campaignId = 1,
        analysisGeneratedAt = NOW,
        orderIndex = 0,
        triage = TriageLevel.CODE_RED,
        title = title,
        bullets = bullets.toList(),
        notes = notes,
        sourceRefs = listOf(SourceRef(10, 0, 3)),
        createdAt = NOW
    )

    /** The hidden keywords, or null for a General (fallback) prompt. */
    private fun answersOf(card: MicroCard): List<String>? =
        when (val prompt = RecallPromptBuilder.build(card)) {
            is RecallPrompt.Cloze -> prompt.answers(card.bullets[prompt.bulletIndex])
            RecallPrompt.General -> null
        }

    @Test
    fun vietnameseTheoryHidesTheDefinitionTail() {
        val card = card("Quang hợp", "Quang hợp là quá trình thực vật chuyển đổi năng lượng ánh sáng thành hóa năng.")
        assertEquals(listOf("hóa năng"), answersOf(card))
    }

    @Test
    fun programmingPreconditionIsHidden() {
        val card = card("Binary Search", "Binary Search yêu cầu dữ liệu đã được sắp xếp.")
        assertEquals(listOf("dữ liệu đã được sắp xếp"), answersOf(card))
    }

    @Test
    fun complexityNotationIsHiddenWhole() {
        val card = card("Độ phức tạp", "Độ phức tạp thời gian của Binary Search là O(log n).")
        assertEquals(listOf("O(log n)"), answersOf(card))
        assertEquals(listOf("O(n²)"), answersOf(card("Sắp xếp nổi bọt", "Bubble sort có độ phức tạp O(n²) trong trường hợp xấu.")))
    }

    @Test
    fun formulasAreHiddenWholeWithoutCorruption() {
        assertEquals(
            listOf("ax² + bx + c = 0"),
            answersOf(card("Phương trình bậc hai", "Phương trình bậc hai có dạng ax² + bx + c = 0."))
        )
        assertEquals(
            listOf("x = (-b ± √Δ) / 2a"),
            answersOf(card("Công thức nghiệm", "Nghiệm được tính bằng x = (-b ± √Δ) / 2a khi Δ > 0."))
        )
    }

    @Test
    fun annotationsAndIdentifiersAreHiddenIntactSkippingTheSubject() {
        assertEquals(
            listOf("@Controller", "@ResponseBody"),
            answersOf(card("@RestController", "@RestController kết hợp @Controller và @ResponseBody."))
        )
        // At most 2 keywords; generics and camelCase identifiers stay whole.
        assertEquals(
            listOf("getNames", "List<String>"),
            answersOf(card("Kiểu trả về", "Hàm getNames trả về List<String> chứa các tên."))
        )
    }

    @Test
    fun languageUsageHidesTheKeyPhrase() {
        val card = card("Present Perfect", "Present Perfect diễn tả hành động có liên hệ với hiện tại.")
        assertEquals(listOf("hiện tại"), answersOf(card))
    }

    @Test
    fun realCardPatternsFromTheCurrentCampaign() {
        // Number rule — the leading list marker style is not used, the count is.
        assertEquals(
            listOf("6"),
            answersOf(card("Quy trình phát triển phần mềm", "Gồm 6 bước: Khảo sát-Đặc tả, Phân tích-Thiết kế, Lập trình, Kiểm thử, Viết tài liệu, Bảo trì."))
        )
        assertEquals(
            listOf("Test Condition/Possibility"),
            answersOf(
                card(
                    "Kịch bản kiểm thử (Test Scenario)",
                    "Bao gồm tất cả các chức năng có thể được kiểm thử, còn gọi là Test Condition/Possibility."
                )
            )
        )
    }

    @Test
    fun fallsBackInsteadOfProducingNonsense() {
        // No formula, identifier, number or short definition tail.
        assertEquals(null, answersOf(card("Lời khuyên", "Ôn tập kỹ phần này trước khi thi.")))
        // Bullets truncated by the 3-Bullet Budget are never used.
        assertEquals(null, answersOf(card("Kiểm thử", "Kiểm thử là quá trình thực nghiệm phát hiện lỗi và nhu…")))
        // A keyword shown in the title would give the answer away.
        assertEquals(null, answersOf(card("Hóa năng", "Quang hợp là quá trình thực vật chuyển đổi năng lượng ánh sáng thành hóa năng.")))
        // A leading list number is not a keyword.
        assertEquals(null, answersOf(card("Bước", "1. Khảo sát kỹ lưỡng trước khi thiết kế.")))
    }

    @Test
    fun laterBulletIsUsedWhenTheFirstHasNoSafeTarget() {
        val prompt = RecallPromptBuilder.build(card("Kiểm thử", "Ôn tập kỹ phần này.", "Độ phức tạp thời gian là O(n log n)."))
        assertEquals(1, (prompt as RecallPrompt.Cloze).bulletIndex)
    }

    @Test
    fun generationIsDeterministicAcrossCalls() {
        val card = card("Binary Search", "Binary Search yêu cầu dữ liệu đã được sắp xếp.", "Độ phức tạp là O(log n).")
        val first = RecallPromptBuilder.build(card)
        repeat(20) { assertEquals(first, RecallPromptBuilder.build(card.copy())) }
    }

    @Test
    fun everyOccurrenceIsHiddenAndTheRestIsPreservedExactly() {
        val bullet = "Chuỗi UTF-8 có dấu: tính O(1) nhanh; lặp lại O(1) vẫn đúng."
        val card = card("Truy cập", bullet)
        val prompt = RecallPromptBuilder.build(card) as RecallPrompt.Cloze
        val segments = prompt.segments(bullet)

        assertEquals(2, segments.count { it is ClozeSegment.Hidden })
        // Reassembling the segments gives back the original, byte for byte.
        assertEquals(bullet, segments.joinToString("") {
            when (it) {
                is ClozeSegment.Visible -> it.text
                is ClozeSegment.Hidden -> it.text
            }
        })
        assertTrue(segments.filterIsInstance<ClozeSegment.Visible>().none { "O(1)" in it.text })
    }

    @Test
    fun cardContentNotesAndSourcesAreNeverModifiedOrUsed() {
        val card = card("Binary Search", "Binary Search yêu cầu dữ liệu đã được sắp xếp.", notes = "Ghi chú chứa đáp án sắp xếp")
        val before = card.copy()
        val prompt = RecallPromptBuilder.build(card) as RecallPrompt.Cloze

        assertEquals(before, card)
        assertEquals(listOf(SourceRef(10, 0, 3)), card.sourceRefs)
        // The prompt only addresses a bullet; notes are not part of it.
        assertEquals(0, prompt.bulletIndex)
        assertTrue(prompt.hiddenRanges.all { it.last < card.bullets[0].length })
    }
}
