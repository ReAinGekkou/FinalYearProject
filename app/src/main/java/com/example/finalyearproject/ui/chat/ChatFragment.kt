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

