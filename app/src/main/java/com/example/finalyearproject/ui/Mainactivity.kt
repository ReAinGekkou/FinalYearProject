package com.example.finalyearproject.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityMainBinding
import com.example.finalyearproject.ui.auth.LoginActivity
import com.example.finalyearproject.ui.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    // Fragment instances — create once, hide/show for performance
    private val homeFragment     = HomeFragment()
    private val communityFragment by lazy { createPlaceholder("Community — Blog") }
    private val chatFragment     by lazy { createPlaceholder("Chat") }
    private val profileFragment  by lazy { createPlaceholder("Profile") }

    private var activeTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect to login if not authenticated
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments(savedInstanceState)
        setupNavigation()
    }

    // ── Fragment management ───────────────────────────────────────────────────

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return  // fragments already restored

        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, homeFragment, TAG_HOME)
            commit()
        }
    }

    private fun switchTab(tab: Int) {
        if (tab == activeTab) return
        activeTab = tab

        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        // Hide all, show selected
        listOf(homeFragment).forEach { f ->
            if (f.isAdded) transaction.hide(f)
        }

        val target = when (tab) {
            TAB_HOME      -> homeFragment
            TAB_COMMUNITY -> communityFragment.also {
                if (!it.isAdded) transaction.add(R.id.fragment_container, it, TAG_COMMUNITY)
            }
            TAB_CHAT      -> chatFragment.also {
                if (!it.isAdded) transaction.add(R.id.fragment_container, it, TAG_CHAT)
            }
            TAB_PROFILE   -> profileFragment.also {
                if (!it.isAdded) transaction.add(R.id.fragment_container, it, TAG_PROFILE)
            }
            else -> homeFragment
        }

        transaction.show(target).commit()
        updateNavIcons(tab)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupNavigation() {
        binding.navHome.setOnClickListener      { switchTab(TAB_HOME) }
        binding.navCommunity.setOnClickListener { switchTab(TAB_COMMUNITY) }
        binding.navChat.setOnClickListener      { switchTab(TAB_CHAT) }
        binding.navProfile.setOnClickListener   { switchTab(TAB_PROFILE) }

        binding.fabCreateBtn.setOnClickListener { showCreateBottomSheet() }

        updateNavIcons(TAB_HOME)
    }

    private fun updateNavIcons(selectedTab: Int) {
        val primary  = ContextCompat.getColor(this, R.color.brand_primary)
        val inactive = ContextCompat.getColor(this, R.color.text_hint)

        fun setTab(iconView: View, textView: View, active: Boolean) {
            val color = if (active) primary else inactive
            (iconView as? android.widget.ImageView)?.setColorFilter(color)
            (textView as? android.widget.TextView)?.setTextColor(color)
        }

        setTab(binding.ivHome,      binding.tvNavHome,      selectedTab == TAB_HOME)
        setTab(binding.ivCommunity, binding.tvNavCommunity, selectedTab == TAB_COMMUNITY)
        setTab(binding.ivChat,      binding.tvNavChat,      selectedTab == TAB_CHAT)
        setTab(binding.ivProfile,   binding.tvNavProfile,   selectedTab == TAB_PROFILE)
    }

    private fun showCreateBottomSheet() {
        // Replace with BottomSheetDialogFragment when ready
        // For now show a toast
        android.widget.Toast.makeText(
            this,
            "Create: Upload Recipe / Blog / Video",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createPlaceholder(label: String) =
        PlaceholderFragment.newInstance(label)

    companion object {
        private const val TAB_HOME      = 0
        private const val TAB_COMMUNITY = 1
        private const val TAB_CHAT      = 2
        private const val TAB_PROFILE   = 3

        private const val TAG_HOME      = "home"
        private const val TAG_COMMUNITY = "community"
        private const val TAG_CHAT      = "chat"
        private const val TAG_PROFILE   = "profile"
    }
}

// ── Temporary placeholder fragment ────────────────────────────────────────────

class PlaceholderFragment : androidx.fragment.app.Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val label = arguments?.getString("label") ?: "Screen"
        return android.widget.TextView(requireContext()).apply {
            text      = "$label\n(Coming soon)"
            textSize  = 18f
            gravity   = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#616161"))
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    companion object {
        fun newInstance(label: String) = PlaceholderFragment().apply {
            arguments = android.os.Bundle().apply { putString("label", label) }
        }
    }
}