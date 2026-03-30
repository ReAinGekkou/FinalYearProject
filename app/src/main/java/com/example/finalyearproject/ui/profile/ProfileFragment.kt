package com.example.finalyearproject.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.example.finalyearproject.R
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.databinding.FragmentProfileBinding
import com.example.finalyearproject.databinding.FragmentRecipeListBinding
import com.example.finalyearproject.ui.MainActivity
import com.example.finalyearproject.ui.home.RecipeAdapter
import com.example.finalyearproject.utils.LanguageManager
import com.example.finalyearproject.utils.Resource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// ProfileViewModel
// ─────────────────────────────────────────────────────────────────────────────



// ─────────────────────────────────────────────────────────────────────────────
// ProfileFragment
// ─────────────────────────────────────────────────────────────────────────────

class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHeader()
        setupTabs()
        setupButtons()
    }

    private fun loadHeader() {
        val user = auth.currentUser ?: return
        b.tvName.text  = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@") ?: "User"
        b.tvEmail.text = user.email ?: ""

        // Load avatar from Google/Firebase
        if (user.photoUrl != null) {
            Glide.with(this)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(b.ivAvatar)
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                b.tvRecipeCount.text = (doc.getLong("recipeCount")    ?: 0).toString()
                b.tvFollowers.text   = (doc.getLong("followerCount")  ?: 0).toString()
                b.tvFollowing.text   = (doc.getLong("followingCount") ?: 0).toString()
            }
    }

    private fun setupTabs() {
        b.viewPager.adapter = ProfilePagerAdapter(this)
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "My Recipes" else "Favorites"
        }.attach()
    }

    private fun setupButtons() {
        // ── LOGOUT ────────────────────────────────────────────────────────────
        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    (activity as? MainActivity)?.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── SETTINGS ──────────────────────────────────────────────────────────
        b.btnSettings.setOnClickListener {
            val current = LanguageManager.getSavedLanguage(requireContext())
            val label   = if (current == "vi") "🌐 Switch to English"
            else "🌐 Switch to Vietnamese"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Settings")
                .setItems(arrayOf(label, "🔔 Notifications (coming soon)")) { _, which ->
                    if (which == 0) {
                        LanguageManager.setLanguage(
                            requireActivity(),
                            LanguageManager.toggle(current)
                        )
                    }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private inner class ProfilePagerAdapter(f: Fragment) : FragmentStateAdapter(f) {
        override fun getItemCount() = 2
        override fun createFragment(pos: Int): Fragment =
            if (pos == 0) RecipeListFragment.newMyRecipes()
            else RecipeListFragment.newFavorites()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RecipeListFragment — shared tab for My Recipes and Favorites
// ─────────────────────────────────────────────────────────────────────────────

class RecipeListFragment : Fragment() {

    private var _b: FragmentRecipeListBinding? = null
    private val b get() = _b!!

    private val viewModel: ProfileViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: RecipeAdapter

    private val isMyRecipes get() = arguments?.getBoolean(ARG_MY) == true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentRecipeListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecipeAdapter(
            onRecipeClick   = { recipe -> openDetail(recipe.recipeId) },
            onFavoriteClick = { },
            horizontal      = false
        )
        b.rvRecipes.adapter       = adapter
        b.rvRecipes.layoutManager = LinearLayoutManager(context)

        // Pull-to-refresh
        b.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        b.swipeRefresh.setOnRefreshListener { reload() }

        setupEmptyState()

        if (isMyRecipes) {
            viewModel.myRecipes.observe(viewLifecycleOwner) { render(it) }
            viewModel.loadMyRecipes()
        } else {
            viewModel.favorites.observe(viewLifecycleOwner) { render(it) }
            viewModel.loadFavorites()
        }
    }

    private fun setupEmptyState() {
        if (isMyRecipes) {
            b.tvEmptyTitle.text = "No recipes yet"
            b.tvEmptySub.text   = "Start creating your first recipe!"
            b.btnEmptyAction.text = "Create Recipe"
            b.btnEmptyAction.setOnClickListener {
                (requireActivity() as? MainActivity)?.openCreateRecipe()
            }
        } else {
            b.tvEmptyTitle.text = "No saved recipes"
            b.tvEmptySub.text   = "Tap ❤️ on any recipe to save it here"
            b.btnEmptyAction.text = "Explore Recipes"
            b.btnEmptyAction.setOnClickListener {
                (requireActivity() as? MainActivity)?.switchToHome()
            }
        }
    }

    private fun reload() {
        if (isMyRecipes) viewModel.loadMyRecipes() else viewModel.loadFavorites()
    }

    private fun render(resource: Resource<List<Recipe>>) {
        b.swipeRefresh.isRefreshing = resource is Resource.Loading

        when (resource) {
            is Resource.Loading -> {
                b.shimmer.apply { startShimmer(); visibility = View.VISIBLE }
                b.rvRecipes.visibility   = View.GONE
                b.layoutEmpty.visibility = View.GONE
            }
            is Resource.Success -> {
                b.shimmer.apply { stopShimmer(); visibility = View.GONE }
                if (resource.data.isEmpty()) {
                    // Show friendly empty state — never raw error
                    b.layoutEmpty.visibility = View.VISIBLE
                    b.rvRecipes.visibility   = View.GONE
                } else {
                    b.layoutEmpty.visibility = View.GONE
                    b.rvRecipes.visibility   = View.VISIBLE
                    adapter.submitList(resource.data)
                }
            }
            is Resource.Error -> {
                // Show empty state with retry — don't expose the error message
                b.shimmer.apply { stopShimmer(); visibility = View.GONE }
                b.layoutEmpty.visibility = View.VISIBLE
                b.rvRecipes.visibility   = View.GONE
                b.tvEmptySub.text = "Couldn't load. Pull down to retry."
            }
        }
    }

    private fun openDetail(recipeId: String) {
        val intent = android.content.Intent(requireContext(),
            com.example.finalyearproject.ui.recipe.RecipeDetailActivity::class.java)
        intent.putExtra("recipeId", recipeId)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_MY = "isMyRecipes"
        fun newMyRecipes() = RecipeListFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_MY, true) }
        }
        fun newFavorites() = RecipeListFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_MY, false) }
        }
    }
}