package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finalyearproject.databinding.FragmentChatBinding
import com.example.finalyearproject.databinding.FragmentAiChatBinding
import com.example.finalyearproject.ui.ai.ChatAdapter
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// ChatFragment — hosts 2 tabs: AI Assistant | Community Chat
// ─────────────────────────────────────────────────────────────────────────────

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _b = FragmentChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.vpChat.adapter = ChatPagerAdapter(this)
        TabLayoutMediator(b.tabChat, b.vpChat) { tab, pos ->
            tab.text = if (pos == 0) "🤖 AI Assistant" else "💬 Community"
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private inner class ChatPagerAdapter(f: Fragment) : FragmentStateAdapter(f) {
        override fun getItemCount() = 2
        override fun createFragment(pos: Int): Fragment =
            if (pos == 0) AiChatFragment() else CommunityGroupChatFragment()
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AiChatFragment — real AI integration
// ─────────────────────────────────────────────────────────────────────────────

class AiChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatAdapter()
        b.rvChat.adapter = adapter
        b.rvChat.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }

        setupSend()
        setupChips()
        observeViewModel()
    }

    private fun setupSend() {
        b.btnSend.setOnClickListener { send() }
        b.etMessage.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }
    }

    private fun send() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")
        viewModel.sendMessage(text)
    }

    private fun setupChips() {
        b.chipSuggest.setOnClickListener    { viewModel.sendMessage("Suggest a recipe for me based on common ingredients") }
        b.chipCalories.setOnClickListener   { viewModel.sendMessage("How many calories does a typical Vietnamese bowl have?") }
        b.chipQuickMeal.setOnClickListener  { viewModel.sendMessage("Give me 3 quick meal ideas under 30 minutes") }
        b.chipNutrition.setOnClickListener  { viewModel.sendMessage("Give me nutrition tips for a healthy Vietnamese diet") }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages.toList())
            if (messages.isNotEmpty()) {
                b.rvChat.smoothScrollToPosition(messages.size - 1)
            }
        }
        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            b.cardTyping.visibility = if (typing) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ChatViewModel — manages chat messages + AI calls
// ─────────────────────────────────────────────────────────────────────────────

class ChatViewModel : ViewModel() {

    private val _messages = androidx.lifecycle.MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: androidx.lifecycle.LiveData<MutableList<ChatMessage>> = _messages

    private val _isTyping = androidx.lifecycle.MutableLiveData(false)
    val isTyping: androidx.lifecycle.LiveData<Boolean> = _isTyping

    private val aiService = com.example.finalyearproject.data.remote.ai.AIService.getInstance()

    fun sendMessage(text: String) {
        val current = _messages.value ?: mutableListOf()
        current.add(ChatMessage(text = text, isFromUser = true))
        _messages.value = current

        _isTyping.value = true

        viewModelScope.launch {
            val result = aiService.askFoodQuestion(text)
            _isTyping.postValue(false)

            val reply = when (result) {
                is Resource.Success -> result.data
                is Resource.Error   -> "Sorry, I couldn't process that. Please try again."
                else                -> "…"
            }

            val updated = _messages.value ?: mutableListOf()
            updated.add(ChatMessage(text = reply, isFromUser = false))
            _messages.postValue(updated)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CommunityGroupChatFragment — placeholder for future group chat
// ─────────────────────────────────────────────────────────────────────────────

class CommunityGroupChatFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        return android.widget.TextView(requireContext()).apply {
            text = "Community group chat\ncoming soon 💬"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#616161"))
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}