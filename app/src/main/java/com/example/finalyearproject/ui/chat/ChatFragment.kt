package com.example.finalyearproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.finalyearproject.R

/**
 * ChatFragment
 * Real-time group chat via Firestore.
 * Placeholder — replace with chat RecyclerView in next sprint.
 */
class ChatFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_placeholder, container, false).also { root ->
        root.findViewById<TextView>(R.id.tv_placeholder_title)?.text = "Chat 💬"
        root.findViewById<TextView>(R.id.tv_placeholder_sub)?.text =
            "Real-time group chat\ncoming in the next update."
    }
}