package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cramrescue.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessages(campaignId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE campaignId = :campaignId")
    suspend fun clearMessages(campaignId: Long): Int
}
