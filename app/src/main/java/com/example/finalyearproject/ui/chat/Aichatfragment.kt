package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalyearproject.data.remote.ai.AIService
import com.example.finalyearproject.databinding.FragmentAiChatBinding
import com.example.finalyearproject.ui.ai.ChatAdapter
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

/**
 * AiChatFragment
 *
 * Features:
 *  • Empty state with greeting when no messages
 *  • Animated 3-dot typing indicator while AI is thinking
 *  • Real Gemini API call via AIService
 *  • Context-aware fallback if API is unavailable
 *  • Error messages shown as amber-tinted AI bubbles (not raw text)
 *  • Send button disabled while loading
 *  • Keyboard-aware layout (windowSoftInputMode = adjustResize)
 */
class AiChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter : ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Share ViewModel with parent (ChatFragment) so messages persist on tab switch
        viewModel = ViewModelProvider(requireParentFragment())[ChatViewModel::class.java]

        setupRecyclerView()
        setupInput()
        setupChips()
        observeViewModel()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        b.rvChat.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            itemAnimator = null   // disable default flicker animation
        }
    }

    // ── Input wiring ──────────────────────────────────────────────────────────

    private fun setupInput() {
        b.btnSend.setOnClickListener { sendMessage() }

        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Enable / disable send based on content
        b.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                updateSendButton(s?.toString()?.isNotBlank() == true)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        updateSendButton(false)
    }

    private fun updateSendButton(hasText: Boolean) {
        val loading = viewModel.isTyping.value == true
        b.btnSend.isEnabled = hasText && !loading
        b.btnSend.alpha     = if (b.btnSend.isEnabled) 1f else 0.45f
    }

    // ── Suggestion chips ──────────────────────────────────────────────────────

    private fun setupChips() {
        b.chipSuggest.setOnClickListener {
            prefill("Suggest a recipe I can make with chicken and rice")
        }
        b.chipCalories.setOnClickListener {
            prefill("How many calories are in a bowl of pho?")
        }
        b.chipQuick.setOnClickListener {
            prefill("Give me 3 quick meal ideas under 30 minutes")
        }
        b.chipNutrition.setOnClickListener {
            prefill("What are the healthiest Vietnamese dishes?")
        }
    }

    private fun prefill(text: String) {
        b.etMessage.setText(text)
        b.etMessage.setSelection(text.length)
        b.etMessage.requestFocus()
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return

        b.etMessage.setText("")
        hideKeyboard()
        viewModel.sendMessage(text)
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            val hasMessages = messages.any { !it.isTyping }

            // Toggle empty state vs message list
            b.layoutEmpty.visibility = if (hasMessages) View.GONE else View.VISIBLE
            b.rvChat.visibility      = if (hasMessages) View.VISIBLE else View.GONE

            // Hide suggestion chips once the user has started chatting
            b.layoutSuggestions.visibility = if (hasMessages) View.GONE else View.VISIBLE

            adapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    b.rvChat.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            b.tvStatus.text = if (typing) "Thinking…" else "Ready to help"
            b.tvStatus.setTextColor(
                if (typing) resources.getColor(android.R.color.holo_orange_dark, null)
                else resources.getColor(android.R.color.holo_green_dark, null)
            )

            if (typing) adapter.showTyping() else adapter.hideTyping()
            updateSendButton(b.etMessage.text?.isNotBlank() == true)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(b.etMessage.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}


