package com.example.finalyearproject.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityMainBinding
import com.example.finalyearproject.ui.auth.LoginActivity
import com.example.finalyearproject.ui.blog.CommunityFragment
import com.example.finalyearproject.ui.chat.ChatFragment
import com.example.finalyearproject.ui.create.CreateRecipeActivity
import com.example.finalyearproject.ui.home.HomeFragment
import com.example.finalyearproject.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

/**
 * MainActivity — single-activity host.
 *
 * ✅ Correct navigation pattern:
 *   Login → MainActivity (via Intent, flags = NEW_TASK | CLEAR_TASK)
 *   All screens → showFragment() — NEVER Intent to a Fragment
 *   Only Activities opened via Intent: CreateRecipeActivity, RecipeDetailActivity
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    private val homeFragment      by lazy { HomeFragment() }
    private val communityFragment by lazy { CommunityFragment() }
    private val chatFragment      by lazy { ChatFragment() }
    private val profileFragment   by lazy { ProfileFragment() }

    private var currentTag = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            goToLogin(); return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) showFragment(TAG_HOME)
        else currentTag = savedInstanceState.getString(KEY_TAB, TAG_HOME)

        setupBottomNav()
        setupFab()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString(KEY_TAB, currentTag)
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_home      -> { showFragment(TAG_HOME);      true }
                R.id.nav_community -> { showFragment(TAG_COMMUNITY); true }
                R.id.nav_chat      -> { showFragment(TAG_CHAT);      true }
                R.id.nav_profile   -> { showFragment(TAG_PROFILE);   true }
                R.id.nav_create    -> false  // FAB handles this
                else -> false
            }
        }
        binding.bottomNav.menu.findItem(R.id.nav_create)?.isEnabled = false
    }

    private fun setupFab() {
        binding.fabCreate.setOnClickListener {
            binding.fabCreate.animate().rotationBy(45f).setDuration(200).start()
            CreateOptionsBottomSheet().show(supportFragmentManager, "create")
        }
    }

    // ── Fragment switching ────────────────────────────────────────────────────

    private fun showFragment(tag: String) {
        if (currentTag == tag) return
        currentTag = tag

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

        if (!target.isAdded) tx.add(R.id.fragment_container, target, tag)

        listOf(TAG_HOME, TAG_COMMUNITY, TAG_CHAT, TAG_PROFILE)
            .filter { it != tag }
            .mapNotNull { fm.findFragmentByTag(it) }
            .forEach { tx.hide(it) }

        tx.show(target).commit()

        // Sync nav item — avoid triggering listener again
        val navId = when (tag) {
            TAG_HOME      -> R.id.nav_home
            TAG_COMMUNITY -> R.id.nav_community
            TAG_CHAT      -> R.id.nav_chat
            TAG_PROFILE   -> R.id.nav_profile
            else          -> R.id.nav_home
        }
        if (binding.bottomNav.selectedItemId != navId) {
            binding.bottomNav.selectedItemId = navId
        }
    }

    // ── Public helpers (called from child fragments) ──────────────────────────

    /** Open the Create Recipe screen (Activity, not Fragment) */
    fun openCreateRecipe() {
        startActivity(Intent(this, CreateRecipeActivity::class.java))
    }

    /** Navigate the bottom nav to the Home tab programmatically */
    fun switchToHome() {
        showFragment(TAG_HOME)
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    /** Called from ProfileFragment logout button */
    fun logout() {
        auth.signOut()
        goToLogin()
    }

    companion object {
        const val TAG_HOME      = "home"
        const val TAG_COMMUNITY = "community"
        const val TAG_CHAT      = "chat"
        const val TAG_PROFILE   = "profile"
        private const val KEY_TAB = "tab"
    }
}