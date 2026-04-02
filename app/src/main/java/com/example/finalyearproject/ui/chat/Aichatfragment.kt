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

    // ── ViewModel scoped to PARENT so messages survive tab switches ───────────
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

        // Must use requireParentFragment() – not requireActivity() and not
        // this fragment – otherwise ViewModel is recreated on tab switch and
        // messages are lost.
        viewModel = ViewModelProvider(requireParentFragment())[ChatViewModel::class.java]

        setupRecyclerView()
        setupInput()
        setupChips()
        observe()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        b.rvChat.apply {
            this.adapter  = adapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            itemAnimator  = null   // prevent flicker on list updates
        }
    }

    // ── Input field ───────────────────────────────────────────────────────────

    private fun setupInput() {
        b.btnSend.setOnClickListener { sendFromInput() }

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

    // ── Chips — send directly, NEVER touch the input field ───────────────────

    private fun setupChips() {
        b.chipSuggest.setOnClickListener {
            viewModel.sendMessage("Suggest a recipe I can make with chicken and rice")
        }
        b.chipCalories.setOnClickListener {
            viewModel.sendMessage("How many calories are in a bowl of pho?")
        }
        b.chipQuickMeal.setOnClickListener {
            viewModel.sendMessage("Give me 3 quick meal ideas under 30 minutes")
        }
        b.chipNutrition.setOnClickListener {
            viewModel.sendMessage("What are the healthiest Vietnamese dishes?")
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observe() {

        // Messages observer — drives the RecyclerView
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            val hasMessages = messages.isNotEmpty()

            b.layoutEmpty.visibility = if (hasMessages) View.GONE  else View.VISIBLE
            b.rvChat.visibility      = if (hasMessages) View.VISIBLE else View.GONE

            // Pass a new List object — DiffUtil detects the delta
            adapter.submitList(messages.toList()) {
                // Scroll AFTER the adapter draw pass
                b.rvChat.post {
                    if (adapter.itemCount > 0)
                        b.rvChat.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        // Typing indicator observer
        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
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

    // ── Typing bubble animation ───────────────────────────────────────────────

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
        val loading = viewModel.isTyping.value == true
        b.btnSend.isEnabled = hasText && !loading
        b.btnSend.alpha     = if (b.btnSend.isEnabled) 1f else 0.4f
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(b.etMessage.windowToken, 0)
    }

    override fun onDestroyView() {
        dotAnimSet?.cancel()
        dotAnimSet = null
        super.onDestroyView()
        _b = null
    }
}