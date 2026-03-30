package com.example.finalyearproject.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repo = RecipeRepository.getInstance()

    private val _myRecipes = androidx.lifecycle.MutableLiveData<Resource<List<Recipe>>>()
    val myRecipes: androidx.lifecycle.LiveData<Resource<List<Recipe>>> = _myRecipes

    private val _favorites = androidx.lifecycle.MutableLiveData<Resource<List<Recipe>>>()
    val favorites: androidx.lifecycle.LiveData<Resource<List<Recipe>>> = _favorites

    /**
     * Uses the subcollection approach — completely avoids composite index.
     * Falls back to the direct query if subcollection is empty.
     */
    fun loadMyRecipes() {
        _myRecipes.value = Resource.Loading()
        viewModelScope.launch {
            // Try subcollection first (no index needed)
            var result = repo.getMyRecipesViaSubcollection()
            if (result is Resource.Success && result.data.isEmpty()) {
                // Fallback: try direct query (works if no orderBy — our fixed version)
                result = repo.getMyRecipes()
            }
            _myRecipes.value = result
        }
    }

    fun loadFavorites() {
        _favorites.value = Resource.Loading()
        viewModelScope.launch {
            _favorites.value = repo.getFavorites()
        }
    }
}