package com.example.cramrescue.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cramrescue.data.local.dao.CampaignDao
import com.example.cramrescue.data.local.entity.CampaignEntity

@Database(entities = [CampaignEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao

    companion object {
        private const val DATABASE_NAME = "cramrescue.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
