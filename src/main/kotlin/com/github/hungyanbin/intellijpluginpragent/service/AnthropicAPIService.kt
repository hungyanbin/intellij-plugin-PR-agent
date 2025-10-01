package com.github.hungyanbin.intellijpluginpragent.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    val stop_reason: String? = null,
    val usage: Usage
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class Usage(
    val input_tokens: Int,
    val output_tokens: Int
)

class AnthropicAPIService {

    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    var config = Config(
        model = "claude-sonnet-4-20250514"
    )

    suspend fun callAnthropicAPI(apiKey: String, prompt: String): String {
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val requestBody = """
                {
                    "model": "${config.model}",
                    "max_tokens": 4096,
                    "messages": [
                        {
                            "role": "user",
                            "content": "$escapedPrompt"
                        }
                    ]
                }
            """.trimIndent()
        println("requestBody: $requestBody")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return withContext(Dispatchers.IO) {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                parseAnthropicResponse(response.body())
            } else {
                "Error ${response.statusCode()}: ${response.body()}"
            }
        }
    }

    private fun parseAnthropicResponse(responseBody: String): String {
        return try {
            val response = json.decodeFromString<AnthropicResponse>(responseBody)
            response.content.firstOrNull()?.text ?: "No content in response"
        } catch (e: Exception) {
            "Error parsing response: ${e.message}\n\nRaw response: $responseBody"
        }
    }

    class Config(
        val model: String
    )
}