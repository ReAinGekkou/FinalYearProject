package com.example.finalyearproject.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.remote.ai.AIService
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

/**
 * ChatViewModel
 *
 * Scoped to the PARENT ChatFragment so messages survive tab switches.
 * Does NOT write to Firestore — that is CommunityChatFragment's job.
 *
 * KEY FIX: _messages is backed by a plain immutable List, not
 * MutableList.  Every update posts a brand-new list object so
 * LiveData's value-equality check always detects a change and
 * notifies the observer.
 */
class ChatViewModel : ViewModel() {

    // Immutable List<> so every assignment is a new object reference.
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val aiService = AIService.getInstance()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // ── Step 1: add user bubble with a NEW list ───────────────────────────
        val withUser = _messages.value.orEmpty() + ChatMessage(
            text       = text,
            isFromUser = true
        )
        _messages.value = withUser          // main thread – safe

        // ── Step 2: show typing indicator ─────────────────────────────────────
        _isTyping.value = true

        viewModelScope.launch {
            // ── Step 3: call Gemini API ──────────────────────────────────────
            // AIService always returns Resource.Success (with fallback on error)
            val result = aiService.askFoodQuestion(text)

            val aiMessage = when (result) {
                is Resource.Success -> ChatMessage(
                    text       = result.data,
                    isFromUser = false
                )
                else -> ChatMessage.error(
                    "Hmm… something went wrong. Try again! 🤔"
                )
            }

            // ── Step 4: hide typing, post reply with a NEW list ──────────────
            _isTyping.postValue(false)

            // postValue runs on main thread after the coroutine resumes;
            // creating a new list guarantees the observer fires even if the
            // text happened to be identical to a previous message.
            _messages.postValue(_messages.value.orEmpty() + aiMessage)
        }
    }
}