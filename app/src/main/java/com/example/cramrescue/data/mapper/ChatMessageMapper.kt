package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.ChatMessageEntity
import com.example.cramrescue.domain.model.ChatCitation
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender
import org.json.JSONArray
import org.json.JSONObject

fun ChatMessage.toEntity(): ChatMessageEntity {
    val json = if (citations.isNotEmpty()) {
        val array = JSONArray()
        citations.forEach { citation ->
            val obj = JSONObject().apply {
                put("documentId", citation.documentId)
                put("documentLabel", citation.documentLabel)
                put("pageIndex", citation.pageIndex)
            }
            array.put(obj)
        }
        array.toString()
    } else {
        null
    }

    return ChatMessageEntity(
        id = id,
        campaignId = campaignId,
        sender = sender.name,
        content = content,
        timestamp = timestamp,
        citationsJson = json
    )
}

fun ChatMessageEntity.toDomain(): ChatMessage {
    val citationsList = mutableListOf<ChatCitation>()
    if (!citationsJson.isNullOrBlank()) {
        try {
            val array = JSONArray(citationsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                citationsList.add(
                    ChatCitation(
                        documentId = obj.optLong("documentId", 0L),
                        documentLabel = obj.optString("documentLabel", ""),
                        pageIndex = obj.optInt("pageIndex", 0)
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore malformed citation JSON and return empty citations
        }
    }

    return ChatMessage(
        id = id,
        campaignId = campaignId,
        sender = try {
            ChatSender.valueOf(sender)
        } catch (_: Exception) {
            ChatSender.USER
        },
        content = content,
        timestamp = timestamp,
        citations = citationsList
    )
}
