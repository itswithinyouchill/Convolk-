package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- Prompts ---
    @Query("SELECT * FROM system_prompts ORDER BY isBuiltIn DESC, name ASC")
    fun getAllPromptsFlow(): Flow<List<SystemPrompt>>

    @Query("SELECT * FROM system_prompts ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAllPrompts(): List<SystemPrompt>

    @Query("SELECT * FROM system_prompts WHERE id = :id")
    suspend fun getPromptById(id: Int): SystemPrompt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: SystemPrompt): Long

    @Query("DELETE FROM system_prompts WHERE id = :id AND isBuiltIn = 0")
    suspend fun deletePromptById(id: Int)

    @Query("SELECT COUNT(*) FROM system_prompts")
    suspend fun getPromptCount(): Int

    // --- Chat Histories ---
    @Query("SELECT * FROM chat_history ORDER BY lastUpdatedAt DESC")
    fun getAllChatsFlow(): Flow<List<ChatHistory>>

    @Query("SELECT * FROM chat_history WHERE id = :id")
    suspend fun getChatById(id: String): ChatHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatHistory)

    @Query("DELETE FROM chat_history WHERE id = :id")
    suspend fun deleteChatById(id: String)

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    @Transaction
    suspend fun deleteWholeChat(chatId: String) {
        deleteMessagesByChatId(chatId)
        deleteChatById(chatId)
    }
}
