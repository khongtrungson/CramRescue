package com.example.cramrescue.presentation.grounding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cramrescue.domain.grounding.TextGroundedSource
import com.example.cramrescue.domain.model.DocumentTextBlock
import com.example.cramrescue.domain.model.TextBlockKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Word text-snippet viewer (FR-14): cited paragraphs labelled and scrolled into view, no pages. */
@RunWith(AndroidJUnit4::class)
class WordGroundingViewerTest {

    @get:Rule
    val compose = createComposeRule()

    private val blocks = List(60) { i ->
        DocumentTextBlock(9, i, if (i == 0) TextBlockKind.HEADING else TextBlockKind.PARAGRAPH, "Nội dung đoạn số ${i + 1}")
    }

    private fun show(cited: Set<Int>, missing: Int = 0) = compose.setContent {
        Column(Modifier.fillMaxSize()) {
            TextGroundedSourceContent(
                TextGroundedSource(1, 9, "notes.docx", blocks, cited, missingBlockCount = missing, cardStale = false)
            )
        }
    }

    @Test
    fun citedParagraphsFarDownAreScrolledIntoViewAndLabelled() {
        show(cited = setOf(45, 47))

        compose.onNodeWithText("notes.docx · văn bản Word · 2 đoạn được trích dẫn").assertIsDisplayed()
        compose.onNodeWithText("Tài liệu Word không có trang in", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Đoạn 46 — được trích dẫn").assertIsDisplayed()
        compose.onNodeWithText("Nội dung đoạn số 46").assertIsDisplayed()
        // Uncited paragraphs carry no label; nothing is presented as a page.
        assertEquals(0, compose.onAllNodesWithText("Đoạn 45 — được trích dẫn").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Trang", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun missingCitedParagraphsAreReported() {
        show(cited = setOf(1), missing = 2)
        compose.onNodeWithText("2 đoạn được trích dẫn không còn trong tài liệu", substring = true).assertIsDisplayed()
    }
}
