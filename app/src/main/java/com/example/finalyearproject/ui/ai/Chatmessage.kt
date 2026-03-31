package com.example.finalyearproject.ui.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ChatMessage
 *
 * Unified message model for the AI chat adapter.
 * Three types are driven by the boolean flags:
 *   isFromUser = true              → VIEW_TYPE_USER  (right, green)
 *   isFromUser = false, !isTyping  → VIEW_TYPE_AI    (left, grey)
 *   isTyping   = true              → VIEW_TYPE_TYPING (3-dot animation)
 *
 * isError = true paints the AI bubble with a soft amber tint so
 * the user immediately understands it's a fallback message.
 */
data class ChatMessage(
    val id        : String    = UUID.randomUUID().toString(),
    val text      : String,
    val isFromUser: Boolean,
    val timestamp : Long      = System.currentTimeMillis(),
    val hasActions: Boolean   = false,
    val isError   : Boolean   = false,   // amber bubble tint for fallback messages
    val isTyping  : Boolean   = false    // special type — shows 3-dot animation
) {
    /** Human-readable time string shown inside the bubble. */
    val timeLabel: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    companion object {
        /** Factory: create a typing-indicator placeholder. */
        fun typing() = ChatMessage(text = "", isFromUser = false, isTyping = true)

        /** Factory: create an error/fallback AI message. */
        fun error(fallback: String) = ChatMessage(
            text = fallback,
            isFromUser = false,
            isError = true
        )
    }
}