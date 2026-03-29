package com.example.finalyearproject.ui.create

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.utils.Resource
import kotlinx.coroutines.launch

class CreateRecipeViewModel : ViewModel() {

    private val repo = RecipeRepository.getInstance()

    // Holds the local URI before upload
    var selectedImageUri: Uri? = null

    private val _createState = MutableLiveData<Resource<String>>()
    val createState: LiveData<Resource<String>> = _createState

    fun submitRecipe(
        title       : String,
        description : String,
        ingredients : List<String>,
        steps       : List<String>,
        category    : String,
        cookTime    : Int,
        videoUrl    : String = ""
    ) {
        if (title.isBlank()) {
            _createState.value = Resource.Error("Title is required")
            return
        }
        if (ingredients.all { it.isBlank() }) {
            _createState.value = Resource.Error("Add at least one ingredient")
            return
        }

        _createState.value = Resource.Loading("Uploading recipe…")

        viewModelScope.launch {
            // Upload image first (if selected)
            val imageUrl = if (selectedImageUri != null) {
                when (val r = repo.uploadImage(selectedImageUri!!)) {
                    is Resource.Success -> r.data
                    is Resource.Error   -> {
                        _createState.value = Resource.Error("Image upload failed: ${r.message}")
                        return@launch
                    }
                    else -> ""
                }
            } else ""

            _createState.value = repo.createRecipe(
                title       = title,
                description = description,
                ingredients = ingredients.filter { it.isNotBlank() },
                steps       = steps.filter { it.isNotBlank() },
                category    = category,
                cookTime    = cookTime,
                imageUrl    = imageUrl,
                videoUrl    = videoUrl
            )
        }
    }
}