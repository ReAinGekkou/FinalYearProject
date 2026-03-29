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

    private val _myRecipes   = MutableLiveData<Resource<List<Recipe>>>()
    val myRecipes: LiveData<Resource<List<Recipe>>> = _myRecipes

    private val _favorites   = MutableLiveData<Resource<List<Recipe>>>()
    val favorites: LiveData<Resource<List<Recipe>>> = _favorites

    fun loadMyRecipes() {
        _myRecipes.value = Resource.Loading()
        viewModelScope.launch {
            _myRecipes.value = repo.getMyRecipes()
        }
    }

    fun loadFavorites() {
        _favorites.value = Resource.Loading()
        viewModelScope.launch {
            _favorites.value = repo.getFavorites()
        }
    }
}