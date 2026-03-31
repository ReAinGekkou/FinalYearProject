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
import com.example.finalyearproject.ui.chat.AiChatFragment


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
        b.layoutSuggestions.visibility = View.GONE
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