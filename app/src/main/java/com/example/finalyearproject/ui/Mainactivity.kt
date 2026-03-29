package com.example.finalyearproject.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityMainBinding
import com.example.finalyearproject.ui.auth.LoginActivity
import com.example.finalyearproject.ui.blog.CommunityFragment
import com.example.finalyearproject.ui.chat.ChatFragment
import com.example.finalyearproject.ui.home.HomeFragment
import com.example.finalyearproject.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    // Keep fragment instances alive — avoid recreating on every tab switch
    private val homeFragment      by lazy { HomeFragment() }
    private val communityFragment by lazy { CommunityFragment() }
    private val chatFragment      by lazy { ChatFragment() }
    private val profileFragment   by lazy { ProfileFragment() }

    private var currentFragmentTag = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect unauthenticated users to login
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show home on first launch
        if (savedInstanceState == null) {
            showFragment(TAG_HOME)
        } else {
            currentFragmentTag = savedInstanceState.getString(KEY_CURRENT_TAB, TAG_HOME)
        }

        setupBottomNav()
        setupFab()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_TAB, currentFragmentTag)
    }

    // ── Bottom navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_home      -> { showFragment(TAG_HOME);      true }
                R.id.nav_community -> { showFragment(TAG_COMMUNITY); true }
                R.id.nav_chat      -> { showFragment(TAG_CHAT);      true }
                R.id.nav_profile   -> { showFragment(TAG_PROFILE);   true }
                R.id.nav_create    -> false  // handled by FAB — do nothing here
                else               -> false
            }
        }

        // Disable the center "Create" placeholder item visually
        binding.bottomNav.menu.findItem(R.id.nav_create)?.isEnabled = false
    }

    private fun setupFab() {
        binding.fabCreate.setOnClickListener {
            showCreateBottomSheet()
        }
    }

    // ── Fragment switching ────────────────────────────────────────────────────

    /**
     * Hides all fragments and shows the requested one.
     * Uses add-once + hide/show pattern for performance:
     * fragments are not recreated on tab switches.
     */
    private fun showFragment(tag: String) {
        if (currentFragmentTag == tag) return
        currentFragmentTag = tag

        val target = when (tag) {
            TAG_HOME      -> homeFragment
            TAG_COMMUNITY -> communityFragment
            TAG_CHAT      -> chatFragment
            TAG_PROFILE   -> profileFragment
            else          -> homeFragment
        }

        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        // Add any fragment that hasn't been added yet
        if (!target.isAdded) {
            tx.add(R.id.fragment_container, target, tag)
        }

        // Hide all other known fragments
        listOf(TAG_HOME, TAG_COMMUNITY, TAG_CHAT, TAG_PROFILE)
            .filter { it != tag }
            .mapNotNull { fm.findFragmentByTag(it) }
            .forEach { tx.hide(it) }

        tx.show(target).commit()

        // Sync bottom nav selection (in case showFragment is called programmatically)
        val navItemId = when (tag) {
            TAG_HOME      -> R.id.nav_home
            TAG_COMMUNITY -> R.id.nav_community
            TAG_CHAT      -> R.id.nav_chat
            TAG_PROFILE   -> R.id.nav_profile
            else          -> R.id.nav_home
        }
        if (binding.bottomNav.selectedItemId != navItemId) {
            binding.bottomNav.selectedItemId = navItemId
        }
    }

    // ── Create post ───────────────────────────────────────────────────────────

    private fun showCreateBottomSheet() {
        CreateOptionsBottomSheet().show(supportFragmentManager, "create")
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    /** Called from ProfileFragment / SettingsFragment */
    fun logout() {
        auth.signOut()
        goToLogin()
    }

    companion object {
        const val TAG_HOME      = "home"
        const val TAG_COMMUNITY = "community"
        const val TAG_CHAT      = "chat"
        const val TAG_PROFILE   = "profile"
        private const val KEY_CURRENT_TAB = "current_tab"
    }
}