package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalyearproject.databinding.FragmentAiChatBinding
import com.example.finalyearproject.databinding.ItemChatAiBinding
import com.example.finalyearproject.databinding.ItemChatUserBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

// ── Data model ────────────────────────────────────────────────────────────────

data class CommunityMessage(
    val id        : String     = "",
    val userId    : String     = "",
    val userName  : String     = "",
    val message   : String     = "",
    val timestamp : Timestamp? = null
)

// ── Fragment ──────────────────────────────────────────────────────────────────

/**
 * CommunityChatFragment
 *
 * FIXES applied:
 *
 * 1. Firestore listener uses ASCENDING order + limitToLast(100).
 *    Combined with stackFromEnd=true on RecyclerView this means the
 *    newest message is always at the bottom — no .reversed() needed.
 *
 * 2. submitList() receives a NEW list object (toList()) every call so
 *    DiffUtil always detects the change and redraws.
 *
 * 3. Listener registered in onStart / removed in onStop so it is
 *    always active while the Community tab is visible.
 *
 * 4. Uses its own CommunityAdapter — avoids ViewType conflicts with
 *    ChatAdapter (AI messages).
 *
 * 5. AI-only views (layoutEmpty, layoutSuggestions / chipRow, cardTyping)
 *    are hidden immediately in onViewCreated.
 */
class CommunityChatFragment : Fragment() {

    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val myUid  get() = auth.currentUser?.uid ?: ""
    private val myName get() = auth.currentUser?.displayName
        ?.takeIf { it.isNotBlank() } ?: "User"

    private lateinit var communityAdapter: CommunityAdapter
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentAiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideAiOnlyViews()
        setupRecyclerView()
        setupInput()
    }

    // Hide views that belong to the AI tab only
    private fun hideAiOnlyViews() {
        b.layoutEmpty.visibility       = View.GONE
        b.layoutSuggestions.visibility = View.GONE
        b.cardTyping.visibility        = View.GONE
        b.tvToolbarTitle.text          = "Community Chat"
        b.tvStatus.text                = "Group chat"
        b.tvStatus.setTextColor(
            requireContext().getColor(android.R.color.darker_gray)
        )
        b.etMessage.hint = "Say something to the community…"
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        communityAdapter = CommunityAdapter(myUid)
        b.rvChat.apply {
            adapter       = communityAdapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            itemAnimator  = null
            visibility    = View.VISIBLE
        }
    }

    // ── Firestore listener lifecycle ──────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        startListening()
    }

    override fun onStop() {
        super.onStop()
        stopListening()
    }

    private fun startListening() {
        firestoreListener = db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CommunityChat", "Listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val msgs: List<CommunityMessage> = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CommunityMessage(
                        id        = doc.id,
                        userId    = d["userId"]    as? String ?: "",
                        userName  = d["userName"]  as? String ?: "User",
                        message   = d["message"]   as? String ?: "",
                        timestamp = d["timestamp"] as? Timestamp
                    )
                }

                // toList() creates a NEW list — DiffUtil will see the change
                communityAdapter.submitList(msgs.toList()) {
                    // Scroll to bottom after the list is drawn
                    b.rvChat.post {
                        if (communityAdapter.itemCount > 0)
                            b.rvChat.scrollToPosition(communityAdapter.itemCount - 1)
                    }
                }
            }
    }

    private fun stopListening() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private fun setupInput() {
        b.btnSend.setOnClickListener { sendMessage() }
        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun sendMessage() {
        val text = b.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        b.etMessage.setText("")
        hideKeyboard()

        // Write to Firestore — the listener above picks it up automatically
        db.collection("messages").add(
            hashMapOf(
                "userId"    to myUid,
                "userName"  to myName,
                "message"   to text,
                "timestamp" to Timestamp.now()
            )
        ).addOnFailureListener { e ->
            android.util.Log.e("CommunityChat", "Send failed: ${e.message}")
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(b.etMessage.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class CommunityAdapter(private val myUid: String) :
    ListAdapter<CommunityMessage, RecyclerView.ViewHolder>(Diff()) {

    private val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    companion object {
        private const val TYPE_ME    = 0
        private const val TYPE_OTHER = 1
    }

    override fun getItemViewType(pos: Int) =
        if (getItem(pos).userId == myUid) TYPE_ME else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (type == TYPE_ME)
            MeVH(ItemChatUserBinding.inflate(inf, parent, false))
        else
            OtherVH(ItemChatAiBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        when (h) {
            is MeVH    -> h.bind(getItem(pos))
            is OtherVH -> h.bind(getItem(pos))
        }
    }

    inner class MeVH(private val b: ItemChatUserBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(m: CommunityMessage) {
            b.tvMessage.text   = m.message
            b.tvTimestamp.text = m.timestamp?.toDate()?.let { fmt.format(it) } ?: ""
        }
    }

    inner class OtherVH(private val b: ItemChatAiBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(m: CommunityMessage) {
            b.tvSenderLabel.text = m.userName
            b.tvMessage.text     = m.message
            b.tvTimestamp.text   = m.timestamp?.toDate()?.let { fmt.format(it) } ?: ""
            // Reset to default grey (not error amber)
            b.cardBubble.setCardBackgroundColor(
                android.graphics.Color.parseColor("#F1F3F4")
            )
            b.tvMessage.setTextColor(android.graphics.Color.parseColor("#212121"))
        }
    }

    class Diff : DiffUtil.ItemCallback<CommunityMessage>() {
        override fun areItemsTheSame(a: CommunityMessage, b: CommunityMessage) = a.id == b.id
        override fun areContentsTheSame(a: CommunityMessage, b: CommunityMessage) = a == b
    }
}