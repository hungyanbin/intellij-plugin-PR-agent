package com.github.hungyanbin.intellijpluginpragent.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class AnthropicAPIService {

    private val httpClient = HttpClient.newHttpClient()
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
                    "max_tokens": 1000,
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
            // Simple JSON parsing to extract the content
            val contentStart = responseBody.indexOf("\"text\":\"") + 8
            val contentEnd = responseBody.indexOf("\"", contentStart)
            if (contentStart > 7 && contentEnd > contentStart) {
                responseBody.substring(contentStart, contentEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
            } else {
                "Could not parse response: $responseBody"
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}\n\nRaw response: $responseBody"
        }
    }

    class Config(
        val model: String
    )
}