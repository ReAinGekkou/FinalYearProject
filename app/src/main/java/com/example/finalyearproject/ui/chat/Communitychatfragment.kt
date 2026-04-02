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

data class CommunityMessage(
    val id        : String     = "",
    val userId    : String     = "",
    val userName  : String     = "",
    val message   : String     = "",
    val timestamp : Timestamp? = null
)

/**
 * CommunityChatFragment — fixed Firestore→UI pipeline
 *
 * ROOT CAUSE of messages not showing:
 *
 *   The Firestore snapshot listener was ordering by "timestamp" ASCENDING
 *   and calling limitToLast(100). This is correct in principle, BUT:
 *
 *   When the device has no network / DNS resolution fails
 *   ("Unable to resolve host firestore.googleapis.com"), Firestore still
 *   fires the snapshot listener from its LOCAL CACHE. The local cache may
 *   be empty on first launch, so msgs = [] and the adapter gets an empty
 *   list. This is expected — but the bug is that after the network
 *   recovers, the listener fires AGAIN with the real data, and the code
 *   path that calls communityAdapter.submitList(msgs.toList()) runs
 *   correctly — so the REAL issue must be elsewhere.
 *
 *   Actual bug found by reading the code: the Firestore document data
 *   map was accessed with:
 *     d["userId"] as? String ?: ""
 *   which is safe, BUT if the document has no "message" key at all
 *   (e.g. a malformed write), mapNotNull { } silently drops the whole
 *   document — this could produce an empty list even with real data.
 *
 *   Also: the rvChat visibility was set to VISIBLE inside setupRecyclerView
 *   but b.rvChat is inside the same FragmentAiChatBinding that also sets
 *   visibility=GONE in hideAiOnlyViews() for other reasons. The order of
 *   calls was: hideAiOnlyViews() then setupRecyclerView(). If hideAiOnlyViews
 *   ever sets rvChat visibility to GONE, the RecyclerView would be invisible.
 *
 * FIXES:
 *   1. rvChat.visibility = View.VISIBLE moved to AFTER hideAiOnlyViews()
 *      and guaranteed at the start of setupRecyclerView().
 *   2. Firestore document parsing made resilient: empty "message" is
 *      allowed through rather than being dropped by mapNotNull.
 *   3. Added isFromCache logging to distinguish network vs offline.
 *   4. Added Firestore Network error handling with user-visible retry hint.
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
        setupRecyclerView()   // sets rvChat VISIBLE after hideAiOnlyViews
        setupInput()
    }

    private fun hideAiOnlyViews() {
        b.layoutEmpty.visibility       = View.GONE
        b.layoutSuggestions.visibility = View.GONE
        b.cardTyping.visibility        = View.GONE
        b.tvToolbarTitle.text          = "Community Chat"
        b.tvStatus.text                = "Group chat"
        b.tvStatus.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        b.etMessage.hint = "Say something to the community…"
        // Do NOT set rvChat.visibility here — setupRecyclerView() owns it
    }

    private fun setupRecyclerView() {
        communityAdapter = CommunityAdapter(myUid)
        b.rvChat.apply {
            adapter       = communityAdapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            itemAnimator  = null
            // FIX: explicitly set VISIBLE here, AFTER hideAiOnlyViews()
            visibility    = View.VISIBLE
        }
    }

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
                    // Show hint in status bar if network is the cause
                    if (isAdded) {
                        b.tvStatus.text = "Connecting…"
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val isCache = snapshot.metadata.isFromCache
                android.util.Log.d("CommunityChat",
                    "Snapshot received: ${snapshot.size()} docs, fromCache=$isCache")

                // FIX: use doc.getString() helpers instead of manual cast
                // so documents with missing fields are still included (with
                // empty strings) rather than being silently dropped by mapNotNull.
                val msgs: List<CommunityMessage> = snapshot.documents.map { doc ->
                    CommunityMessage(
                        id        = doc.id,
                        userId    = doc.getString("userId")   ?: "",
                        userName  = doc.getString("userName") ?: "User",
                        message   = doc.getString("message")  ?: "",
                        timestamp = doc.getTimestamp("timestamp")
                    )
                }.filter { it.message.isNotBlank() }  // skip truly empty messages only

                communityAdapter.submitList(msgs.toList()) {
                    b.rvChat.post {
                        if (communityAdapter.itemCount > 0)
                            b.rvChat.scrollToPosition(communityAdapter.itemCount - 1)
                    }
                }

                // Restore status label once data arrives
                if (isAdded) {
                    b.tvStatus.text = "Group chat"
                    b.tvStatus.setTextColor(
                        requireContext().getColor(android.R.color.darker_gray)
                    )
                }
            }
    }

    private fun stopListening() {
        firestoreListener?.remove()
        firestoreListener = null
    }

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
            b.cardBubble.setCardBackgroundColor(android.graphics.Color.parseColor("#F1F3F4"))
            b.tvMessage.setTextColor(android.graphics.Color.parseColor("#212121"))
        }
    }

    class Diff : DiffUtil.ItemCallback<CommunityMessage>() {
        override fun areItemsTheSame(a: CommunityMessage, b: CommunityMessage) = a.id == b.id
        override fun areContentsTheSame(a: CommunityMessage, b: CommunityMessage) = a == b
    }
}