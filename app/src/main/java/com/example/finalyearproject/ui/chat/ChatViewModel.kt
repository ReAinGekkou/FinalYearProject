package com.example.finalyearproject.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.remote.ai.AIService
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {

    private val messageList = mutableListOf<ChatMessage>()
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val aiService = AIService.getInstance()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message
        messageList.add(ChatMessage(text = text, isFromUser = true))
        _messages.value = messageList.toList()
        Log.d("ChatVM", "User message added, list size=${messageList.size}")

        _isTyping.value = true

        viewModelScope.launch {
            val result = aiService.askFoodQuestion(text)
            Log.d("ChatVM", "AI result: $result")

            val aiMessage = when (result) {
                is Resource.Success -> {
                    val responseText = result.data ?: "I'm having trouble answering right now. Please try again!"
                    ChatMessage(text = responseText, isFromUser = false)
                }
                else -> ChatMessage.error("Hmm… something went wrong. Try again! 🤔")
            }

            withContext(Dispatchers.Main) {
                messageList.add(aiMessage)
                _messages.value = messageList.toList()
                _isTyping.value = false
                Log.d("ChatVM", "AI message added, new list size=${messageList.size}")
            }
        }
    }
}