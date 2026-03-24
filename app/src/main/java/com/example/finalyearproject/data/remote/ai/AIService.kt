package com.example.finalyearproject.data.remote.ai

import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.retryCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AIService — Day 3 Enhanced
 *
 * Improvements:
 *  - suggestMealByPreference() combines user profile + favorites for personalised prompts
 *  - generateRecipeFromFavorites() uses user's liked recipes as context
 *  - All calls wrapped in retryCall() for automatic retry on network errors
 *  - Structured system prompts per function for consistent AI output
 */
class AIService private constructor() {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    companion object {
        @Volatile private var instance: AIService? = null
        fun getInstance(): AIService =
            instance ?: synchronized(this) {
                instance ?: AIService().also { instance = it }
            }

        // ── System prompt constants ────────────────────────────────────────────
        private const val BASE_SYSTEM =
            "You are a professional chef and certified nutritionist with 20 years of experience. " +
                    "You give practical, delicious, and healthy food advice. " +
                    "Be concise, specific, and always explain the reasoning behind your suggestions."

        private const val RECIPE_FORMAT =
            "Format your recipe response as:\n" +
                    "**[Recipe Name]**\n" +
                    "📝 Description: ...\n" +
                    "⏱ Prep: ... | Cook: ... | Servings: ...\n" +
                    "⭐ Difficulty: Easy/Medium/Hard\n\n" +
                    "**Ingredients:**\n- ...\n\n" +
                    "**Instructions:**\n1. ...\n\n" +
                    "**Tips:** ..."

        private const val NUTRITION_FORMAT =
            "Format your nutrition response as:\n" +
                    "**Nutritional Analysis**\n" +
                    "🍽 Serving size: ...\n" +
                    "🔥 Calories: ...\n" +
                    "💪 Protein: ...g\n" +
                    "🌾 Carbohydrates: ...g\n" +
                    "🥑 Fat: ...g\n" +
                    "🥦 Fiber: ...g\n" +
                    "⚡ Key nutrients: ...\n" +
                    "✅ Health notes: ..."
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC AI FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Suggests a meal based on a free-text prompt.
     * Uses retryCall for automatic retry on network errors.
     */
    suspend fun suggestMeal(prompt: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext Resource.Error("Prompt cannot be empty.")

            retryCall {
                val system = "$BASE_SYSTEM\n\nWhen suggesting a meal, include: " +
                        "meal name, brief description, key ingredients, and why it fits the request."
                sendRequest(system, prompt)
            }
        }

    /**
     * Generates a full recipe from ingredient list.
     */
    suspend fun generateRecipe(ingredients: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (ingredients.isBlank())
                return@withContext Resource.Error("Ingredients cannot be empty.")

            retryCall {
                val system = "$BASE_SYSTEM\n\n$RECIPE_FORMAT"
                val userMsg = "Generate a recipe using these ingredients: $ingredients\n" +
                        "(You may add basic pantry staples like salt, pepper, oil, garlic)"
                sendRequest(system, userMsg)
            }
        }

