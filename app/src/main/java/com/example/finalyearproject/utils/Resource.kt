package com.example.finalyearproject.utils

// ─────────────────────────────────────────────────────────────────────────────
// Resource.kt
//
// Sealed class that wraps every async operation result.
// Eliminates null checks and exception leakage across layers.
//
// Usage in ViewModel:
//   viewModelScope.launch {
//       _uiState.value = Resource.Loading()
//       _uiState.value = loginUseCase(email, password)
//   }
//
// Usage in Fragment/Activity:
//   viewModel.uiState.observe(viewLifecycleOwner) { resource ->
//       when (resource) {
//           is Resource.Loading -> showLoader()
//           is Resource.Success -> navigateHome(resource.data)
//           is Resource.Error   -> showError(resource.message)
//       }
//   }
// ─────────────────────────────────────────────────────────────────────────────

sealed class Resource<out T> {

    /**
     * Operation succeeded. [data] holds the result.
     */
    data class Success<out T>(val data: T) : Resource<T>()

    /**
     * Operation failed. [message] is a user-friendly error string.
     * [exception] is preserved for logging/crash reporting.
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : Resource<Nothing>()

    /**
     * Operation is in progress. [message] is an optional status string
     * (e.g. "Uploading image…") for granular loading feedback.
     */
    data class Loading(val message: String? = null) : Resource<Nothing>()

    // ── Convenience extensions ────────────────────────────────────────────────

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean   get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /** Returns the data if Success, null otherwise. */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the error message if Error, null otherwise. */
    fun errorMessage(): String? = (this as? Error)?.message

    /**
     * Transforms the data inside Success without changing the wrapper.
     * Error and Loading pass through untouched.
     */
    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error   -> this
        is Loading -> this
    }
}