package com.example.finalyearproject.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.remote.ai.AIService
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // ✅ List immutable — mỗi update tạo reference MỚI → observer luôn fire
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val aiService = AIService.getInstance()

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // ✅ setValue trên main thread — hiển thị user message ngay lập tức
        _messages.value = _messages.value.orEmpty() + ChatMessage(
            text       = trimmed,
            isFromUser = true
        )
        _isTyping.value = true

        viewModelScope.launch {
            // ✅ Snapshot TRƯỚC khi suspend — tránh race condition
            val snapshot = _messages.value.orEmpty()

            val result = aiService.askFoodQuestion(trimmed)

            val replyMsg = when (result) {
                is Resource.Success -> {
                    val data = result.data?.trim()
                    if (!data.isNullOrEmpty()) {
                        ChatMessage(
                            text       = data,
                            isFromUser = false,
                            hasActions = data.contains("recipe", ignoreCase = true)
                        )
                    } else {
                        ChatMessage.error("I didn't get a response. Please try again! 🤔")
                    }
                }
                is Resource.Error -> {
                    ChatMessage.error(
                        "Hmm… something went wrong. Try again! 🤔\n\n" +
                                "(If this keeps happening, check your internet connection.)"
                    )
                }
                else -> ChatMessage.error("Unexpected error. Please try again.")
            }

            // ✅ Messages trước → typing sau: UI hiển thị tin nhắn xong mới ẩn indicator
            _messages.postValue(snapshot + replyMsg)
            _isTyping.postValue(false)
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _isTyping.value = false
    }
}