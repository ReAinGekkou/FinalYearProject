package com.example.finalyearproject.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.FragmentProfileBinding
import com.example.finalyearproject.databinding.FragmentRecipeListBinding
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.ui.MainActivity
import com.example.finalyearproject.ui.home.RecipeAdapter
import com.example.finalyearproject.utils.Resource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHeader()
        setupTabs()
        setupButtons()
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun loadHeader() {
        val user = auth.currentUser ?: return
        binding.tvName.text  = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@") ?: "User"
        binding.tvEmail.text = user.email ?: ""

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                binding.tvRecipeCount.text  = (doc.getLong("recipeCount")   ?: 0).toString()
                binding.tvFollowers.text    = (doc.getLong("followerCount") ?: 0).toString()
                binding.tvFollowing.text    = (doc.getLong("followingCount") ?: 0).toString()
            }
    }

    // ── Tabs (My Recipes | Favorites) ─────────────────────────────────────────

    private fun setupTabs() {
        binding.viewPager.adapter = ProfilePagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "My Recipes" else "Favorites"
        }.attach()
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    (activity as? MainActivity)?.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSettings.setOnClickListener {
            // Language toggle dialog
            val current = com.example.finalyearproject.utils.LanguageManager
                .getSavedLanguage(requireContext())
            val isVi = current == "vi"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Settings")
                .setItems(arrayOf(if (isVi) "🌐 Switch to English" else "🌐 Switch to Vietnamese")) { _, _ ->
                    com.example.finalyearproject.utils.LanguageManager.setLanguage(
                        requireActivity(),
                        com.example.finalyearproject.utils.LanguageManager.toggle(current)
                    )
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── ViewPager2 adapter ────────────────────────────────────────────────────

    private inner class ProfilePagerAdapter(f: Fragment) : FragmentStateAdapter(f) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment =
            if (position == 0) RecipeListFragment.newMyRecipes()
            else RecipeListFragment.newFavorites()
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// RecipeListFragment — shared fragment for both tabs
// ─────────────────────────────────────────────────────────────────────────────

class RecipeListFragment : Fragment() {

    private var _b: FragmentRecipeListBinding? = null
    private val b get() = _b!!

    private val viewModel: ProfileViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: RecipeAdapter

    private val isMyRecipes get() = arguments?.getBoolean(ARG_MY) == true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentRecipeListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecipeAdapter(
            onRecipeClick   = { r -> openDetail(r) },
            onFavoriteClick = { },
            horizontal      = false
        )
        b.rvRecipes.adapter = adapter
        b.rvRecipes.layoutManager = LinearLayoutManager(context)

        if (isMyRecipes) {
            b.tvEmptySub.text  = "Start sharing your cooking!"
            b.btnEmptyAction.text = "Create Recipe"
            b.btnEmptyAction.setOnClickListener {
                (requireActivity() as? MainActivity)?.openCreateRecipe()
            }
            viewModel.myRecipes.observe(viewLifecycleOwner) { render(it) }
            viewModel.loadMyRecipes()
        } else {
            b.tvEmptyTitle.text = "No saved recipes"
            b.tvEmptySub.text   = "Save recipes to find them here"
            b.btnEmptyAction.text = "Explore Recipes"
            b.btnEmptyAction.setOnClickListener {
                (requireActivity() as? MainActivity)?.switchToHome()
            }
            viewModel.favorites.observe(viewLifecycleOwner) { render(it) }
            viewModel.loadFavorites()
        }
    }

    private fun render(resource: Resource<List<Recipe>>) {
        when (resource) {
            is Resource.Loading -> {
                b.shimmer.apply { startShimmer(); visibility = View.VISIBLE }
                b.rvRecipes.visibility   = View.GONE
                b.layoutEmpty.visibility = View.GONE
            }
            is Resource.Success -> {
                b.shimmer.apply { stopShimmer(); visibility = View.GONE }
                if (resource.data.isEmpty()) {
                    b.layoutEmpty.visibility = View.VISIBLE
                    b.rvRecipes.visibility   = View.GONE
                } else {
                    b.layoutEmpty.visibility = View.GONE
                    b.rvRecipes.visibility   = View.VISIBLE
                    adapter.submitList(resource.data)
                }
            }
            is Resource.Error -> {
                b.shimmer.apply { stopShimmer(); visibility = View.GONE }
                b.layoutEmpty.visibility = View.VISIBLE
                b.tvEmptySub.text = resource.message
            }
        }
    }

    private fun openDetail(recipe: Recipe) {
        val intent = android.content.Intent(requireContext(),
            com.example.finalyearproject.ui.recipe.RecipeDetailActivity::class.java)
        intent.putExtra("recipeId", recipe.recipeId)
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