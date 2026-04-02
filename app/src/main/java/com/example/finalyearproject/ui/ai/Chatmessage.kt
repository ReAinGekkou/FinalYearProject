package com.example.finalyearproject.ui.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    val timeLabel: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    companion object {
        fun error(text: String) = ChatMessage(
            text = text,
            isFromUser = false,
            isError = true
        )
    }
}