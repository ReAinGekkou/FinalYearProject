package com.example.finalyearproject.ui.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalyearproject.databinding.ItemChatAiBinding
import com.example.finalyearproject.databinding.ItemChatUserBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatAdapter — Day 5
 *
 * Two view types: VIEW_TYPE_USER and VIEW_TYPE_AI.
 * Uses ListAdapter + DiffUtil for efficient updates.
 * Auto-scrolls to newest message when list updates.
 */
class ChatAdapter(
    private val onShowRecipeClick: ((ChatMessage) -> Unit)? = null,
    private val onNutritionClick: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isFromUser) VIEW_TYPE_USER else VIEW_TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemChatUserBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                UserViewHolder(binding)
            }
            else -> {
                val binding = ItemChatAiBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AiViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(getItem(position))
            is AiViewHolder   -> holder.bind(getItem(position))
        }
    }

    // ── User ViewHolder ───────────────────────────────────────────────────────

    inner class UserViewHolder(
        private val binding: ItemChatUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.tvMessage.text = message.text
            binding.tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    // ── AI ViewHolder ─────────────────────────────────────────────────────────

    inner class AiViewHolder(
        private val binding: ItemChatAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnActionRecipe.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onShowRecipeClick?.invoke(getItem(position))
                }
            }
            binding.btnActionNutrition.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onNutritionClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(message: ChatMessage) {
            // Simple markdown-bold: **text** → SpannableString
            binding.tvMessage.text = formatMarkdown(message.text)
            binding.tvTimestamp.text = formatTime(message.timestamp)

            // Show action buttons only for recipe-type AI responses
            val showActions = message.hasActions
            binding.layoutActions.visibility =
                if (showActions) android.view.View.VISIBLE else android.view.View.GONE
        }

        /**
         * Minimal markdown renderer for **bold** text.
         * For production, use a library like `Markwon`.
         */
        private fun formatMarkdown(text: String): CharSequence {
            if (!text.contains("**")) return text
            val spannable = android.text.SpannableStringBuilder(text)
            val pattern = Regex("\\*\\*(.+?)\\*\\*")
            var offset = 0
            pattern.findAll(text).forEach { match ->
                val start = match.range.first - offset
                val end = match.range.last + 1 - offset
                val content = match.groupValues[1]
                spannable.replace(start, end, content)
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    start + content.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                offset += (match.value.length - content.length)
            }
            return spannable
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun formatTime(timestamp: Long): String =
        timeFormat.format(Date(timestamp))

    // ── DiffCallback ──────────────────────────────────────────────────────────

    private class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI   = 2
    }
}

// ── Data model for chat messages ──────────────────────────────────────────────

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val hasActions: Boolean = false   // true if AI response includes recipe actions
)