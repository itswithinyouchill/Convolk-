package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistory(
    @PrimaryKey val id: String,
    val title: String,
    val systemPromptId: Int?, // Selected system prompt, or Null for standard chat
    val modelName: String,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
