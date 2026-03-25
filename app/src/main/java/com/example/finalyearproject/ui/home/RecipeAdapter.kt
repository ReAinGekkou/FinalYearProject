package com.example.finalyearproject.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.finalyearproject.R
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.databinding.ItemRecipeCardBinding
import java.util.Locale

/**
 * RecipeAdapter — Day 5
 *
 * ListAdapter with DiffUtil for efficient partial updates.
 * Uses ViewBinding — no findViewById.
 * Glide handles image loading with cross-fade transition.
 *
 * Usage:
 *   val adapter = RecipeAdapter(
 *       onRecipeClick = { recipe -> navigateToDetail(recipe) },
 *       onFavoriteClick = { recipe -> viewModel.toggleFavorite(recipe) }
 *   )
 *   binding.rvRecipes.adapter = adapter
 *   adapter.submitList(recipes)
 */
class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {

    // Track favorited IDs for icon toggle without full rebind
    private val favoritedIds = mutableSetOf<String>()

    fun setFavoritedIds(ids: Set<String>) {
        val changed = ids != favoritedIds
        favoritedIds.clear()
        favoritedIds.addAll(ids)
        if (changed) notifyItemRangeChanged(0, itemCount, PAYLOAD_FAVORITE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: RecipeViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_FAVORITE)) {
            holder.updateFavoriteIcon(favoritedIds.contains(getItem(position).recipeId))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class RecipeViewHolder(
        private val binding: ItemRecipeCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onRecipeClick(getItem(position))
                }
            }
            binding.btnFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onFavoriteClick(getItem(position))
                }
            }
        }

        fun bind(recipe: Recipe) {
            with(binding) {
                // Title
                tvRecipeTitle.text = recipe.title

                // Category chip
                chipCategory.text = recipe.category.ifBlank { "Recipe" }

                // Rating
                ratingBar.rating = recipe.averageRating.toFloat()
                tvRatingValue.text = String.format(
                    Locale.getDefault(),
                    "%.1f (%d)",
                    recipe.averageRating,
                    recipe.reviewCount
                )

                // Author
                tvAuthorName.text = recipe.authorName.ifBlank {
                    itemView.context.getString(R.string.label_unknown_author)
                }

                // Cook time
                tvCookTime.text = formatTime(recipe.totalTimeMinutes)

                // Difficulty chip
                chipDifficulty.text = recipe.difficulty.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }

                // Favorite icon
                updateFavoriteIcon(favoritedIds.contains(recipe.recipeId))

                // Image — Glide with cross-fade
                Glide.with(ivRecipeImage.context)
                    .load(recipe.imageUrl)
                    .placeholder(R.drawable.ic_recipe_placeholder)
                    .error(R.drawable.ic_recipe_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(ivRecipeImage)

                // Author avatar
                Glide.with(ivAuthorAvatar.context)
                    .load(recipe.authorImageUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(ivAuthorAvatar)
            }
        }

        fun updateFavoriteIcon(isFavorited: Boolean) {
            binding.btnFavorite.setIconResource(
                if (isFavorited) R.drawable.ic_favourite_filled
                else R.drawable.ic_favourite_outline
            )
            binding.btnFavorite.iconTint = itemView.context.getColorStateList(
                if (isFavorited) R.color.status_error
                else android.R.color.white
            )
        }

        private fun formatTime(totalMinutes: Int): String {
            if (totalMinutes <= 0) return "—"
            return if (totalMinutes < 60) {
                "${totalMinutes}m"
            } else {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
            }
        }
    }

    // ── DiffCallback ──────────────────────────────────────────────────────────

    private class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(old: Recipe, new: Recipe) =
            old.recipeId == new.recipeId

        override fun areContentsTheSame(old: Recipe, new: Recipe) = old == new

        override fun getChangePayload(old: Recipe, new: Recipe): Any? {
            // Return a payload for favorite-only changes to avoid full rebind
            return null
        }
    }

    companion object {
        private const val PAYLOAD_FAVORITE = "payload_favorite"
    }
}