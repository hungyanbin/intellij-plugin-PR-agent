package com.github.hungyanbin.pragent.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
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

@Serializable
data class PullRequest(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val html_url: String
)

@Serializable
data class RepositoryInfo(
    val default_branch: String
)

class GitHubAPIService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    suspend fun createPullRequest(
        githubPat: String,
        repository: GitHubRepository,
        prRequest: CreatePRRequest
    ): String = withContext(Dispatchers.IO) {
        try {
            val response = client.post("https://api.github.com/repos/${repository.owner}/${repository.name}/pulls") {
                headers {
                    append(HttpHeaders.Authorization, "token $githubPat")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                contentType(ContentType.Application.Json)
                setBody(prRequest)
            }

            return@withContext response.body<String>()
        } catch (e: Exception) {
            throw Exception("Failed to create PR: ${e.message}", e)
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

    suspend fun findExistingPullRequest(
        githubPat: String,
        repository: GitHubRepository,
        head: String,
        base: String
    ): PullRequest? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://api.github.com/repos/${repository.owner}/${repository.name}/pulls") {
                headers {
                    append(HttpHeaders.Authorization, "token $githubPat")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                parameter("head", "${repository.owner}:$head")
                parameter("base", base)
                parameter("state", "all")
            }

            val pullRequests = response.body<List<PullRequest>>()
            return@withContext pullRequests.firstOrNull()
        } catch (e: Exception) {
            // Return null on any error
            return@withContext null
        }
    }

    suspend fun updatePullRequest(
        githubPat: String,
        repository: GitHubRepository,
        prNumber: Int,
        title: String,
        body: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val response = client.patch("https://api.github.com/repos/${repository.owner}/${repository.name}/pulls/$prNumber") {
                headers {
                    append(HttpHeaders.Authorization, "token $githubPat")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "title" to title,
                    "body" to body
                ))
            }

            return@withContext response.body<String>()
        } catch (e: Exception) {
            throw Exception("Failed to update PR: ${e.message}", e)
        }
    }

    suspend fun getDefaultBranch(
        githubPat: String,
        repository: GitHubRepository
    ): String = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://api.github.com/repos/${repository.owner}/${repository.name}") {
                headers {
                    append(HttpHeaders.Authorization, "token $githubPat")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
            }

            val repoInfo = response.body<RepositoryInfo>()
            return@withContext repoInfo.default_branch
        } catch (e: Exception) {
            throw Exception("Failed to get default branch: ${e.message}", e)
        }
    }

    fun close() {
        client.close()
    }
}