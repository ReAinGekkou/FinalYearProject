package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.FragmentChatBinding
import com.example.finalyearproject.databinding.FragmentCommunityChatBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale


class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.vpChat.adapter = ChatPagerAdapter(this)
        b.vpChat.offscreenPageLimit = 1
        b.vpChat.isUserInputEnabled = false
        TabLayoutMediator(b.tabChat, b.vpChat) { tab, pos ->
            tab.text = if (pos == 0) "🤖 AI Assistant" else "💬 Community"
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private inner class ChatPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {
        override fun getItemCount() = 2
        override fun createFragment(pos: Int): Fragment =
            if (pos == 0) AiChatFragment() else CommunityChatFragment()
    }
}

class CommunityChatFragment : Fragment() {

    private var _b: FragmentCommunityChatBinding? = null
    private val b get() = _b!!

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""
    private val name get() = auth.currentUser?.displayName ?: "User"

    private lateinit var adapter: CommunityMessageAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentCommunityChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CommunityMessageAdapter(uid)

        b.rvCommunity.apply {
            this.adapter  = adapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            itemAnimator = null
        }

        b.etMessage.hint = "Say something to the community…"
        b.btnSend.setOnClickListener { sendCommunityMessage() }
        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCommunityMessage(); true } else false
        }

        listenToMessages()
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _b = null
    }

    private fun listenToMessages() {
        listener = db.collection("communityMessages")
            // ✅ FIX BUG 1: ASCENDING + limitToLast thay vì DESCENDING + limit + reversed()
            // Lý do: DESCENDING + reversed() bị sai thứ tự khi tin nhắn mới có
            // server timestamp = null tạm thời (chưa confirmed) → DiffUtil
            // nghĩ list không đổi → RecyclerView không update
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener

                val msgs = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CommunityMessage(
                        id        = doc.id,
                        userId    = d["userId"]    as? String    ?: "",
                        userName  = d["userName"]  as? String    ?: "User",
                        message   = d["message"]   as? String    ?: "",
                        timestamp = d["timestamp"] as? Timestamp
                    )
                }

                // ✅ FIX BUG 2: ArrayList(msgs) tạo object reference MỚI mỗi lần
                // Lý do: ListAdapter so sánh reference trước khi chạy DiffUtil
                // Nếu cùng object reference → bỏ qua hoàn toàn → UI không update
                adapter.submitList(ArrayList(msgs)) {
                    // post() đảm bảo scroll sau khi RecyclerView đã layout xong
                    if (msgs.isNotEmpty()) {
                        b.rvCommunity.post {
                            b.rvCommunity.smoothScrollToPosition(msgs.size - 1)
                        }
                    }
                }
            }
    }

    private fun sendCommunityMessage() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")

        db.collection("communityMessages").add(hashMapOf(
            "userId"    to uid,
            "userName"  to name,
            "message"   to text,
            "timestamp" to Timestamp.now()
        ))
    }
}

data class CommunityMessage(
    val id        : String     = "",
    val userId    : String     = "",
    val userName  : String     = "",
    val message   : String     = "",
    val timestamp : Timestamp? = null
)

class CommunityMessageAdapter(private val myUid: String) :
    ListAdapter<CommunityMessage, CommunityMessageAdapter.VH>(DiffCb()) {

    companion object {
        private const val VIEW_MINE  = 0
        private const val VIEW_OTHER = 1
    }

    override fun getItemViewType(pos: Int) =
        if (getItem(pos).userId == myUid) VIEW_MINE else VIEW_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_MINE) R.layout.item_chat_user
        else                       R.layout.item_chat_ai
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMsg  = v.findViewById<android.widget.TextView>(R.id.tv_message)
        private val tvTime = v.findViewById<android.widget.TextView>(R.id.tv_timestamp)

        fun bind(msg: CommunityMessage) {
            val isMine   = msg.userId == myUid
            tvMsg?.text  = if (isMine) msg.message else "${msg.userName}: ${msg.message}"
            tvTime?.text = msg.timestamp?.toDate()?.let {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
            } ?: ""
        }
    }

    private class DiffCb : DiffUtil.ItemCallback<CommunityMessage>() {
        override fun areItemsTheSame(a: CommunityMessage, b: CommunityMessage) = a.id == b.id
        override fun areContentsTheSame(a: CommunityMessage, b: CommunityMessage) = a == b
    }
}
