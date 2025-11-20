package com.github.hungyanbin.pragent.repository

import com.github.hungyanbin.pragent.service.GitHubAPIService
import com.github.hungyanbin.pragent.service.GitHubUser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class UserRepository {
    private var cachedUser: GitHubUser? = null
    private var cachedPatHash: Int? = null
    private val githubAPIService = GitHubAPIService()

    suspend fun getAuthenticatedUser(githubPat: String): GitHubUser? {
        val currentPatHash = githubPat.hashCode()

        // Return cached user if PAT hasn't changed
        if (cachedUser != null && cachedPatHash == currentPatHash) {
            return cachedUser
        }

        // Fetch new user and update cache
        val user = githubAPIService.getAuthenticatedUser(githubPat)
        if (user != null) {
            cachedUser = user
            cachedPatHash = currentPatHash
        }

        return user
    }

    fun clearCache() {
        cachedUser = null
        cachedPatHash = null
    }

    companion object {
        fun getInstance(): UserRepository = service()
    }
}