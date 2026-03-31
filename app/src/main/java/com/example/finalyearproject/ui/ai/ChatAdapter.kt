package com.example.finalyearproject.ui.ai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ItemChatAiBinding
import com.example.finalyearproject.databinding.ItemChatTypingBinding
import com.example.finalyearproject.databinding.ItemChatUserBinding

/**
 * ChatAdapter
 *
 * Three view types:
 *   VIEW_USER   — right-aligned green bubble
 *   VIEW_AI     — left-aligned grey bubble with avatar
 *   VIEW_TYPING — animated 3-dot typing indicator
 *
 * Usage:
 *   adapter.submitList(messages)
 *   // To show typing: adapter.showTyping()
 *   // To hide typing: adapter.hideTyping()
 */
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    private val messages = mutableListOf<ChatMessage>()
    private var isShowingTyping = false

    // ── Submit helpers ────────────────────────────────────────────────────────

    override fun submitList(list: List<ChatMessage>?) {
        messages.clear()
        if (list != null) messages.addAll(list)
        if (isShowingTyping) messages.add(ChatMessage.typing())
        super.submitList(messages.toList())
    }

    fun addMessage(msg: ChatMessage) {
        val newList = currentList.toMutableList()
        // Remove any existing typing indicator before adding real message
        newList.removeAll { it.isTyping }
        isShowingTyping = false
        newList.add(msg)
        super.submitList(newList)
    }

    fun showTyping() {
        if (isShowingTyping) return
        isShowingTyping = true
        val newList = currentList.toMutableList()
        newList.add(ChatMessage.typing())
        super.submitList(newList)
    }

    fun hideTyping() {
        if (!isShowingTyping) return
        isShowingTyping = false
        val newList = currentList.toMutableList()
        newList.removeAll { it.isTyping }
        super.submitList(newList)
    }

    // ── ViewType ──────────────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        return when {
            msg.isTyping    -> VIEW_TYPING
            msg.isFromUser  -> VIEW_USER
            else            -> VIEW_AI
        }
    }

    // ── onCreateViewHolder ────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER   -> UserVH(ItemChatUserBinding.inflate(inflater, parent, false))
            VIEW_TYPING -> TypingVH(ItemChatTypingBinding.inflate(inflater, parent, false))
            else        -> AiVH(ItemChatAiBinding.inflate(inflater, parent, false))
        }
    }

    // ── onBindViewHolder ──────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserVH   -> holder.bind(getItem(position))
            is AiVH     -> holder.bind(getItem(position))
            is TypingVH -> holder.startAnimation()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is TypingVH) holder.stopAnimation()
        super.onViewRecycled(holder)
    }

    // ── ViewHolder: User ──────────────────────────────────────────────────────

    inner class UserVH(private val b: ItemChatUserBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(msg: ChatMessage) {
            b.tvMessage.text   = msg.text
            b.tvTimestamp.text = msg.timeLabel
        }
    }

    // ── ViewHolder: AI ────────────────────────────────────────────────────────

    inner class AiVH(private val b: ItemChatAiBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(msg: ChatMessage) {
            b.tvMessage.text   = msg.text
            b.tvTimestamp.text = msg.timeLabel

            // Error/fallback messages get an amber tint + italic text
            if (msg.isError) {
                b.cardBubble.setCardBackgroundColor(Color.parseColor("#FFF8E1"))
                b.tvMessage.setTextColor(Color.parseColor("#5D4037"))
                b.tvSenderLabel.text = "Food AI ⚠️"
            } else {
                b.cardBubble.setCardBackgroundColor(Color.parseColor("#F1F3F4"))
                b.tvMessage.setTextColor(Color.parseColor("#212121"))
                b.tvSenderLabel.text = "Food AI"
            }
        }
    }

    // ── ViewHolder: Typing ────────────────────────────────────────────────────

    inner class TypingVH(private val b: ItemChatTypingBinding) :
        RecyclerView.ViewHolder(b.root) {

        private var animatorSet: AnimatorSet? = null

        fun startAnimation() {
            stopAnimation()
            val dots = listOf(b.dot1, b.dot2, b.dot3)
            val set  = AnimatorSet()
            val anims = dots.mapIndexed { index, dot ->
                val up = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -8f, 0f).apply {
                    duration    = 600
                    startDelay  = index * 160L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                up
            }
            set.playTogether(*anims.toTypedArray())
            set.start()
            animatorSet = set
        }

        fun stopAnimation() {
            animatorSet?.cancel()
            animatorSet = null
            listOf(b.dot1, b.dot2, b.dot3).forEach { it.translationY = 0f }
        }
    }

    // ── DiffCallback ──────────────────────────────────────────────────────────

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }

    companion object {
        private const val VIEW_USER   = 0
        private const val VIEW_AI     = 1
        private const val VIEW_TYPING = 2
    }
}