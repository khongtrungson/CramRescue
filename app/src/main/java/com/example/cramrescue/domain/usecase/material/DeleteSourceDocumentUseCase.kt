package com.example.cramrescue.domain.usecase.material

import com.example.cramrescue.domain.repository.SourceDocumentRepository
import kotlinx.coroutines.CancellationException

sealed interface DeleteDocumentResult {
    data object Deleted : DeleteDocumentResult
    data object NotFound : DeleteDocumentResult

    /** The document exists but belongs to a different campaign; nothing was deleted. */
    data object NotInCampaign : DeleteDocumentResult
    data object Failed : DeleteDocumentResult
}

/**
 * Deletes one material from one campaign, by explicit ids. Never triggers AI analysis: an existing
 * analysis simply becomes stale (its source fingerprint no longer matches the campaign).
 */
class DeleteSourceDocumentUseCase(
    private val sourceDocumentRepository: SourceDocumentRepository
) {
    suspend operator fun invoke(campaignId: Long, documentId: Long): DeleteDocumentResult {
        val document = sourceDocumentRepository.getDocumentById(documentId)
            ?: return DeleteDocumentResult.NotFound
        if (document.campaignId != campaignId) return DeleteDocumentResult.NotInCampaign

        return try {
            if (sourceDocumentRepository.deleteDocument(campaignId, documentId)) {
                DeleteDocumentResult.Deleted
            } else {
                // Deleted concurrently between the lookup and the transaction.
                DeleteDocumentResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DeleteDocumentResult.Failed
        }
    }
}
