package com.example.finalyearproject.ui.blog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.finalyearproject.R

/**
 * CommunityFragment
 * Blog / community feed.
 * Placeholder — replace with full RecyclerView blog feed in next sprint.
 */
class CommunityFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_placeholder, container, false).also { root ->
        root.findViewById<TextView>(R.id.tv_placeholder_title)?.text = "Community 📝"
        root.findViewById<TextView>(R.id.tv_placeholder_sub)?.text =
            "Blog posts and community recipes\ncoming in the next update."
    }
}