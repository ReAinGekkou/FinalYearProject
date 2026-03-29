package com.example.finalyearproject.ui.recipe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

class RecipeDetailViewModel : ViewModel() {

    private val repo = RecipeRepository.getInstance()

    private val _recipe     = MutableLiveData<Resource<Recipe>>()
    val recipe: LiveData<Resource<Recipe>> = _recipe

    private val _isLiked    = MutableLiveData(false)
    val isLiked: LiveData<Boolean> = _isLiked

    private val _isSaved    = MutableLiveData(false)
    val isSaved: LiveData<Boolean> = _isSaved

    private val _comments   = MutableLiveData<Resource<List<Map<String, Any>>>>()
    val comments: LiveData<Resource<List<Map<String, Any>>>> = _comments

    private val _actionResult = MutableLiveData<Resource<String>>()
    val actionResult: LiveData<Resource<String>> = _actionResult

    fun load(recipeId: String) {
        _recipe.value = Resource.Loading()
        viewModelScope.launch {
            _recipe.value = repo.getRecipeById(recipeId)
            _isLiked.value = repo.isLikedBy(recipeId)
            _isSaved.value = repo.isSavedBy(recipeId)
            loadComments(recipeId)
        }
    }

    fun toggleLike(recipeId: String) {
        viewModelScope.launch {
            when (val r = repo.toggleLike(recipeId)) {
                is Resource.Success -> _isLiked.value = r.data
                is Resource.Error   -> _actionResult.value = Resource.Error(r.message)
                else -> Unit
            }
        }
    }

    fun toggleSave(recipe: Recipe) {
        viewModelScope.launch {
            when (val r = repo.toggleSave(recipe)) {
                is Resource.Success -> _isSaved.value = r.data
                is Resource.Error   -> _actionResult.value = Resource.Error(r.message)
                else -> Unit
            }
        }
    }

    fun postComment(recipeId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val r = repo.addComment(recipeId, text)) {
                is Resource.Success -> loadComments(recipeId)
                is Resource.Error   -> _actionResult.value = Resource.Error(r.message)
                else -> Unit
            }
        }
    }

    private suspend fun loadComments(recipeId: String) {
        _comments.value = repo.getComments(recipeId)
    }
}