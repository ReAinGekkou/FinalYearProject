package com.example.finalyearproject.data.remote.ai

import com.example.finalyearproject.utils.Constants
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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AIService — Day 2
 * Gemini API via OkHttp. No Hilt — singleton pattern.
 *
 * Three public functions:
 *   suggestMeal()      → What should I eat based on a prompt?
 *   generateRecipe()   → Full recipe from a list of ingredients
 *   analyzeNutrition() → Nutritional breakdown of a food/recipe
 */
class AIService {

    // ── OkHttp Client ─────────────────────────────────────────────────────────

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
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Suggests a meal based on a free-text prompt.
     *
     * Example prompt:
     *   "I'm feeling tired and want something warm and filling.
     *    I have chicken, rice, and carrots in my fridge."
     *
     * @return Resource.Success with a meal suggestion string.
     */
    suspend fun suggestMeal(prompt: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext Resource.Error("Prompt cannot be empty.")

            val systemInstruction =
                "You are a professional chef and nutritionist. " +
                        "Suggest a specific meal based on the user's request. " +
                        "Keep your answer concise — meal name, brief description, and why it fits."

            sendRequest(systemInstruction, prompt)
        }

    /**
     * Generates a complete recipe from a list of ingredients.
     *
     * Example ingredients:
     *   "chicken breast, garlic, olive oil, lemon, rosemary"
     *
     * @return Resource.Success with a full recipe as a formatted string.
     */
    suspend fun generateRecipe(ingredients: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (ingredients.isBlank())
                return@withContext Resource.Error("Ingredients cannot be empty.")

            val systemInstruction =
                "You are a professional chef. Generate a complete recipe using ONLY " +
                        "the provided ingredients (plus basic pantry staples like salt, pepper, oil). " +
                        "Format your response as:\n" +
                        "**Recipe Name**\n" +
                        "Description: ...\n" +
                        "Prep Time: ... | Cook Time: ... | Servings: ...\n" +
                        "**Ingredients:**\n- item\n" +
                        "**Instructions:**\n1. step"

            val userPrompt = "Ingredients I have: $ingredients"
            sendRequest(systemInstruction, userPrompt)
        }

    /**
     * Analyses the nutritional content of a food or recipe description.
     *
     * Example food:
     *   "A bowl of chicken fried rice with 2 eggs, 1 cup rice, and vegetables."
     *
     * @return Resource.Success with a nutritional breakdown string.
     */
    suspend fun analyzeNutrition(food: String): Resource<String> =
        withContext(Dispatchers.IO) {
            if (food.isBlank())
                return@withContext Resource.Error("Food description cannot be empty.")

            val systemInstruction =
                "You are a certified nutritionist. Analyze the nutritional content " +
                        "of the provided food or recipe. Format your response as:\n" +
                        "**Nutritional Analysis**\n" +
                        "Serving size: ...\n" +
                        "Calories: ...\n" +
                        "Protein: ...g\n" +
                        "Carbohydrates: ...g\n" +
                        "Fat: ...g\n" +
                        "Fiber: ...g\n" +
                        "Key vitamins/minerals: ...\n" +
                        "Health notes: ..."

            sendRequest(systemInstruction, food)
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE REQUEST HANDLER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builds and executes the POST request to Gemini API.
     * Returns the text content from the response.
     */
    private fun sendRequest(
        systemInstruction: String,
        userPrompt: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7
    ): Resource<String> {
        return try {
            val requestBody = buildRequestBody(
                systemInstruction = systemInstruction,
                userPrompt = userPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )

            val url = "${Constants.AI_BASE_URL}${Constants.AI_ENDPOINT_QUERY}" +
                    "?key=${Constants.AI_API_KEY}"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val httpResponse = client.newCall(httpRequest).execute()

            val rawJson = httpResponse.body?.string()
                ?: return Resource.Error("Empty response from Gemini.")

            if (!httpResponse.isSuccessful) {
                return Resource.Error(
                    "Gemini API error ${httpResponse.code}: ${parseErrorMessage(rawJson)}"
                )
            }

            val content = parseResponseText(rawJson)
            Resource.Success(content)

        } catch (e: IOException) {
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Resource.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builds Gemini-compatible JSON request body.
     *
     * Gemini format:
     * {
     *   "system_instruction": { "parts": [{ "text": "..." }] },
     *   "contents": [{ "parts": [{ "text": "..." }] }],
     *   "generationConfig": { "maxOutputTokens": 1024, "temperature": 0.7 }
     * }
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
                mapOf(
                    "parts" to listOf(mapOf("text" to userPrompt))
                )
            ),
            "generationConfig" to mapOf(
                "maxOutputTokens" to maxTokens,
                "temperature" to temperature
            )
        )
        return gson.toJson(payload)
    }

    /**
     * Parses the text from Gemini's response.
     * Response path: candidates[0].content.parts[0].text
     */
    private fun parseResponseText(rawJson: String): String {
        return try {
            val jsonObj = gson.fromJson(rawJson, JsonObject::class.java)
            jsonObj
                .getAsJsonArray("candidates")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString
                ?: "No response received."
        } catch (e: Exception) {
            "Could not parse AI response."
        }
    }

    /**
     * Extracts error message from Gemini error response body.
     */
    private fun parseErrorMessage(rawJson: String): String = try {
        val jsonObj = gson.fromJson(rawJson, JsonObject::class.java)
        jsonObj.getAsJsonObject("error")?.get("message")?.asString ?: rawJson
    } catch (e: Exception) {
        rawJson
    }
}