package com.example.finalyearproject.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.FragmentHomeBinding
import com.example.finalyearproject.utils.FirestoreSeeder
import com.example.finalyearproject.utils.Resource
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var aiAdapter: RecipeAdapter
    private lateinit var trendingAdapter: RecipeAdapter
    private lateinit var allRecipesAdapter: RecipeAdapter

    private val categories = listOf(
        "All", "Soup", "Rice", "Noodle",
        "Sandwich", "Stir Fry", "Dessert", "Salad"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Seed sample data if Firestore is empty
        FirestoreSeeder.seedIfEmpty()

        setupGreeting()
        setupAdapters()
        setupCategoryChips()
        setupSearch()
        observeViewModel()

        binding.btnRetry.setOnClickListener { viewModel.loadAll() }
    }

    // ── Greeting ──────────────────────────────────────────────────────────────

    private fun setupGreeting() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Chef"
        binding.tvUserName.text = name
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        aiAdapter = RecipeAdapter(
            onRecipeClick   = { recipe -> viewModel.onRecipeVisible(recipe.recipeId) },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = true
        )
        trendingAdapter = RecipeAdapter(
            onRecipeClick   = { recipe -> viewModel.onRecipeVisible(recipe.recipeId) },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = true
        )
        allRecipesAdapter = RecipeAdapter(
            onRecipeClick = { recipe ->
                viewModel.onRecipeVisible(recipe.recipeId)
            },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = false
        )

        binding.rvRecommended.apply {
            adapter = aiAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvTrending.apply {
            adapter = trendingAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvRecipes.apply {
            adapter = allRecipesAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    // ── Category chips ────────────────────────────────────────────────────────

    private fun setupCategoryChips() {
        binding.chipGroupCategories.removeAllViews()
        categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text        = cat
                isCheckable = true
                isChecked   = cat == "All"
                chipCornerRadius = resources.getDimension(R.dimen.spacing_xl)
                chipStrokeWidth  = 1.5f
                setTextColor(requireContext().getColorStateList(R.color.selector_chip_text))
                chipBackgroundColor = requireContext().getColorStateList(R.color.selector_chip_bg)
                setChipStrokeColorResource(R.color.brand_primary)
                textSize = 13f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    viewModel.filterByCategory(if (cat == "All") null else cat)
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                val isSearching = q.isNotEmpty()
                binding.layoutAiSection.visibility      = if (isSearching) View.GONE else View.VISIBLE
                binding.layoutTrendingSection.visibility = if (isSearching) View.GONE else View.VISIBLE
                viewModel.search(q)
            }
        })
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {

        viewModel.likedIds.observe(viewLifecycleOwner) { ids ->
            aiAdapter.setLikedIds(ids)
            trendingAdapter.setLikedIds(ids)
            allRecipesAdapter.setLikedIds(ids)
        }

        viewModel.recommendations.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.shimmerAi.apply { startShimmer(); visibility = View.VISIBLE }
                    binding.rvRecommended.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.shimmerAi.apply { stopShimmer(); visibility = View.GONE }
                    if (resource.data.isEmpty()) {
                        binding.layoutAiSection.visibility = View.GONE
                    } else {
                        binding.layoutAiSection.visibility = View.VISIBLE
                        binding.rvRecommended.visibility   = View.VISIBLE
                        aiAdapter.submitList(resource.data)
                    }
                }
                is Resource.Error -> {
                    binding.shimmerAi.apply { stopShimmer(); visibility = View.GONE }
                    binding.layoutAiSection.visibility = View.GONE
                }
            }
        }

        viewModel.trending.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.shimmerTrending.apply { startShimmer(); visibility = View.VISIBLE }
                    binding.rvTrending.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.shimmerTrending.apply { stopShimmer(); visibility = View.GONE }
                    if (resource.data.isEmpty()) {
                        binding.layoutTrendingSection.visibility = View.GONE
                    } else {
                        binding.layoutTrendingSection.visibility = View.VISIBLE
                        binding.rvTrending.visibility            = View.VISIBLE
                        trendingAdapter.submitList(resource.data)
                    }
                }
                is Resource.Error -> {
                    binding.shimmerTrending.apply { stopShimmer(); visibility = View.GONE }
                }
            }
        }

        viewModel.recipes.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.shimmerRecipes.apply { startShimmer(); visibility = View.VISIBLE }
                    binding.rvRecipes.visibility   = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.shimmerRecipes.apply { stopShimmer(); visibility = View.GONE }
                    val count = resource.data.size
                    binding.tvRecipeCount.text = if (count > 0) "$count recipes" else ""
                    if (resource.data.isEmpty()) {
                        binding.rvRecipes.visibility   = View.GONE
                        binding.layoutEmpty.visibility = View.VISIBLE
                    } else {
                        binding.rvRecipes.visibility   = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        allRecipesAdapter.submitList(resource.data)
                    }
                }
                is Resource.Error -> {
                    binding.shimmerRecipes.apply { stopShimmer(); visibility = View.GONE }
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}