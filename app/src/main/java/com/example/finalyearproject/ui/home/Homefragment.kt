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
import com.example.finalyearproject.databinding.FragmentHomeBinding
import com.example.finalyearproject.utils.FirestoreSeeder
import com.example.finalyearproject.utils.Resource
import com.google.android.material.chip.Chip

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var recommendAdapter: RecipeAdapter
    private lateinit var trendingAdapter: RecipeAdapter
    private lateinit var allRecipesAdapter: RecipeAdapter

    private val categories = listOf("All","Soup","Rice","Noodle","Sandwich",
        "Stir Fry","Appetizer","Dessert","Crepe")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Seed data so the screen is never blank
        FirestoreSeeder.seedIfEmpty()

        setupRecyclerViews()
        setupCategoryChips()
        setupSearch()
        setupSwipeRefresh()
        observeViewModel()
    }

    // ── RecyclerViews ─────────────────────────────────────────────────────────

    private fun setupRecyclerViews() {
        recommendAdapter = RecipeAdapter(
            onRecipeClick   = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = true
        )
        trendingAdapter = RecipeAdapter(
            onRecipeClick   = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = true
        )
        allRecipesAdapter = RecipeAdapter(
            onRecipeClick   = { recipe ->
                viewModel.onRecipeVisible(recipe.recipeId)
                navigateToDetail(recipe.recipeId)
            },
            onFavoriteClick = { recipe -> viewModel.toggleLike(recipe) },
            horizontal      = false
        )

        binding.rvRecommended.apply {
            adapter     = recommendAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvTrending.apply {
            adapter     = trendingAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvRecipes.apply {
            adapter       = allRecipesAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    // ── Category chips ────────────────────────────────────────────────────────

    private fun setupCategoryChips() {
        val group = binding.chipGroupCategories
        group.removeAllViews()

        categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text       = cat
                isCheckable = true
                isChecked  = cat == "All"
                chipCornerRadius  = 20f
                chipStrokeWidth   = 1.5f
                chipMinHeight     = 36f.dpToPx()
                textSize          = 13f
                setTextColor(resources.getColorStateList(
                    com.example.finalyearproject.R.color.selector_chip_text, null))
                setChipBackgroundColorResource(
                    com.example.finalyearproject.R.color.selector_chip_bg)
                setChipStrokeColorResource(
                    com.example.finalyearproject.R.color.brand_primary)
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val selected = if (cat == "All") null else cat
                    viewModel.filterByCategory(selected)
                }
            }
            group.addView(chip)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                // Show/hide sections based on search mode
                val isSearching = query.isNotEmpty()
                binding.layoutAiSection.visibility     = if (isSearching) View.GONE else View.VISIBLE
                binding.layoutTrendingSection.visibility = if (isSearching) View.GONE else View.VISIBLE
                viewModel.search(query)
            }
        })
    }

    // ── Swipe to refresh ─────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
            com.example.finalyearproject.R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {

        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
        }

        viewModel.likedIds.observe(viewLifecycleOwner) { ids ->
            recommendAdapter.setLikedIds(ids)
            trendingAdapter.setLikedIds(ids)
            allRecipesAdapter.setLikedIds(ids)
        }

        viewModel.recommendations.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.shimmerRecommended.apply { startShimmer(); visibility = View.VISIBLE }
                    binding.rvRecommended.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.shimmerRecommended.apply { stopShimmer(); visibility = View.GONE }
                    if (resource.data.isEmpty()) {
                        binding.layoutAiSection.visibility = View.GONE
                    } else {
                        binding.layoutAiSection.visibility = View.VISIBLE
                        binding.rvRecommended.visibility   = View.VISIBLE
                        recommendAdapter.submitList(resource.data)
                    }
                }
                is Resource.Error -> {
                    binding.shimmerRecommended.apply { stopShimmer(); visibility = View.GONE }
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
                    binding.rvRecipes.visibility    = View.GONE
                    binding.layoutEmpty.visibility  = View.GONE
                }
                is Resource.Success -> {
                    binding.shimmerRecipes.apply { stopShimmer(); visibility = View.GONE }
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

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToDetail(recipeId: String) {
        // Uncomment when RecipeDetailFragment is ready:
        // val action = HomeFragmentDirections.actionHomeToDetail(recipeId)
        // findNavController().navigate(action)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
