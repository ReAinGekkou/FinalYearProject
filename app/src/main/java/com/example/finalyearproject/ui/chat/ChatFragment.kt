package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finalyearproject.R
import com.example.finalyearproject.data.remote.ai.AIService
import com.example.finalyearproject.databinding.FragmentChatBinding
import com.example.finalyearproject.databinding.FragmentAiChatBinding
import com.example.finalyearproject.ui.ai.ChatAdapter
import com.example.finalyearproject.ui.ai.ChatMessage
import com.example.finalyearproject.utils.Resource
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// ChatFragment — 2 tabs: AI Assistant | Community Chat
// ─────────────────────────────────────────────────────────────────────────────

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.vpChat.adapter = ChatPagerAdapter(this)
        b.vpChat.isUserInputEnabled = false  // disable swipe for better UX with input fields
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
            if (pos == 0) AiChatFragment() else CommunityChatFragment()
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AiChatFragment — real Gemini integration with loading + fallback
// ─────────────────────────────────────────────────────────────────────────────

class AiChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Share ViewModel with parent fragment so messages persist on tab switch
        viewModel = ViewModelProvider(requireParentFragment())[ChatViewModel::class.java]

        chatAdapter = ChatAdapter()
        b.rvChat.apply {
            adapter       = chatAdapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        }

        setupSend()
        setupChips()
        observeViewModel()
    }

    private fun setupSend() {
        fun send() {
            val text = b.etMessage.text?.toString()?.trim() ?: return
            if (text.isBlank()) return
            b.etMessage.setText("")
            hideKeyboard()
            viewModel.sendMessage(text)
        }

        b.btnSend.setOnClickListener { send() }
        b.etMessage.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }
    }

    private fun setupChips() {
        b.chipSuggest.setOnClickListener {
            viewModel.sendMessage("Suggest a delicious recipe I can make tonight")
        }
        b.chipCalories.setOnClickListener {
            viewModel.sendMessage("How many calories are in a bowl of pho?")
        }
        b.chipQuickMeal.setOnClickListener {
            viewModel.sendMessage("Give me 3 quick meal ideas under 30 minutes")
        }
        b.chipNutrition.setOnClickListener {
            viewModel.sendMessage("What are good foods for muscle building?")
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages.toList()) {
                // Scroll to bottom after DiffUtil finishes
                if (messages.isNotEmpty()) {
                    b.rvChat.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            b.cardTyping.visibility  = if (typing) View.VISIBLE else View.GONE
            b.btnSend.isEnabled      = !typing
            if (typing && chatAdapter.itemCount > 0) {
                b.rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

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


// ─────────────────────────────────────────────────────────────────────────────
// ChatViewModel — manages AI messages, loading, fallback
// ─────────────────────────────────────────────────────────────────────────────

class ChatViewModel : ViewModel() {

    private val _messages  = androidx.lifecycle.MutableLiveData<MutableList<ChatMessage>>(
        mutableListOf()
    )
    val messages: androidx.lifecycle.LiveData<MutableList<ChatMessage>> = _messages

    private val _isTyping = androidx.lifecycle.MutableLiveData(false)
    val isTyping: androidx.lifecycle.LiveData<Boolean> = _isTyping

    private val aiService = AIService.getInstance()

    /**
     * Sends a user message and fetches the AI reply.
     * Shows "Thinking…" indicator while waiting.
     * On failure, always returns a fallback — never shows raw error.
     */
    fun sendMessage(text: String) {
        val list = _messages.value ?: mutableListOf()
        list.add(ChatMessage(text = text, isFromUser = true))
        _messages.value = list

        _isTyping.value = true

        viewModelScope.launch {
            val result = aiService.askFoodQuestion(text)

            val reply = when (result) {
                is Resource.Success -> result.data
                is Resource.Error   -> buildFallback(text)  // should rarely reach here
                else                -> buildFallback(text)
            }

            _isTyping.postValue(false)

            val updated = _messages.value ?: mutableListOf()
            updated.add(ChatMessage(
                text       = reply,
                isFromUser = false,
                hasActions = reply.contains("recipe", ignoreCase = true)
            ))
            _messages.postValue(updated)
        }
    }

    private fun buildFallback(question: String): String {
        return "👨‍🍳 Great question! Here's a quick tip: start with fresh ingredients, " +
                "use proper heat control, and season at every step. " +
                "Ask me anything specific about cooking, recipes, or nutrition!"
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CommunityChatFragment — real-time Firestore group chat
// ─────────────────────────────────────────────────────────────────────────────

class CommunityChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""
    private val name get() = auth.currentUser?.displayName ?: "User"

    private lateinit var adapter: CommunityMessageAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide AI-specific chips; reuse the same layout
        b.hsvSuggestions.visibility = View.GONE
        b.cardTyping.visibility     = View.GONE
        b.etMessage.hint            = "Say something to the community…"

        adapter = CommunityMessageAdapter(uid)
        b.rvChat.apply {
            this.adapter  = adapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        }

        b.btnSend.setOnClickListener { sendCommunityMessage() }
        b.etMessage.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { sendCommunityMessage(); true } else false
        }

        listenToMessages()
    }

    private fun listenToMessages() {
        listener = db.collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val msgs = snap.documents
                    .mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        CommunityMessage(
                            id        = doc.id,
                            userId    = d["userId"] as? String ?: "",
                            userName  = d["userName"] as? String ?: "User",
                            message   = d["message"] as? String ?: "",
                            timestamp = d["timestamp"] as? Timestamp
                        )
                    }
                    .reversed()  // oldest first in list, newest at bottom
                adapter.submitList(msgs) {
                    if (msgs.isNotEmpty()) {
                        b.rvChat.smoothScrollToPosition(msgs.size - 1)
                    }
                }
            }
    }

    private fun sendCommunityMessage() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")

        db.collection("messages").add(hashMapOf(
            "userId"    to uid,
            "userName"  to name,
            "message"   to text,
            "timestamp" to Timestamp.now()
        ))
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _b = null
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Community message data + adapter
// ─────────────────────────────────────────────────────────────────────────────

data class CommunityMessage(
    val id        : String    = "",
    val userId    : String    = "",
    val userName  : String    = "",
    val message   : String    = "",
    val timestamp : Timestamp? = null
)

class CommunityMessageAdapter(private val myUid: String) :
    androidx.recyclerview.widget.ListAdapter<CommunityMessage,
            CommunityMessageAdapter.VH>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<CommunityMessage>() {
            override fun areItemsTheSame(a: CommunityMessage, b: CommunityMessage) = a.id == b.id
            override fun areContentsTheSame(a: CommunityMessage, b: CommunityMessage) = a == b
        }
    ) {

    companion object {
        private const val VIEW_MINE  = 0
        private const val VIEW_OTHER = 1
    }

    override fun getItemViewType(pos: Int) =
        if (getItem(pos).userId == myUid) VIEW_MINE else VIEW_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_MINE)
            R.layout.item_chat_user else R.layout.item_chat_ai
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v, viewType == VIEW_MINE)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(v: View, private val isMine: Boolean) :
        RecyclerView.ViewHolder(v) {

        fun bind(msg: CommunityMessage) {
            val tvMsg  = itemView.findViewById<android.widget.TextView>(R.id.tv_message)
            val tvTime = itemView.findViewById<android.widget.TextView>(R.id.tv_timestamp)

            tvMsg?.text = if (!isMine) "**${msg.userName}**: ${msg.message}" else msg.message
            tvTime?.text = msg.timestamp?.let {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    .format(it.toDate())
            } ?: ""
        }
    }
}