package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.DocumentTextDao
import com.example.cramrescue.data.local.entity.DocumentTextBlockEntity
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.DocumentTextBlock
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.repository.DocumentTextRepository
import com.example.cramrescue.domain.word.ExtractedTextBlock
import java.time.Instant

class DocumentTextRepositoryImpl(
    private val documentTextDao: DocumentTextDao
) : DocumentTextRepository {

    override suspend fun saveWordDocument(document: SourceDocument, blocks: List<ExtractedTextBlock>, extractedAt: Instant): Long =
        documentTextDao.insertWordDocument(
            document.toEntity(),
            blocks.mapIndexed { index, block ->
                DocumentTextBlockEntity(
                    documentId = 0L, // assigned inside the transaction
                    orderIndex = index,
                    kind = block.kind.name,
                    text = block.text,
                    extractedAtEpochMillis = extractedAt.toEpochMilli()
                )
            }
        )

    override suspend fun getText(documentId: Long): DocumentText? {
        val rows = documentTextDao.getBlocks(documentId)
        if (rows.isEmpty()) return null
        return DocumentText(
            documentId = documentId,
            extractedAt = Instant.ofEpochMilli(rows.first().extractedAtEpochMillis),
            blocks = rows.map { row ->
                DocumentTextBlock(
                    documentId = row.documentId,
                    orderIndex = row.orderIndex,
                    // An unknown stored kind degrades to a plain paragraph rather than failing.
                    kind = TextBlockKind.entries.firstOrNull { it.name == row.kind } ?: TextBlockKind.PARAGRAPH,
                    text = row.text
                )
            }
        )
    }
}
