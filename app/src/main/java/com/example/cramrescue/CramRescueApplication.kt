package com.example.cramrescue

import android.app.Application
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.repository.CampaignRepositoryImpl
import com.example.cramrescue.data.repository.SourceDocumentRepositoryImpl
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository

class CramRescueApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val campaignRepository: CampaignRepository by lazy {
        CampaignRepositoryImpl(database.campaignDao())
    }

    val sourceDocumentRepository: SourceDocumentRepository by lazy {
        SourceDocumentRepositoryImpl(database.sourceDocumentDao())
    }

    val materialFileStore: MaterialFileStore by lazy { MaterialFileStore(this) }
}
