package com.example.finalyearproject.ui.chat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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
    private var dotAnimSet: AnimatorSet? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireParentFragment())[ChatViewModel::class.java]
        setupRecyclerView()
        setupInput()
        setupChips()
        observe()
    }

    override fun onDestroyView() {
        dotAnimSet?.cancel()
        dotAnimSet = null
        super.onDestroyView()
        _b = null
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        // rvChat → android:id="@+id/rvChat" ✓
        b.rvChat.apply {
            this.adapter  = adapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            itemAnimator  = null
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private fun setupInput() {
        // btnSend → android:id="@+id/btnSend" ✓
        b.btnSend.setOnClickListener { sendFromInput() }

        // etMessage → android:id="@+id/etMessage" ✓
        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendFromInput(); true } else false
        }

        b.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {
                refreshSendButton(s?.isNotBlank() == true)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshSendButton(false)
    }

    private fun sendFromInput() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")
        hideKeyboard()
        viewModel.sendMessage(text)
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    private fun setupChips() {
        // chipSuggest   → android:id="@+id/chipSuggest"   ✓
        b.chipSuggest.setOnClickListener {
            viewModel.sendMessage("Suggest a recipe I can make with chicken and rice")
        }
        // chipCalories  → android:id="@+id/chipCalories"  ✓
        b.chipCalories.setOnClickListener {
            viewModel.sendMessage("How many calories are in a bowl of pho?")
        }
        // ✅ FIX: chipQuickMeal → android:id="@+id/chipQuickMeal" ✓
        // Previous code used b.chipQuick → DOES NOT EXIST in XML → compile error
        b.chipQuickMeal.setOnClickListener {
            viewModel.sendMessage("Give me 3 quick meal ideas under 30 minutes")
        }
        // chipNutrition → android:id="@+id/chipNutrition" ✓
        b.chipNutrition.setOnClickListener {
            viewModel.sendMessage("What are the healthiest Vietnamese dishes?")
        }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observe() {

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            val hasMessages = messages.isNotEmpty()

            // layoutEmpty → android:id="@+id/layoutEmpty" ✓
            b.layoutEmpty.visibility = if (hasMessages) View.GONE   else View.VISIBLE
            // rvChat → android:id="@+id/rvChat" ✓
            b.rvChat.visibility      = if (hasMessages) View.VISIBLE else View.GONE
            // layoutSuggestions → android:id="@+id/layoutSuggestions" ✓ — always visible
            b.layoutSuggestions.visibility = View.VISIBLE

            // ArrayList() creates a NEW reference every time → DiffUtil always runs
            adapter.submitList(ArrayList(messages)) {
                b.rvChat.post {
                    if (adapter.itemCount > 0)
                        b.rvChat.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            // tvStatus → android:id="@+id/tvStatus" ✓
            b.tvStatus.text = if (typing) "Thinking…" else "Ready to help"
            b.tvStatus.setTextColor(
                requireContext().getColor(
                    if (typing) android.R.color.holo_orange_dark
                    else        android.R.color.holo_green_dark
                )
            )

            if (typing) showTypingBubble() else hideTypingBubble()
            refreshSendButton(b.etMessage.text?.isNotBlank() == true)
        }
    }

    // ── Typing bubble ─────────────────────────────────────────────────────────
    // dot1/dot2/dot3 → CONFIRMED exist in fragment_ai_chat.xml ✓
    // cardTyping     → android:id="@+id/cardTyping" ✓

    private fun showTypingBubble() {
        b.cardTyping.visibility = View.VISIBLE
        dotAnimSet?.cancel()

        val dots = listOf(b.dot1, b.dot2, b.dot3)
        val anims = dots.mapIndexed { i, dot ->
            ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -8f, 0f).apply {
                duration     = 600
                startDelay   = i * 160L
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
        dotAnimSet = AnimatorSet().apply {
            playTogether(*anims.toTypedArray())
            start()
        }

        b.rvChat.post {
            if (adapter.itemCount > 0)
                b.rvChat.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun hideTypingBubble() {
        dotAnimSet?.cancel()
        dotAnimSet = null
        listOf(b.dot1, b.dot2, b.dot3).forEach { it.translationY = 0f }
        b.cardTyping.visibility = View.GONE
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshSendButton(hasText: Boolean) {
        val loading         = viewModel.isTyping.value == true
        b.btnSend.isEnabled = hasText && !loading
        b.btnSend.alpha     = if (b.btnSend.isEnabled) 1f else 0.4f
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(b.etMessage.windowToken, 0)
    }
}