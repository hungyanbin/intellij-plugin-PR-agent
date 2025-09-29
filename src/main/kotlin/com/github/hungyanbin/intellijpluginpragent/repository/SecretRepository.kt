package com.github.hungyanbin.intellijpluginpragent.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecretRepository {

    private val anthropicApiKeyAttribute = CredentialAttributes(
        "anthropic",
        "anthropic_api_key"
    )

    private val githubPatAttribute = CredentialAttributes(
        "github",
        "github_pat"
    )

    suspend fun storeAnthropicApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        PasswordSafe.instance.setPassword(anthropicApiKeyAttribute, apiKey)
    }

    suspend fun getAnthropicApiKey(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = PasswordSafe.instance.getPassword(anthropicApiKeyAttribute)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun storeGithubPat(pat: String) = withContext(Dispatchers.IO) {
        PasswordSafe.instance.setPassword(githubPatAttribute, pat)
    }

    suspend fun getGithubPat(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = PasswordSafe.instance.getPassword(githubPatAttribute)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearGithubPat() = withContext(Dispatchers.IO) {
        PasswordSafe.instance.setPassword(githubPatAttribute, null)
    }
}