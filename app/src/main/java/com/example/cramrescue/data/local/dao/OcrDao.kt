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
    suspend fun upsertResult(result: OcrResultEntity)

    @Insert
    suspend fun insertBlocks(blocks: List<OcrTextBlockEntity>)

    @Query("DELETE FROM ocr_text_blocks WHERE documentId = :documentId")
    suspend fun deleteBlocksForDocument(documentId: Long)

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId")
    suspend fun getResult(documentId: Long): OcrResultEntity?

    @Query("SELECT * FROM ocr_text_blocks WHERE documentId = :documentId ORDER BY orderIndex ASC")
    suspend fun getBlocksForDocument(documentId: Long): List<OcrTextBlockEntity>

    /**
     * Replaces any previous OCR result for [OcrResultEntity.documentId] atomically, so
     * reprocessing a document never leaves old and new blocks mixed together.
     */
    @Transaction
    suspend fun replaceOcrResult(result: OcrResultEntity, blocks: List<OcrTextBlockEntity>) {
        deleteBlocksForDocument(result.documentId)
        upsertResult(result)
        insertBlocks(blocks)
    }
}
