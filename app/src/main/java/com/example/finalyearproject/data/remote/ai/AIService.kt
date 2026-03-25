package com.example.finalyearproject.data.remote.ai

import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.utils.AppLogger
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
 * AIService — Day 4 Final
 *
 * Improvements:
 *  - All calls routed through retryCall() — 3 retries on network errors
 *  - Structured AppLogger calls on every request/response/error
 *  - Input validation before any network call
 *  - Clean separation of prompt building from HTTP execution
 */
class AIService private constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor { message ->
                AppLogger.d(AppLogger.TAG_AI, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val gson = Gson()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    companion object {
        @Volatile private var instance: AIService? = null
        fun getInstance(): AIService =
            instance ?: synchronized(this) {
                instance ?: AIService().also { instance = it }
            }

        private const val BASE_SYSTEM =
            "You are a professional chef and certified nutritionist with 20 years of experience. " +
                    "Provide practical, delicious, and healthy food advice. " +
                    "Be concise, specific, and explain the reasoning behind your suggestions."

        private const val RECIPE_FORMAT =
            "Format your response as:\n" +
                    "**[Recipe Name]**\n" +
                    "📝 Description: ...\n" +
                    "⏱ Prep: ... | Cook: ... | Servings: ...\n" +
                    "⭐ Difficulty: Easy/Medium/Hard\n\n" +
                    "**Ingredients:**\n- ...\n\n" +
                    "**Instructions:**\n1. ...\n\n" +
                    "**Chef's Tips:** ..."

        private const val NUTRITION_FORMAT =
            "Format your response as:\n" +
                    "**Nutritional Analysis**\n" +
                    "🍽 Serving size: ...\n" +
                    "🔥 Calories: ...\n" +
                    "💪 Protein: ...g  |  🌾 Carbs: ...g  |  🥑 Fat: ...g  |  🥦 Fiber: ...g\n" +
                    "⚡ Key nutrients: ...\n" +
                    "✅ Health notes: ..."
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun suggestMeal(prompt: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext Resource.Error("Prompt cannot be empty.")

            AppLogger.d(AppLogger.TAG_AI, "suggestMeal: prompt=${prompt.take(80)}")

            retryCall(tag = AppLogger.TAG_AI) {
                val system = "$BASE_SYSTEM Suggest ONE specific meal with: " +
                        "name, brief description, key ingredients, and why it fits the request."
                executeRequest(system, prompt)
            }
        }

    suspend fun generateRecipe(ingredients: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (ingredients.isBlank())
                return@withContext Resource.Error("Ingredients cannot be empty.")

            AppLogger.d(AppLogger.TAG_AI, "generateRecipe: ingredients=${ingredients.take(80)}")

            retryCall(tag = AppLogger.TAG_AI) {
                val system = "$BASE_SYSTEM\n\n$RECIPE_FORMAT"
                val prompt = "Generate a recipe using: $ingredients\n" +
                        "(You may add basic pantry staples: salt, pepper, oil, garlic)"
                executeRequest(system, prompt)
            }
        }

    suspend fun analyzeNutrition(food: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (food.isBlank())
                return@withContext Resource.Error("Food description cannot be empty.")

            AppLogger.d(AppLogger.TAG_AI, "analyzeNutrition: food=${food.take(80)}")

            retryCall(tag = AppLogger.TAG_AI) {
                val system = "$BASE_SYSTEM\n\n$NUTRITION_FORMAT"
                executeRequest(system, "Analyze this food/recipe: $food")
            }
        }

    suspend fun suggestMealByPreference(
        userPreference: String,
        user: User? = null,
        favoriteRecipes: List<Recipe> = emptyList()
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (userPreference.isBlank())
            return@withContext Resource.Error("Please describe your preference.")

        AppLogger.d(AppLogger.TAG_AI,
            "suggestMealByPreference: uid=${user?.uid ?: "anonymous"}, " +
                    "favCount=${favoriteRecipes.size}")

        retryCall(tag = AppLogger.TAG_AI) {
            val context = buildUserContext(user, favoriteRecipes)
            val system = "$BASE_SYSTEM\n\n" +
                    "Personalise your meal suggestion based on the user's profile and past favourites. " +
                    "Reference their taste history when relevant. Suggest ONE specific meal."

            val prompt = buildString {
                if (context.isNotEmpty()) appendLine("User context: $context\n")
                append("Request: $userPreference")
            }
            executeRequest(system, prompt)
        }
    }

    suspend fun generateRecipeFromFavorites(
        favoriteRecipes: List<Recipe>,
        twist: String? = null
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (favoriteRecipes.isEmpty())
            return@withContext Resource.Error("Save some recipes first to get personalised suggestions.")

        retryCall(tag = AppLogger.TAG_AI) {
            val favTitles = favoriteRecipes.take(5).joinToString(", ") { it.title }
            val categories = favoriteRecipes.map { it.category }
                .filter { it.isNotBlank() }.distinct().joinToString(", ")

            val system = "$BASE_SYSTEM\n\n$RECIPE_FORMAT"
            val prompt = buildString {
                append("Based on these favourite recipes: $favTitles")
                if (categories.isNotEmpty()) append(" (categories: $categories)")
                append(", generate a NEW recipe they would likely enjoy.")
                twist?.let { append(" Twist: $it") }
            }
            executeRequest(system, prompt)
        }
    }

    suspend fun generateMealPlan(
        preferences: String,
        days: Int = 7
    ): Resource<String> = withContext(Dispatchers.IO) {
        if (preferences.isBlank())
            return@withContext Resource.Error("Please describe your dietary preferences.")
        if (days !in 1..14)
            return@withContext Resource.Error("Days must be between 1 and 14.")

        AppLogger.d(AppLogger.TAG_AI, "generateMealPlan: days=$days")

        retryCall(tag = AppLogger.TAG_AI) {
            val system = "$BASE_SYSTEM\n\n" +
                    "Format the $days-day meal plan as:\n" +
                    "**Day 1**\n🌅 Breakfast: ...\n☀️ Lunch: ...\n🌙 Dinner: ...\n🍎 Snack: ...\n" +
                    "(repeat for each day, then add a brief shopping list at the end)"
            executeRequest(system, "Preferences: $preferences. Create a $days-day meal plan.",
                maxTokens = 2048)
        }
    }

    suspend fun askFoodQuestion(question: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (question.isBlank()) return@withContext Resource.Error("Question cannot be empty.")
            retryCall(tag = AppLogger.TAG_AI) {
                executeRequest(BASE_SYSTEM, question)
            }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE HTTP EXECUTION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun executeRequest(
        systemInstruction: String,
        userPrompt: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7
    ): Resource<String> {
        val url = "${Constants.AI_BASE_URL}${Constants.AI_ENDPOINT_QUERY}?key=${Constants.AI_API_KEY}"

        return try {
            val body = buildRequestBody(systemInstruction, userPrompt, maxTokens, temperature)
            AppLogger.apiRequest("POST", Constants.AI_BASE_URL + Constants.AI_ENDPOINT_QUERY, body)

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: return Resource.Error("Empty response from Gemini.")

            AppLogger.apiResponse(response.code, rawJson.take(300))

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(rawJson)
                AppLogger.e(AppLogger.TAG_AI, "Gemini API error ${response.code}: $errorMsg")
                return Resource.Error("AI error ${response.code}: $errorMsg")
            }

            val text = parseResponseText(rawJson)
            Resource.Success(text)

        } catch (e: IOException) {
            AppLogger.apiError(url, e)
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            AppLogger.apiError(url, e)
            Resource.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun buildRequestBody(
        system: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double
    ): String = gson.toJson(
        mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to system))
            ),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "maxOutputTokens" to maxTokens,
                "temperature" to temperature
            )
        )
    )

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
        AppLogger.e(AppLogger.TAG_AI, "Failed to parse Gemini response", e)
        "Could not parse response."
    }

    private fun parseErrorMessage(rawJson: String): String = try {
        gson.fromJson(rawJson, JsonObject::class.java)
            .getAsJsonObject("error")?.get("message")?.asString ?: rawJson
    } catch (e: Exception) { rawJson }

    private fun buildUserContext(user: User?, favorites: List<Recipe>): String {
        val parts = mutableListOf<String>()
        user?.displayName?.takeIf { it.isNotBlank() }?.let { parts.add("User: $it") }
        if (favorites.isNotEmpty()) {
            parts.add("Favourites: ${favorites.take(5).joinToString(", ") { it.title }}")
            val cats = favorites.map { it.category }.filter { it.isNotBlank() }.distinct()
            if (cats.isNotEmpty()) parts.add("Preferred categories: ${cats.joinToString(", ")}")
        }
        return parts.joinToString(" | ")
    }
}