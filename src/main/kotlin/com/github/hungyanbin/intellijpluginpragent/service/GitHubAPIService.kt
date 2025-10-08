package com.github.hungyanbin.intellijpluginpragent.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class CreatePRRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String
)

data class GitHubRepository(
    val owner: String,
    val name: String
)

class GitHubAPIService {

    suspend fun createPullRequest(
        githubPat: String,
        repository: GitHubRepository,
        prRequest: CreatePRRequest
    ): String = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/${repository.owner}/${repository.name}/pulls"
        val connection = URL(apiUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "token $githubPat")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val jsonPayload = """
                {
                    "title": "${escapeJson(prRequest.title)}",
                    "head": "${escapeJson(prRequest.head)}",
                    "base": "${escapeJson(prRequest.base)}",
                    "body": "${escapeJson(prRequest.body)}"
                }
            """.trimIndent()

            connection.outputStream.use { outputStream ->
                outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseMessage = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $responseCode: ${connection.responseMessage}"
                throw Exception("Failed to create PR: $errorMessage")
            }

            return@withContext responseMessage

        } finally {
            connection.disconnect()
        }
    }

    fun parseGitHubRepository(remoteUrl: String): GitHubRepository? {
        // Handle both HTTPS and SSH URLs
        val httpsRegex = Regex("""https://github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$""")
        val sshRegex = Regex("""git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$""")

        val httpsMatch = httpsRegex.find(remoteUrl)
        if (httpsMatch != null) {
            val (owner, repo) = httpsMatch.destructured
            return GitHubRepository(owner, repo)
        }

        val sshMatch = sshRegex.find(remoteUrl)
        if (sshMatch != null) {
            val (owner, repo) = sshMatch.destructured
            return GitHubRepository(owner, repo)
        }

        return null
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}