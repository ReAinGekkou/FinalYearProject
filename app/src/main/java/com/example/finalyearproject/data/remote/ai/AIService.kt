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
 * AIService
 *
 * OkHttp-based client for communicating with the AI backend API.
 * All network calls are dispatched on [Dispatchers.IO] and wrapped in
 * [Resource] to avoid leaking HTTP exceptions into the ViewModel.
 *
 * Location: data/remote/ai/AIService.kt
 *
 * Usage example:
 *   val response = aiService.sendFoodQuery("What should I eat today?")
 *   // response is Resource<AIResponse>
 */

class AIService constructor() {

    // ── OkHttp client ─────────────────────────────────────────────────────────

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY   // Use NONE in release builds
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)          // AI APIs can be slow
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            // Attach API key to every request
            val original = chain.request()
            val withAuth = original.newBuilder()
                .header("Authorization", "Bearer ${Constants.AI_API_KEY}")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(withAuth)
        }
        .build()

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Request / Response models ─────────────────────────────────────────────

    /**
     * Payload sent to the AI endpoint.
     */
    data class AIRequest(
        val prompt: String,
        val context: String? = null,         // optional additional context
        val maxTokens: Int = 512,
        val temperature: Double = 0.7
    )

    /**
     * Parsed AI response payload.
     * Adjust fields to match your chosen AI provider's schema
     * (e.g. OpenAI, Gemini, Groq, Mistral, etc.).
     */
    data class AIResponse(
        val id: String?,
        val response: String,                // the main text answer
        val tokensUsed: Int?,
        val rawJson: String                  // always store raw JSON for debugging
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a natural-language food query to the AI backend.
     *
     * Example: "What should I eat today given I have chicken and rice?"
     *
     * @param query    User's question
     * @param context  Optional food context (e.g. available ingredients, dietary prefs)
     * @return [Resource.Success] with [AIResponse], or [Resource.Error].
     */
    suspend fun sendFoodQuery(
        query: String,
        context: String? = null
    ): Resource<AIResponse> = withContext(Dispatchers.IO) {
        sendRequest(
            AIRequest(
                prompt = query,
                context = context
            )
        )
    }

    /**
     * Asks the AI to generate a recipe based on given ingredients.
     */
    suspend fun generateRecipe(ingredients: List<String>): Resource<AIResponse> =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                append("Generate a detailed recipe using these ingredients: ")
                append(ingredients.joinToString(", "))
                append(". Include title, description, step-by-step instructions, and estimated cooking time.")
            }
            sendRequest(AIRequest(prompt = prompt, maxTokens = 1024))
        }

    /**
     * Asks the AI to analyse the nutritional profile of a recipe.
     */
    suspend fun analyseNutrition(recipeDescription: String): Resource<AIResponse> =
        withContext(Dispatchers.IO) {
            val prompt = "Analyse the nutritional content of this recipe and provide " +
                    "estimated calories, protein, carbs, and fat: $recipeDescription"
            sendRequest(AIRequest(prompt = prompt))
        }

    // ── Core POST helper ──────────────────────────────────────────────────────

    /**
     * Builds and executes the HTTP POST request.
     * Returns the raw JSON body on success, mapped to [AIResponse].
     */
    private fun sendRequest(aiRequest: AIRequest): Resource<AIResponse> {
        return try {
            val requestBody = buildRequestBody(aiRequest)

            val httpRequest = Request.Builder()
                .url(Constants.AI_BASE_URL + Constants.AI_ENDPOINT_QUERY)
                .post(requestBody.toRequestBody(JSON))
                .build()

            val httpResponse = client.newCall(httpRequest).execute()

            // ── Response handling ──────────────────────────────────────────
            val rawJson = httpResponse.body?.string()
                ?: return Resource.Error("Empty response from AI server.")

            if (!httpResponse.isSuccessful) {
                return Resource.Error(
                    "AI API error ${httpResponse.code}: ${parseErrorMessage(rawJson)}"
                )
            }

            val aiResponse = parseResponse(rawJson)
            Resource.Success(aiResponse)

        } catch (e: IOException) {
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Resource.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    // ── JSON builders ─────────────────────────────────────────────────────────

    /**
     * Builds the JSON request body string.
     *
     * Adjust the JSON schema to match your AI provider.
     * This template follows OpenAI-compatible APIs (Groq, Together.ai, etc.).
     */
    private fun buildRequestBody(request: AIRequest): String {
        val messages = buildList {
            add(
                mapOf(
                    "role" to "system",
                    "content" to "You are a professional chef and nutritionist. " +
                            "Provide helpful, accurate, and engaging food-related advice."
                )
            )
            request.context?.let {
                add(mapOf("role" to "user", "content" to "Context: $it"))
            }
            add(mapOf("role" to "user", "content" to request.prompt))
        }

        val payload = mapOf(
            "model" to Constants.AI_MODEL,
            "messages" to messages,
            "max_tokens" to request.maxTokens,
            "temperature" to request.temperature
        )
        return gson.toJson(payload)
    }

    /**
     * Parses the raw JSON response into [AIResponse].
     * Handles OpenAI-compatible response format.
     */
    private fun parseResponse(rawJson: String): AIResponse {
        return try {
            val jsonObj = gson.fromJson(rawJson, JsonObject::class.java)

            // OpenAI-compatible: choices[0].message.content
            val content = jsonObj
                .getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: jsonObj.get("response")?.asString   // fallback for custom APIs
                ?: rawJson

            val id = jsonObj.get("id")?.asString
            val tokens = jsonObj.getAsJsonObject("usage")
                ?.get("total_tokens")?.asInt

            AIResponse(
                id = id,
                response = content,
                tokensUsed = tokens,
                rawJson = rawJson
            )
        } catch (e: Exception) {
            // If parsing fails, return the raw string as the response
            AIResponse(
                id = null,
                response = rawJson,
                tokensUsed = null,
                rawJson = rawJson
            )
        }
    }

    /**
     * Attempts to extract an error message from a non-2xx response body.
     */
    private fun parseErrorMessage(rawJson: String): String = try {
        val jsonObj = gson.fromJson(rawJson, JsonObject::class.java)
        jsonObj.getAsJsonObject("error")?.get("message")?.asString ?: rawJson
    } catch (e: Exception) {
        rawJson
    }
}