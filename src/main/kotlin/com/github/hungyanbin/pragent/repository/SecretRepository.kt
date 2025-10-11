package com.github.hungyanbin.pragent.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
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

    companion object {
        private const val LLM_PROVIDER_KEY = "com.github.hungyanbin.pragent.llm.provider"
        private const val LLM_MODEL_KEY = "com.github.hungyanbin.pragent.llm.model"
    }

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

    // Store and retrieve LLM provider name (non-sensitive)
    fun storeLLMProvider(providerName: String) {
        PropertiesComponent.getInstance().setValue(LLM_PROVIDER_KEY, providerName)
    }

    fun getLLMProvider(): String? {
        return PropertiesComponent.getInstance().getValue(LLM_PROVIDER_KEY)
    }

    // Store and retrieve LLM model name (non-sensitive)
    fun storeLLMModel(modelName: String) {
        PropertiesComponent.getInstance().setValue(LLM_MODEL_KEY, modelName)
    }

    fun getLLMModel(): String? {
        return PropertiesComponent.getInstance().getValue(LLM_MODEL_KEY)
    }
}