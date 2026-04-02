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
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun askFoodQuestion(question: String): Resource<String> {
        if (question.isBlank()) return Resource.Success(getFallback("general"))
        return callGemini(system = BASE_SYSTEM, user = question)
    }

    suspend fun suggestMeal(prompt: String): Resource<String> =
        callGemini(
            system = "$BASE_SYSTEM Suggest ONE specific meal. Include name, brief description, key ingredients, and cook time.",
            user = prompt
        )

    suspend fun generateRecipe(ingredients: String): Resource<String> =
        callGemini(
            system = "$BASE_SYSTEM $RECIPE_FORMAT",
            user = "Generate a recipe using: $ingredients"
        )

    suspend fun analyzeNutrition(food: String): Resource<String> =
        callGemini(
            system = "$BASE_SYSTEM $NUTRITION_FORMAT",
            user = "Analyze nutrition for: $food"
        )

    suspend fun suggestMealByPreference(
        userPreference: String,
        favoriteCategories: List<String> = emptyList()
    ): Resource<String> {
        val ctx = if (favoriteCategories.isNotEmpty())
            "User enjoys: ${favoriteCategories.joinToString(", ")}. " else ""
        return callGemini(
            system = "$BASE_SYSTEM Personalise your suggestion based on the user's tastes.",
            user = "${ctx}Request: $userPreference"
        )
    }

    private suspend fun callGemini(
        system: String,
        user: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7
    ): Resource<String> = withContext(Dispatchers.IO) {

        val apiKey: String = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "BuildConfig.GEMINI_API_KEY missing – check build.gradle", e)
            ""
        }

        Log.d(TAG, "API key length: ${apiKey.length} (0 = missing)")

        if (apiKey.isBlank() || apiKey.contains("YOUR_") || apiKey.length < 20) {
            Log.w(TAG, "GEMINI_API_KEY not properly configured – using fallback")
            return@withContext Resource.Success(getFallback(user))
        }

        val body = gson.toJson(
            mapOf(
                "system_instruction" to mapOf(
                    "parts" to listOf(mapOf("text" to system))
                ),
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to user)))
                ),
                "generationConfig" to mapOf(
                    "maxOutputTokens" to maxTokens,
                    "temperature" to temperature
                )
            )
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonType))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            Log.d(TAG, "Gemini HTTP ${response.code} – preview: ${rawJson.take(200)}")

            if (!response.isSuccessful) {
                Log.w(TAG, "API error ${response.code}: ${rawJson.take(300)}")
                return@withContext Resource.Success(getFallback(user))
            }

            val text = parseResponse(rawJson)
            if (text.isBlank()) {
                Log.w(TAG, "Empty response from Gemini – using fallback")
                Resource.Success(getFallback(user))
            } else {
                Resource.Success(text)
            }
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            Resource.Success(getFallback(user))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            Resource.Success(getFallback(user))
        }
    }

    private fun parseResponse(json: String): String = try {
        gson.fromJson(json, JsonObject::class.java)
            .getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: ""
    } catch (e: Exception) {
        Log.w(TAG, "Parse failed: ${e.message}")
        ""
    }

    private fun getFallback(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("pho") || q.contains("phở") ->
                "🍜 **Pho (Phở)**\n\nA typical Vietnamese pho bowl has around **350–500 calories** depending on portion size and protein choice.\n\n• Beef pho (~450 kcal) is higher in protein\n• Chicken pho (~350 kcal) is leaner\n• Vegetable pho (~250 kcal) is the lightest\n\nPho is rich in collagen from bone broth, making it great for gut health!"

            q.contains("calorie") || q.contains("nutrition") ->
                "🔥 **Quick Nutrition Guide**\n\nCommon Vietnamese dishes:\n• Phở bò — ~400–500 kcal\n• Bánh mì — ~350–450 kcal\n• Gỏi cuốn (fresh rolls) — ~100–150 kcal each\n• Cơm tấm — ~550–650 kcal\n• Bún bò Huế — ~450–550 kcal\n\nTip: fresh rolls and soups are your lightest options!"

            q.contains("recipe") || q.contains("make") || q.contains("cook") ->
                "🍳 **Quick Garlic Butter Chicken Rice**\n\n⏱ 25 min | 👨‍🍳 Easy | 🍽 2 servings\n\n**Ingredients:**\n• 300g chicken breast, cubed\n• 2 cups jasmine rice\n• 4 cloves garlic, minced\n• 2 tbsp butter\n• Fish sauce, soy sauce, pepper\n• Spring onion for garnish\n\n**Steps:**\n1. Cook rice in rice cooker\n2. Heat butter, sauté garlic until golden\n3. Add chicken, season with fish sauce + soy sauce\n4. Cook 8–10 min until done\n5. Serve over rice, garnish with spring onion\n\n✨ Simple, satisfying, and under 500 calories!"

            q.contains("quick") || q.contains("fast") || q.contains("easy") ->
                "⚡ **5 Quick Meals Under 30 Minutes**\n\n1. **Egg Fried Rice** — 10 min, uses leftovers\n2. **Chicken Stir-Fry** — 15 min, high protein\n3. **Banh Mi Sandwich** — 15 min, satisfying\n4. **Instant Noodles Upgrade** — 8 min, add egg + veggies\n5. **Scrambled Egg Toast** — 5 min, great breakfast\n\nAll under 500 calories and perfect for busy days!"

            q.contains("healthy") || q.contains("diet") || q.contains("weight") ->
                "🥗 **Healthy Eating Tips**\n\n• Choose **fresh spring rolls** over fried ones (saves ~200 kcal)\n• **Pho and bún** soups are filling and low-calorie if you limit noodles\n• Swap white rice for **brown rice** for more fiber\n• **Gỏi (salads)** are excellent — low calorie, high nutrients\n• Drink green tea instead of sugary drinks\n\n💡 Vietnamese cuisine is naturally one of the healthiest in the world!"

            else ->
                "👨‍🍳 **Food AI Assistant**\n\nI can help you with:\n\n🍳 **Recipes** — \"Make something with chicken and vegetables\"\n🔥 **Calories** — \"How many calories in bánh mì?\"\n⚡ **Quick meals** — \"What can I make in 20 minutes?\"\n🥗 **Nutrition** — \"Is pho healthy?\"\n\nJust type your question and I'll answer!"
        }
    }

    companion object {
        private const val TAG = "AIService"
        private const val BASE_SYSTEM =
            "You are a professional Vietnamese chef and certified nutritionist. " +
                    "Give practical, accurate, and engaging food advice. Be concise — " +
                    "use markdown bold (**text**) for key terms. Always be helpful and friendly."

        private const val RECIPE_FORMAT =
            "Format: **Recipe Name** | ⏱ time | 👨‍🍳 difficulty | 🍽 servings\n" +
                    "Then: Ingredients list (bullet points), Instructions (numbered), " +
                    "one Tip at the end."

        private const val NUTRITION_FORMAT =
            "Format: 🔥 Calories | 💪 Protein | 🌾 Carbs | 🥑 Fat | ✅ Health note. " +
                    "Keep it brief and practical."

        @Volatile
        private var instance: AIService? = null

        fun getInstance(): AIService = instance ?: synchronized(this) {
            instance ?: AIService().also { instance = it }
        }
    }
}