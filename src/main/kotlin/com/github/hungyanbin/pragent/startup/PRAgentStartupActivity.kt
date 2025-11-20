package com.github.hungyanbin.pragent.startup

import com.github.hungyanbin.pragent.repository.SecretRepository
import com.github.hungyanbin.pragent.repository.UserRepository
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PRAgentStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        prefetchAuthenticatedUser()
    }

    private suspend fun prefetchAuthenticatedUser() = withContext(Dispatchers.IO) {
        try {
            val secretRepository = SecretRepository()
            val githubPat = secretRepository.getGithubPat()

            if (!githubPat.isNullOrEmpty()) {
                val userRepository = UserRepository.getInstance()

                // Pre-fetch and cache the authenticated user
                userRepository.getAuthenticatedUser(githubPat)
            }
        } catch (e: Exception) {
            // Silently fail - this is just a pre-fetch optimization
        }
    }
}