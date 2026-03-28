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
import com.example.finalyearproject.databinding.ItemRecipeCardHorizontalBinding
import java.util.Locale

/**
 * RecipeAdapter — supports both vertical feed and horizontal scroll modes.
 *
 * @param horizontal  true = compact horizontal card (160dp wide)
 *                    false = full-width vertical card
 */
class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit,
    private val horizontal: Boolean = false
) : ListAdapter<Recipe, RecyclerView.ViewHolder>(RecipeDiffCallback()) {

    private val VIEW_VERTICAL   = 0
    private val VIEW_HORIZONTAL = 1

    private val likedIds  = mutableSetOf<String>()

    fun setLikedIds(ids: Set<String>) {
        val changed = ids != likedIds
        likedIds.clear()
        likedIds.addAll(ids)
        if (changed) notifyItemRangeChanged(0, itemCount, PAYLOAD_LIKED)
    }

    override fun getItemViewType(position: Int) =
        if (horizontal) VIEW_HORIZONTAL else VIEW_VERTICAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HORIZONTAL) {
            HorizontalViewHolder(
                ItemRecipeCardHorizontalBinding.inflate(inflater, parent, false)
            )
        } else {
            VerticalViewHolder(
                ItemRecipeCardBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val recipe = getItem(position)
        when (holder) {
            is VerticalViewHolder   -> holder.bind(recipe)
            is HorizontalViewHolder -> holder.bind(recipe)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_LIKED)) {
            val recipe = getItem(position)
            when (holder) {
                is VerticalViewHolder   -> holder.updateFavoriteIcon(recipe.recipeId in likedIds)
                is HorizontalViewHolder -> holder.updateFavoriteIcon(recipe.recipeId in likedIds)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ── Vertical ViewHolder ───────────────────────────────────────────────────

    inner class VerticalViewHolder(
        private val b: ItemRecipeCardBinding
    ) : RecyclerView.ViewHolder(b.root) {

        init {
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onRecipeClick(getItem(pos))
            }
            b.btnFavorite.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onFavoriteClick(getItem(pos))
            }
        }

        fun bind(recipe: Recipe) {
            b.tvRecipeTitle.text   = recipe.title
            b.chipCategory.text    = recipe.category.ifBlank { "Recipe" }
            b.ratingBar.rating     = recipe.averageRating.toFloat()
            b.tvRatingValue.text   = String.format(
                Locale.getDefault(), "%.1f (%d)", recipe.averageRating, recipe.reviewCount)
            b.tvAuthorName.text    = recipe.authorName.ifBlank { b.root.context.getString(R.string.label_unknown_author) }
            b.tvCookTime.text      = formatTime(recipe.totalTimeMinutes)
            b.chipDifficulty.text  = recipe.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }
            updateFavoriteIcon(recipe.recipeId in likedIds)

            Glide.with(b.ivRecipeImage.context)
                .load(recipe.imageUrl)
                .placeholder(R.drawable.ic_recipe_placeholder)
                .error(R.drawable.ic_recipe_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(b.ivRecipeImage)

            Glide.with(b.ivAuthorAvatar.context)
                .load(recipe.authorImageUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(b.ivAuthorAvatar)
        }

        fun updateFavoriteIcon(liked: Boolean) {
            b.btnFavorite.setIconResource(
                if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            b.btnFavorite.iconTint = b.root.context.getColorStateList(
                if (liked) R.color.status_error else android.R.color.white)
        }
    }

    // ── Horizontal ViewHolder ─────────────────────────────────────────────────

    inner class HorizontalViewHolder(
        private val b: ItemRecipeCardHorizontalBinding
    ) : RecyclerView.ViewHolder(b.root) {

        init {
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onRecipeClick(getItem(pos))
            }
            b.btnFavorite.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onFavoriteClick(getItem(pos))
            }
        }

        fun bind(recipe: Recipe) {
            b.tvRecipeTitle.text = recipe.title
            b.tvCookTime.text    = formatTime(recipe.totalTimeMinutes)
            b.tvLikeCount.text   = formatCount(recipe.likeCount)
            updateFavoriteIcon(recipe.recipeId in likedIds)

            Glide.with(b.ivRecipeImage.context)
                .load(recipe.imageUrl)
                .placeholder(R.drawable.ic_recipe_placeholder)
                .error(R.drawable.ic_recipe_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(b.ivRecipeImage)
        }

        fun updateFavoriteIcon(liked: Boolean) {
            b.btnFavorite.setIconResource(
                if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            b.btnFavorite.iconTint = b.root.context.getColorStateList(
                if (liked) R.color.status_error else android.R.color.white)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatTime(mins: Int): String = when {
        mins <= 0  -> "—"
        mins < 60  -> "${mins}m"
        else       -> "${mins / 60}h ${mins % 60}m".trimEnd()
    }

    private fun formatCount(n: Int): String = when {
        n >= 1000 -> String.format(Locale.getDefault(), "%.1fk", n / 1000.0)
        else      -> n.toString()
    }

    // ── DiffCallback ──────────────────────────────────────────────────────────

    class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(old: Recipe, new: Recipe) = old.recipeId == new.recipeId
        override fun areContentsTheSame(old: Recipe, new: Recipe) = old == new
    }

    companion object {
        private const val PAYLOAD_LIKED = "liked"
    }
}
