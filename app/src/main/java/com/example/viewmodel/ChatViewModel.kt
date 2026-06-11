package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatPreferences
import com.example.data.ChatRepository
import com.example.data.ChatHistory
import com.example.data.SystemPrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    context: Context
) : ViewModel() {

    private val preferences = ChatPreferences(context)

    // UI Tab selection: 0 = Chat Workspace, 1 = Prompt Preset Central, 2 = Secure settings Page
    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // Database Flows
    val allPrompts: StateFlow<List<SystemPrompt>> = repository.allPromptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChats: StateFlow<List<ChatHistory>> = repository.allChatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Session
    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId != null) {
                repository.getMessagesFlow(chatId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Configuration options for NEW chats
    private val _activePromptId = MutableStateFlow<Int?>(null)
    val activePromptId: StateFlow<Int?> = _activePromptId.asStateFlow()

    // Local configuration binders mapped to Secure SharedPreferences
    private val _customApiKey = MutableStateFlow(preferences.customApiKey)
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _useCustomKey = MutableStateFlow(preferences.useCustomKey)
    val useCustomKey: StateFlow<Boolean> = _useCustomKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(preferences.selectedModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Interactive Field Values
    private val _activeInputText = MutableStateFlow("")
    val activeInputText: StateFlow<String> = _activeInputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initDefaultPrompts()
        }
    }

    // Tab Navigation
    fun setActiveTab(index: Int) {
        _activeTab.value = index
    }

    // Config updating
    fun updateCustomApiKey(key: String) {
        _customApiKey.value = key
        preferences.customApiKey = key
    }

    fun updateUseCustomKey(use: Boolean) {
        _useCustomKey.value = use
        preferences.useCustomKey = use
    }

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
        preferences.selectedModel = model
    }

    fun selectPromptForNewChat(promptId: Int?) {
        _activePromptId.value = promptId
    }

    fun setInputText(text: String) {
        _activeInputText.value = text
    }

    // Session selection
    fun selectActiveChat(chatId: String?) {
        _selectedChatId.value = chatId
        if (chatId != null) {
            // Find active chat configured prompt and model, to keep selections perfectly synchronized
            viewModelScope.launch {
                val chat = repository.allChatsFlow.stateIn(viewModelScope).value.find { it.id == chatId }
                chat?.let {
                    _selectedModel.value = it.modelName
                    _activePromptId.value = it.systemPromptId
                }
            }
        }
    }

    // Delete existing chat
    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
            if (_selectedChatId.value == chatId) {
                _selectedChatId.value = null
            }
        }
    }

    // Send logic
    fun sendActiveMessage() {
        val messageText = _activeInputText.value.trim()
        if (messageText.isEmpty() || _isLoading.value) return

        _activeInputText.value = ""
        _errorText.value = null

        viewModelScope.launch {
            _isLoading.value = true
            var chatId = _selectedChatId.value

            if (chatId == null) {
                // Instantly generate persistent conversation row
                val title = if (messageText.length > 30) messageText.take(30) + "..." else messageText
                val newChat = repository.createNewChat(
                    title = title,
                    systemPromptId = _activePromptId.value,
                    modelName = _selectedModel.value
                )
                chatId = newChat.id
                _selectedChatId.value = chatId
            }

            val result = repository.sendMessage(chatId, messageText, preferences)
            if (result.isFailure) {
                _errorText.value = result.exceptionOrNull()?.message ?: "An unexpected error occurred"
                // Insert local error placeholder so chat context is not fully blanked for the user
                repository.insertMessage(
                    ChatMessage(
                        chatId = chatId,
                        role = "model",
                        text = "⚠️ Failed to transmit. Error details: ${_errorText.value}"
                    )
                )
            }
            _isLoading.value = false
        }
    }

    fun dismissError() {
        _errorText.value = null
    }

    // Prompt presets logic
    fun savePrompt(id: Int, name: String, text: String) {
        if (name.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            repository.savePrompt(
                SystemPrompt(
                    id = id,
                    name = name.trim(),
                    promptText = text.trim(),
                    isBuiltIn = false
                )
            )
        }
    }

    fun deletePrompt(id: Int) {
        viewModelScope.launch {
            repository.deletePrompt(id)
            if (_activePromptId.value == id) {
                _activePromptId.value = null
            }
        }
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
