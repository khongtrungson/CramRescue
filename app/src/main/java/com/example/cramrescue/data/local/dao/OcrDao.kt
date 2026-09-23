package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity

@Dao
interface OcrDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPageResults(pageResults: List<OcrResultEntity>)

    @Insert
    suspend fun insertBlocks(blocks: List<OcrTextBlockEntity>)

    @Query("DELETE FROM ocr_text_blocks WHERE documentId = :documentId")
    suspend fun deleteBlocksForDocument(documentId: Long)

    @Query("DELETE FROM ocr_results WHERE documentId = :documentId")
    suspend fun deletePageResultsForDocument(documentId: Long)

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPageResultsForDocument(documentId: Long): List<OcrResultEntity>

    @Query("SELECT * FROM ocr_text_blocks WHERE documentId = :documentId ORDER BY pageIndex ASC, orderIndex ASC")
    suspend fun getBlocksForDocument(documentId: Long): List<OcrTextBlockEntity>

    @Query(
        "SELECT * FROM ocr_text_blocks WHERE documentId = :documentId AND pageIndex = :pageIndex " +
            "ORDER BY orderIndex ASC"
    )
    suspend fun getBlocksForPage(documentId: Long, pageIndex: Int): List<OcrTextBlockEntity>

    /**
     * Replaces the entire OCR result for [documentId] atomically, so reprocessing a document (of
     * any page count) never leaves old and new pages/blocks mixed together or duplicated.
     */
    @Transaction
    suspend fun replaceOcrResult(
        documentId: Long,
        pageResults: List<OcrResultEntity>,
        blocks: List<OcrTextBlockEntity>
    ) {
        deleteBlocksForDocument(documentId)
        deletePageResultsForDocument(documentId)
        upsertPageResults(pageResults)
        insertBlocks(blocks)
    }
}
