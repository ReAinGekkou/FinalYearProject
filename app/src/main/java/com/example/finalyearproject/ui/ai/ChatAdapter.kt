package com.example.finalyearproject.ui.ai

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalyearproject.databinding.ItemChatAiBinding
import com.example.finalyearproject.databinding.ItemChatUserBinding

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isFromUser) VIEW_USER else VIEW_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER -> UserVH(ItemChatUserBinding.inflate(inflater, parent, false))
            else -> AiVH(ItemChatAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserVH -> holder.bind(msg)
            is AiVH -> holder.bind(msg)
        }
    }

    inner class UserVH(private val b: ItemChatUserBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            b.tvMessage.text = msg.text
            b.tvTimestamp.text = msg.timeLabel
        }
    }

    inner class AiVH(private val b: ItemChatAiBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            b.tvMessage.text = msg.text
            b.tvTimestamp.text = msg.timeLabel
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

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
    }

    companion object {
        private const val VIEW_USER = 0
        private const val VIEW_AI = 1
    }
}