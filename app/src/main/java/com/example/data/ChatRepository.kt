package com.example.data

import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allPromptsFlow: Flow<List<SystemPrompt>> = chatDao.getAllPromptsFlow()
    val allChatsFlow: Flow<List<ChatHistory>> = chatDao.getAllChatsFlow()

    fun getMessagesFlow(chatId: String): Flow<List<ChatMessage>> = chatDao.getMessagesForChatFlow(chatId)

    suspend fun getPromptById(id: Int): SystemPrompt? = withContext(Dispatchers.IO) {
        chatDao.getPromptById(id)
    }

    suspend fun initDefaultPrompts() = withContext(Dispatchers.IO) {
        if (chatDao.getPromptCount() == 0) {
            val defaults = listOf(
                SystemPrompt(
                    name = "General AI Assistant",
                    promptText = "You are a helpful, friendly, and knowledgeable AI assistant. Answer the user's queries clearly and concisely.",
                    isBuiltIn = true
                ),
                SystemPrompt(
                    name = "Language Translator",
                    promptText = "You are an expert language translator. Your task is to translate any text provided by the user into French, Spanish, or German, and briefly explain subtle grammar differences.",
                    isBuiltIn = true
                ),
                SystemPrompt(
                    name = "Senior Code Assistant",
                    promptText = "You are a Senior Principal Software Engineer. Help the user design architecture, write bugs-free clean code, refactor snippets, and explain complex concepts simply with code blocks.",
                    isBuiltIn = true
                ),
                SystemPrompt(
                    name = "Creative Novelist",
                    promptText = "You are an imaginative novels author and scenic story writer. Please expand the user's basic premises into detailed, dramatic narrative scenes with rich descriptions.",
                    isBuiltIn = true
                )
            )
            for (prompt in defaults) {
                chatDao.insertPrompt(prompt)
            }
        }
    }

    suspend fun createNewChat(title: String, systemPromptId: Int?, modelName: String): ChatHistory = withContext(Dispatchers.IO) {
        val chatId = System.currentTimeMillis().toString()
        val chat = ChatHistory(
            id = chatId,
            title = title,
            systemPromptId = systemPromptId,
            modelName = modelName,
            lastUpdatedAt = System.currentTimeMillis()
        )
        chatDao.insertChat(chat)
        chat
    }

    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteWholeChat(chatId)
    }

    suspend fun savePrompt(prompt: SystemPrompt) = withContext(Dispatchers.IO) {
        chatDao.insertPrompt(prompt)
    }

    suspend fun deletePrompt(id: Int) = withContext(Dispatchers.IO) {
        chatDao.deletePromptById(id)
    }

    suspend fun updateChatTitle(chatId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val currentChat = chatDao.getChatById(chatId) ?: return@withContext
        chatDao.insertChat(currentChat.copy(title = newTitle, lastUpdatedAt = System.currentTimeMillis()))
    }

    suspend fun sendMessage(
        chatId: String,
        userText: String,
        activePreferences: ChatPreferences
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Get the target chat history config
            val chat = chatDao.getChatById(chatId) ?: return@withContext Result.failure(Exception("Chat not found"))

            // 2. Clear out model loading states from local room first, insert the real prompt message
            val userMsg = ChatMessage(chatId = chatId, role = "user", text = userText)
            chatDao.insertMessage(userMsg)

            // Update timestamp of active chat
            chatDao.insertChat(chat.copy(lastUpdatedAt = System.currentTimeMillis()))

            // 3. Select API Key safely
            val apiKey = if (activePreferences.useCustomKey && activePreferences.customApiKey.isNotEmpty()) {
                activePreferences.customApiKey
            } else {
                BuildConfig.GEMINI_API_KEY
            }

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API key is not configured. Please supply your API key in the Settings Panel."))
            }

            // 4. Fetch the existing message logs context
            val history = chatDao.getMessagesForChat(chatId)
            val apiContents = history.map { msg ->
                // Map roles to API compliant terms ("user" and "model")
                val apiRole = if (msg.role == "model") "model" else "user"
                Content(
                    role = apiRole,
                    parts = listOf(Part(text = msg.text))
                )
            }

            // 5. Fetch linked prompt instructions
            var systemPromptText: String? = null
            chat.systemPromptId?.let { promptId ->
                systemPromptText = chatDao.getPromptById(promptId)?.promptText
            }

            val systemInstruction = systemPromptText?.let {
                Content(parts = listOf(Part(text = it)))
            }

            val request = GenerateContentRequest(
                contents = apiContents,
                systemInstruction = systemInstruction
            )

            // 6. Execute direct REST model query
            val response = RetrofitClient.service.generateContent(
                model = chat.modelName,
                apiKey = apiKey,
                request = request
            )

            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("Model returned an empty candidate or finished unexpectedly."))

            // 7. Store the final assistant answer
            val assistantMsg = ChatMessage(chatId = chatId, role = "model", text = replyText)
            chatDao.insertMessage(assistantMsg)

            // Update timestamp of active chat
            chatDao.insertChat(chat.copy(lastUpdatedAt = System.currentTimeMillis()))

            Result.success(replyText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
