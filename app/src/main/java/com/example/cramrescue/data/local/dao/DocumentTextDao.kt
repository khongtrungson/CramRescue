package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.cramrescue.data.local.entity.DocumentTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity

@Dao
interface DocumentTextDao {

    @Insert
    suspend fun insertDocument(document: SourceDocumentEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<DocumentTextBlockEntity>)

    @Query("SELECT * FROM document_text_blocks WHERE documentId = :documentId ORDER BY orderIndex ASC")
    suspend fun getBlocks(documentId: Long): List<DocumentTextBlockEntity>

    /** The Word document row and all of its text, together or not at all. */
    @Transaction
    suspend fun insertWordDocument(document: SourceDocumentEntity, blocks: List<DocumentTextBlockEntity>): Long {
        val documentId = insertDocument(document)
        insertBlocks(blocks.map { it.copy(documentId = documentId) })
        return documentId
    }
}