    /**
     * Analyses nutritional content of a food description.
     */
    suspend fun analyzeNutrition(food: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (food.isBlank())
                return@withContext Resource.Error("Food description cannot be empty.")

            retryCall {
                val system = "$BASE_SYSTEM\n\n$NUTRITION_FORMAT"
                sendRequest(system, "Analyze this food/recipe: $food")
            }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSONALISED AI FUNCTIONS (Day 3 additions)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Suggests a meal personalised to the user's preferences and dietary profile.
     *
     * @param userPreference  Free-text preference from the user (e.g. "something light for lunch")
     * @param user            The Firebase user profile — used to personalise the prompt
     * @param favoriteRecipes User's saved recipes — used as taste context
     *
     * Example context built:
     *   "User: Alice. Favourites: Chicken Curry, Pasta Carbonara, Green Salad.
     *    Request: something light for lunch."
     */
    suspend fun suggestMealByPreference(
        userPreference: String,
        user: User? = null,
        favoriteRecipes: List<Recipe> = emptyList()
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (userPreference.isBlank())
            return@withContext Resource.Error("Please describe your preference.")

        retryCall {
            val context = buildUserContext(user, favoriteRecipes)
            val system = "$BASE_SYSTEM\n\n" +
                    "Personalise your meal suggestion based on the user's profile and past favourites. " +
                    "Reference their taste history when relevant. " +
                    "Suggest ONE specific meal with a brief explanation of why it fits."

            val userMessage = buildString {
                if (context.isNotEmpty()) {
                    appendLine("User context: $context")
                    appendLine()
                }
                append("Request: $userPreference")
            }

            sendRequest(system, userMessage)
        }
    }

    /**
     * Generates a new recipe inspired by the user's favorited recipes.
     * Creates a "fusion" or "you might also like" recipe.
     *
     * @param favoriteRecipes  The user's saved recipes to use as inspiration
     * @param twist            Optional instruction like "make it healthier" or "Asian fusion"
     */
    suspend fun generateRecipeFromFavorites(
        favoriteRecipes: List<Recipe>,
        twist: String? = null
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (favoriteRecipes.isEmpty())
            return@withContext Resource.Error("Save some recipes first to get personalised suggestions.")

        retryCall {
            val favTitles = favoriteRecipes.take(5).joinToString(", ") { it.title }
            val favCategories = favoriteRecipes.map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")

            val system = "$BASE_SYSTEM\n\n$RECIPE_FORMAT"
            val userMessage = buildString {
                append("Based on these favourite recipes: $favTitles")
                if (favCategories.isNotEmpty()) append(" (categories: $favCategories)")
                append(", generate a NEW recipe they would likely enjoy.")
                twist?.let { append(" Twist: $it") }
            }

            sendRequest(system, userMessage)
        }
    }

    /**
     * Provides a weekly meal plan tailored to the user's preferences.
     *
     * @param preferences  Diet/lifestyle preferences (e.g. "high protein, no dairy")
     * @param days         Number of days (default 7)
     */
    suspend fun generateMealPlan(
        preferences: String,
        days: Int = 7
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (preferences.isBlank())
            return@withContext Resource.Error("Please describe your dietary preferences.")
        if (days !in 1..14)
            return@withContext Resource.Error("Meal plan days must be between 1 and 14.")

        retryCall {
            val system = "$BASE_SYSTEM\n\n" +
                    "Create a practical $days-day meal plan. " +
                    "Format as:\n" +
                    "**Day 1**\n" +
                    "🌅 Breakfast: ...\n" +
                    "☀️ Lunch: ...\n" +
                    "🌙 Dinner: ...\n" +
                    "🍎 Snack: ...\n" +
                    "(repeat for each day)\n\n" +
                    "End with a brief shopping list summary."

            sendRequest(system, "Preferences: $preferences. Create a $days-day meal plan.")
        }
    }

    /**
     * Answers a general food or cooking question.
     */
    suspend fun askFoodQuestion(question: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (question.isBlank()) return@withContext Resource.Error("Question cannot be empty.")
            retryCall { sendRequest(BASE_SYSTEM, question) }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Core OkHttp POST to Gemini API.
     * Returns the text content from candidates[0].content.parts[0].text
     */
    private fun sendRequest(
        systemInstruction: String,
        userPrompt: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7
    ): Resource<String> {
        return try {
            val requestBody = buildRequestBody(systemInstruction, userPrompt, maxTokens, temperature)

            val url = "${Constants.AI_BASE_URL}${Constants.AI_ENDPOINT_QUERY}" +
                    "?key=${Constants.AI_API_KEY}"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val rawJson = response.body?.string()
                ?: return Resource.Error("Empty response from Gemini.")

            if (!response.isSuccessful) {
                return Resource.Error(
                    "Gemini API error ${response.code}: ${parseErrorMessage(rawJson)}"
                )
            }

            Resource.Success(parseResponseText(rawJson))

        } catch (e: IOException) {
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Resource.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    /**
     * Builds the Gemini-format JSON payload.
     */
    private fun buildRequestBody(
        systemInstruction: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val payload = mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction))
            ),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to userPrompt)))
            ),
            "generationConfig" to mapOf(
                "maxOutputTokens" to maxTokens,
                "temperature" to temperature
            )
        )
        return gson.toJson(payload)
    }

    private fun parseResponseText(rawJson: String): String = try {
        gson.fromJson(rawJson, JsonObject::class.java)
            .getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: "No response received."
    } catch (e: Exception) {
        "Could not parse response."
    }

    private fun parseErrorMessage(rawJson: String): String = try {
        gson.fromJson(rawJson, JsonObject::class.java)
            .getAsJsonObject("error")
            ?.get("message")?.asString ?: rawJson
    } catch (e: Exception) { rawJson }

    /**
     * Builds a compact user context string for personalised prompts.
     * Example output:
     *   "User: Alice | Favourites: Chicken Curry, Pasta, Green Salad"
     */
    private fun buildUserContext(user: User?, favorites: List<Recipe>): String {
        val parts = mutableListOf<String>()
        user?.displayName?.takeIf { it.isNotBlank() }?.let {
            parts.add("User: $it")
        }
        if (favorites.isNotEmpty()) {
            val titles = favorites.take(5).joinToString(", ") { it.title }
            parts.add("Favourites: $titles")
            val categories = favorites.map { it.category }.filter { it.isNotBlank() }.distinct()
            if (categories.isNotEmpty()) parts.add("Preferred categories: ${categories.joinToString(", ")}")
        }
        return parts.joinToString(" | ")
    }
}