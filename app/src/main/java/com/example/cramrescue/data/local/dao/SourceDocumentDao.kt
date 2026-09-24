package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.cramrescue.data.local.entity.SourceDocumentEntity

@Dao
interface SourceDocumentDao {
    @Insert
    suspend fun insert(entity: SourceDocumentEntity): Long

    @Query("SELECT * FROM source_documents WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun getForCampaign(campaignId: Long): List<SourceDocumentEntity>

    @Query("SELECT * FROM source_documents WHERE id = :documentId")
    suspend fun getById(documentId: Long): SourceDocumentEntity?

    @Query("SELECT * FROM source_documents WHERE id = :documentId AND campaignId = :campaignId")
    suspend fun getOwnedBy(campaignId: Long, documentId: Long): SourceDocumentEntity?

    @Query("DELETE FROM analysis_source_refs WHERE documentId = :documentId")
    suspend fun deleteAnalysisSourceRefsForDocument(documentId: Long)

    @Query("DELETE FROM micro_card_source_refs WHERE documentId = :documentId")
    suspend fun deleteMicroCardSourceRefsForDocument(documentId: Long)

    @Query("DELETE FROM ocr_text_blocks WHERE documentId = :documentId")
    suspend fun deleteOcrBlocksForDocument(documentId: Long)

    @Query("DELETE FROM ocr_results WHERE documentId = :documentId")
    suspend fun deleteOcrPagesForDocument(documentId: Long)

    @Query("DELETE FROM source_documents WHERE id = :documentId AND campaignId = :campaignId")
    suspend fun deleteOwnedBy(campaignId: Long, documentId: Long): Int

    /**
     * Deletes one document and everything that belongs to it, in one transaction, but only if it
     * belongs to [campaignId]. Dependent rows are deleted explicitly rather than relying on CASCADE.
     * Analysis and MicroCard source refs pointing at the document go too (they would point at
     * nothing); the campaign's analysis and cards are kept and show up as stale via the analysis
     * source fingerprint.
     *
     * @return the deleted row (so the caller can clean up its file), or null if nothing matched.
     */
    @Transaction
    suspend fun deleteDocumentGraph(campaignId: Long, documentId: Long): SourceDocumentEntity? {
        val document = getOwnedBy(campaignId, documentId) ?: return null
        deleteAnalysisSourceRefsForDocument(documentId)
        deleteMicroCardSourceRefsForDocument(documentId)
        deleteOcrBlocksForDocument(documentId)
        deleteOcrPagesForDocument(documentId)
        deleteOwnedBy(campaignId, documentId)
        return document
    }
}
