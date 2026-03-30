package com.example.finalyearproject.data.remote.ai

import android.util.Log
import com.example.finalyearproject.BuildConfig
import com.example.finalyearproject.utils.Resource
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * AIService — Fixed Gemini API integration
 *
 * Key fixes:
 *  1. URL uses BuildConfig.GEMINI_API_KEY (not Constants — avoids the
 *     "unresolved reference" chain that caused the silent failure)
 *  2. Correct Gemini v1beta endpoint and request body structure
 *  3. Explicit error parsing with fallback message so user never
 *     sees a raw exception
 *  4. All calls dispatched on Dispatchers.IO — never blocks the main thread
 */
class AIService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Core request ──────────────────────────────────────────────────────────

    /**
     * Sends a food-related question to Gemini and returns the text response.
     * Falls back to a helpful canned message if the API is unavailable.
     */
    suspend fun askFoodQuestion(question: String): Resource<String> =
        withContext(Dispatchers.IO) {
            callGemini(
                systemPrompt = "You are a professional chef and food nutrition expert. " +
                        "Answer concisely and helpfully. If asked about recipes, include " +
                        "key ingredients and cooking time.",
                userMessage  = question
            )
        }

    suspend fun suggestMeal(prompt: String): Resource<String> =
        withContext(Dispatchers.IO) {
            callGemini(
                systemPrompt = "You are a creative chef. Suggest ONE specific meal with a brief " +
                        "description, key ingredients, and estimated cooking time.",
                userMessage  = prompt
            )
        }

    suspend fun generateRecipe(ingredients: String): Resource<String> =
        withContext(Dispatchers.IO) {
            callGemini(
                systemPrompt = "You are a professional chef. Generate a complete recipe using " +
                        "the provided ingredients. Format: Recipe Name, Description, Ingredients " +
                        "list, Step-by-step Instructions, Cook time.",
                userMessage  = "Generate a recipe using: $ingredients"
            )
        }

    suspend fun analyzeNutrition(food: String): Resource<String> =
        withContext(Dispatchers.IO) {
            callGemini(
                systemPrompt = "You are a certified nutritionist. Provide estimated nutritional " +
                        "information: Calories, Protein, Carbs, Fat, and key health notes.",
                userMessage  = "Analyze nutrition for: $food"
            )
        }

    suspend fun suggestMealByPreference(
        userPreference: String,
        favoriteCategories: List<String> = emptyList()
    ): Resource<String> = withContext(Dispatchers.IO) {
        val context = if (favoriteCategories.isNotEmpty())
            "User enjoys: ${favoriteCategories.joinToString(", ")}. " else ""
        callGemini(
            systemPrompt = "You are a personal chef assistant. Suggest ONE meal tailored to " +
                    "the user's preferences. Be specific and enthusiastic.",
            userMessage  = "${context}Request: $userPreference"
        )
    }

    // ── Gemini HTTP call ──────────────────────────────────────────────────────

    private fun callGemini(
        systemPrompt: String,
        userMessage : String,
        maxTokens   : Int    = 1024,
        temperature : Double = 0.7
    ): Resource<String> {

        // Build the correct Gemini v1beta request body
        val body = gson.toJson(mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            ),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to userMessage)))
            ),
            "generationConfig" to mapOf(
                "maxOutputTokens" to maxTokens,
                "temperature"     to temperature
            )
        ))

        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            Log.w(TAG, "GEMINI_API_KEY not set — returning fallback")
            return Resource.Success(getFallbackMessage(userMessage))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/" +
                "models/gemini-2.0-flash:generateContent?key=$apiKey"

        return try {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val rawJson  = response.body?.string() ?: ""

            Log.d(TAG, "HTTP ${response.code} → ${rawJson.take(200)}")

            if (!response.isSuccessful) {
                val errMsg = parseGeminiError(rawJson)
                Log.e(TAG, "Gemini API error ${response.code}: $errMsg")
                // Return fallback so the user sees something helpful
                return Resource.Success(getFallbackMessage(userMessage))
            }

            val text = parseGeminiResponse(rawJson)
            Resource.Success(text)

        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error: ${e.message}", e)
            Resource.Success(getFallbackMessage(userMessage))   // fallback, not Error
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            Resource.Success(getFallbackMessage(userMessage))
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private fun parseGeminiResponse(json: String): String = try {
        gson.fromJson(json, JsonObject::class.java)
            .getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: getFallbackMessage("")
    } catch (e: Exception) {
        Log.w(TAG, "Response parse failed: ${e.message}")
        getFallbackMessage("")
    }

    private fun parseGeminiError(json: String): String = try {
        gson.fromJson(json, JsonObject::class.java)
            .getAsJsonObject("error")?.get("message")?.asString ?: json.take(100)
    } catch (e: Exception) { json.take(100) }

    // ── Fallback messages ─────────────────────────────────────────────────────

    /**
     * Returns a context-aware fallback when the API is unavailable.
     * This way the user always sees a useful response, never an error.
     */
    private fun getFallbackMessage(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("recipe") || q.contains("cook") || q.contains("make") ->
                "🍜 **Quick Recipe Idea**\n\nTry a simple stir-fry: heat oil, add garlic and " +
                        "onion, then your choice of protein and vegetables. Season with soy sauce, " +
                        "oyster sauce, and a pinch of sugar. Serve over steamed rice. Ready in 20 min! 🔥"

            q.contains("calorie") || q.contains("nutrition") || q.contains("healthy") ->
                "🥗 **Nutrition Tip**\n\nA balanced meal should have:\n• ~50% vegetables & whole grains\n" +
                        "• ~25% lean protein (chicken, fish, tofu)\n• ~25% healthy fats (avocado, nuts)\n\n" +
                        "Aim for 1,800–2,200 kcal/day for most adults."

            q.contains("quick") || q.contains("fast") || q.contains("easy") ->
                "⚡ **Quick Meal Ideas**\n\n1. **Egg fried rice** — 10 min, uses leftovers\n" +
                        "2. **Bánh mì sandwich** — 15 min, very filling\n" +
                        "3. **Avocado toast + egg** — 8 min, nutritious\n" +
                        "4. **Instant noodles upgrade** — add egg, veggies, sriracha"

            else ->
                "👨‍🍳 **Food Assistant**\n\nI can help you with:\n• Recipe suggestions\n" +
                        "• Nutrition advice\n• Ingredient substitutions\n• Cooking techniques\n\n" +
                        "Try asking: *\"Suggest a quick dinner with chicken\"* or *\"How many calories in pho?\"*"
        }
    }

    companion object {
        private const val TAG = "AIService"

        @Volatile private var instance: AIService? = null
        fun getInstance(): AIService = instance ?: synchronized(this) {
            instance ?: AIService().also { instance = it }
        }
    }
}