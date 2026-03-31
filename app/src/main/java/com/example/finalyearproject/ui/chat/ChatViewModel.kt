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

    private val _messages  = MutableLiveData<MutableList<ChatMessage>>(
        mutableListOf()
    )
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val aiService = AIService.getInstance()

    /**
     * Sends the user message and fetches an AI reply.
     *
     * Flow:
     *  1. Add user bubble immediately
     *  2. Show typing indicator
     *  3. Await AIService.askFoodQuestion()
     *  4. Remove typing indicator
     *  5. Add AI reply (or styled fallback on error)
     */
    fun sendMessage(text: String) {
        val list = _messages.value ?: mutableListOf()
        list.add(ChatMessage(text = text, isFromUser = true))
        _messages.value = list

        _isTyping.value = true

        viewModelScope.launch {
            val result = aiService.askFoodQuestion(text)

            _isTyping.postValue(false)

            val updated = _messages.value ?: mutableListOf()

            when (result) {
                is Resource.Success -> {
                    updated.add(ChatMessage(
                        text       = result.data,
                        isFromUser = false,
                        hasActions = result.data.contains("recipe", ignoreCase = true)
                    ))
                }
                is Resource.Error -> {
                    // Show as styled amber bubble — not raw error text
                    updated.add(ChatMessage.error(
                        "Hmm… something went wrong. Try again! 🤔\n\n" +
                                "(If this keeps happening, check your internet connection.)"
                    ))
                }
                else -> Unit
            }

            _messages.postValue(updated)
        }
    }
}