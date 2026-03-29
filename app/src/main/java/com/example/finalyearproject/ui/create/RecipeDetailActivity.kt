package com.example.finalyearproject.ui.recipe

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.finalyearproject.R
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.databinding.ActivityRecipeDetailBinding
import com.example.finalyearproject.utils.Resource

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private val viewModel: RecipeDetailViewModel by viewModels()

    private lateinit var commentAdapter: CommentAdapter
    private var currentRecipe: Recipe? = null

    private val recipeId: String by lazy {
        intent.getStringExtra("recipeId") ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupCommentRecyclerView()
        setupActions()
        observeViewModel()

        if (recipeId.isNotBlank()) {
            viewModel.load(recipeId)
        } else {
            Toast.makeText(this, "Recipe not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupCommentRecyclerView() {
        commentAdapter = CommentAdapter()
        binding.rvComments.apply {
            adapter = commentAdapter
            layoutManager = LinearLayoutManager(this@RecipeDetailActivity)
            isNestedScrollingEnabled = false
        }
    }

    // ── Action buttons ────────────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnLike.setOnClickListener {
            viewModel.toggleLike(recipeId)
        }

        binding.btnSave.setOnClickListener {
            currentRecipe?.let { viewModel.toggleSave(it) }
        }

        // Comment send on button click
        binding.btnSendComment.setOnClickListener { sendComment() }

        // Comment send on keyboard "Done"
        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment(); true
            } else false
        }
    }

    private fun sendComment() {
        val text = binding.etComment.text?.toString()?.trim() ?: ""
        if (text.isBlank()) return
        viewModel.postComment(recipeId, text)
        binding.etComment.setText("")
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etComment.windowToken, 0)
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {

        viewModel.recipe.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> { /* could show shimmer */ }
                is Resource.Success -> {
                    currentRecipe = resource.data
                    bindRecipe(resource.data)
                }
                is Resource.Error -> {
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.isLiked.observe(this) { liked ->
            binding.btnLike.text = if (liked) "❤️  Liked" else "❤️  Like"
            binding.btnLike.setTextColor(
                getColor(if (liked) R.color.status_error else R.color.text_primary)
            )
        }

        viewModel.isSaved.observe(this) { saved ->
            binding.btnSave.text = if (saved) "🔖  Saved" else "🔖  Save"
            binding.btnSave.setTextColor(
                getColor(if (saved) R.color.brand_primary else R.color.text_primary)
            )
        }

        viewModel.comments.observe(this) { resource ->
            if (resource is Resource.Success) {
                commentAdapter.submitList(resource.data)
            }
        }

        viewModel.actionResult.observe(this) { resource ->
            if (resource is Resource.Error) {
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    private fun bindRecipe(recipe: Recipe) {
        binding.collapsingToolbar.title = recipe.title
        binding.tvTitle.text            = recipe.title
        binding.tvAuthor.text           = recipe.authorName.ifBlank { "Unknown" }
        binding.tvCookTime.text         = "⏱ ${recipe.totalTimeMinutes} min"

        // Ingredients — bullet list
        binding.tvIngredients.text = recipe.ingredients
            .joinToString("\n") { "• $it" }
            .ifBlank { "No ingredients listed." }

        // Steps — numbered list
        binding.tvSteps.text = recipe.instructions
            .mapIndexed { i, step -> "${i + 1}. $step" }
            .joinToString("\n\n")
            .ifBlank {
                // Fallback: check for "steps" field from CreateRecipeActivity
                "No instructions listed."
            }

        // Load image
        Glide.with(this)
            .load(recipe.imageUrl)
            .placeholder(R.drawable.ic_recipe_placeholder)
            .centerCrop()
            .into(binding.ivRecipeImage)

        // Author avatar (if available)
        if (!recipe.authorImageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(recipe.authorImageUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(binding.ivAuthor)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CommentAdapter
// ─────────────────────────────────────────────────────────────────────────────

class CommentAdapter : androidx.recyclerview.widget.ListAdapter<Map<String, Any>,
        CommentAdapter.CommentVH>(object :
    androidx.recyclerview.widget.DiffUtil.ItemCallback<Map<String, Any>>() {
    override fun areItemsTheSame(a: Map<String, Any>, b: Map<String, Any>) =
        a["text"] == b["text"] && a["userId"] == b["userId"]
    override fun areContentsTheSame(a: Map<String, Any>, b: Map<String, Any>) = a == b
}) {

    inner class CommentVH(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val tvAuthor: android.widget.TextView = itemView.findViewById(R.id.tv_comment_author)
        val tvText  : android.widget.TextView = itemView.findViewById(R.id.tv_comment_text)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CommentVH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentVH(v)
    }

    override fun onBindViewHolder(holder: CommentVH, position: Int) {
        val item = getItem(position)
        holder.tvAuthor.text = item["authorName"] as? String ?: "User"
        holder.tvText.text   = item["text"]       as? String ?: ""
    }
}