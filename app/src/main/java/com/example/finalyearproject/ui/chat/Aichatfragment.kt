package com.example.finalyearproject.ui.chat

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalyearproject.databinding.FragmentAiChatBinding
import com.example.finalyearproject.ui.ai.ChatAdapter

class AiChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Share ViewModel với parent ChatFragment → tin nhắn sống qua tab switch
        viewModel = ViewModelProvider(requireParentFragment())[ChatViewModel::class.java]

        setupRecyclerView()
        setupInput()
        setupChips()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        // rv_chat → b.rvChat
        b.rvChat.apply {
            this.adapter  = adapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            itemAnimator = null
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private fun setupInput() {
        // btn_send → b.btnSend
        b.btnSend.setOnClickListener { sendMessage() }

        // et_message → b.etMessage
        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        b.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {
                setSendEnabled(s?.toString()?.isNotBlank() == true)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setSendEnabled(false)
    }

    private fun setSendEnabled(hasText: Boolean) {
        val isLoading   = viewModel.isTyping.value == true
        b.btnSend.isEnabled = hasText && !isLoading
        b.btnSend.alpha     = if (b.btnSend.isEnabled) 1f else 0.45f
    }

    // ── Suggestion chips ──────────────────────────────────────────────────────
    // chip_suggest    → b.chipSuggest
    // chip_calories   → b.chipCalories
    // chip_quick      → b.chipQuick
    // chip_nutrition  → b.chipNutrition

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
        b.etMessage.apply {
            setText(text)
            setSelection(text.length)
            requestFocus()
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")
        hideKeyboard()
        viewModel.sendMessage(text)    // ViewModel xử lý AI — KHÔNG Firestore
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            val hasMessages = messages.any { !it.isTyping }

            // layout_empty → b.layoutEmpty
            b.layoutEmpty.visibility = if (hasMessages) View.GONE else View.VISIBLE
            // rv_chat → b.rvChat
            b.rvChat.visibility      = if (hasMessages) View.VISIBLE else View.GONE

            // ✅ layout_suggestions → b.layoutSuggestions — LUÔN VISIBLE, không bao giờ GONE
            b.layoutSuggestions.visibility = View.VISIBLE

            adapter.submitList(messages.toList()) {
                if (messages.isNotEmpty()) {
                    b.rvChat.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            // ✅ card_typing → b.cardTyping — ID đã được thêm vào XML
            b.cardTyping.visibility = if (typing) View.VISIBLE else View.GONE

            // tv_status → b.tvStatus
            b.tvStatus.text = if (typing) "Thinking…" else "Ready to help"
            b.tvStatus.setTextColor(
                if (typing)
                    resources.getColor(android.R.color.holo_orange_dark, null)
                else
                    resources.getColor(android.R.color.holo_green_dark, null)
            )

            setSendEnabled(b.etMessage.text?.isNotBlank() == true)
        }
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.etMessage.windowToken, 0)
    }
}